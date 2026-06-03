package com.tji.device.service

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttInbound
import com.tji.device.product.firebucket.mqtt.FireBucketMqttInbound
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttInbound
import com.tji.device.product.speaker.mqtt.SpeakerMqttInbound
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttInbound
import org.json.JSONObject

/**
 * 解析 MQTT 字符串后，按 **订阅时已登记** 的 [ProductType] 转发到对应产品 inbound（不在此做 infer）。
 */
class MqttEventHandler(
    private val fireBucketInbound: FireBucketMqttInbound,
    private val solarCleanInbound: SolarCleanMqttInbound,
    private val dropperSixStageInbound: DropperSixStageMqttInbound,
    private val radioDetectionInbound: RadioDetectionMqttInbound,
    private val speakerInbound: SpeakerMqttInbound
) {

    suspend fun handleMessage(
        serialNumber: String,
        productType: ProductType,
        message: String,
        isRetained: Boolean = false
    ) {
        try {
            if (productType == ProductType.RadioDetection) {
                radioDetectionInbound.handleMessage(
                    serialNumber = serialNumber,
                    message = message,
                    isRetained = isRetained
                )
                return
            }

            val trimmedMessage = message.trim()
            if (trimmedMessage.equals("online", ignoreCase = true) ||
                trimmedMessage.equals("offline", ignoreCase = true)
            ) {
                handlePlainLifecycleMessage(
                    serialNumber = serialNumber,
                    productType = productType,
                    eventType = trimmedMessage.lowercase(),
                    isRetained = isRetained
                )
                return
            }

            val json = JSONObject(message)
            val eventType = json.optString("event_type").ifBlank {
                // SolarClean 入站使用 type 表示 ack/state/event；App 下发 control 才使用数字 cmd。
                json.optString("type")
            }.ifBlank {
                json.optString("cmdName")
            }.ifBlank {
                when {
                    json.has("ota_status") || json.has("status") -> "otaStatus"
                    json.has("firmware_version") || json.has("hardware_version") || json.has("hardware") -> "deviceInfo"
                    else -> ""
                }
            }
            if (eventType.isBlank()) {
                Log.w(TAG, "MQTT 消息缺少 event_type/type: sn=$serialNumber product=$productType")
                return
            }
            Log.d(TAG, "接收到事件: $eventType, sn=$serialNumber, product=$productType, retain=$isRetained")

            when (productType) {
                ProductType.FireBucket -> fireBucketInbound.handleEvent(serialNumber, eventType, json)
                ProductType.SolarClean -> solarCleanInbound.handleEvent(
                    linkSn = serialNumber,
                    eventType = eventType,
                    json = json,
                    isRetained = isRetained
                )
                ProductType.DropperSixStage -> dropperSixStageInbound.handleEvent(
                    serialNumber = serialNumber,
                    eventType = eventType,
                    json = json,
                    isRetained = isRetained
                )
                ProductType.RadioDetection -> Unit
                ProductType.Speaker -> speakerInbound.handleEvent(
                    serialNumber = serialNumber,
                    eventType = eventType,
                    json = json,
                    isRetained = isRetained
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 MQTT 消息失败: ${e.message}", e)
        }
    }

    private suspend fun handlePlainLifecycleMessage(
        serialNumber: String,
        productType: ProductType,
        eventType: String,
        isRetained: Boolean
    ) {
        val json = JSONObject().apply {
            put("type", eventType)
            put("ts", System.currentTimeMillis())
        }
        Log.d(TAG, "接收到纯文本生命周期事件: $eventType, sn=$serialNumber, product=$productType, retain=$isRetained")
        when (productType) {
            ProductType.FireBucket -> fireBucketInbound.handleEvent(serialNumber, eventType, json)
            ProductType.SolarClean -> solarCleanInbound.handleEvent(
                linkSn = serialNumber,
                eventType = eventType,
                json = json,
                isRetained = isRetained
            )
            ProductType.DropperSixStage -> dropperSixStageInbound.handleEvent(
                serialNumber = serialNumber,
                eventType = eventType,
                json = json,
                isRetained = isRetained
            )
            ProductType.RadioDetection -> radioDetectionInbound.handleMessage(serialNumber, eventType, isRetained)
            ProductType.Speaker -> speakerInbound.handleEvent(
                serialNumber = serialNumber,
                eventType = eventType,
                json = json,
                isRetained = isRetained
            )
        }
    }

    fun cleanup() {
        fireBucketInbound.cleanup()
        solarCleanInbound.cleanup()
        dropperSixStageInbound.cleanup()
        radioDetectionInbound.cleanup()
        speakerInbound.cleanup()
    }

    private companion object {
        const val TAG = "MqttEventHandler"
    }
}
