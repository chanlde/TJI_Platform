package com.tji.device.service

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.mqtt.FireBucketMqttInbound
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttInbound
import org.json.JSONObject

/**
 * 解析 MQTT 字符串后，按 **订阅时已登记** 的 [ProductType] 转发到对应产品 inbound（不在此做 infer）。
 */
class MqttEventHandler(
    private val fireBucketInbound: FireBucketMqttInbound,
    private val solarCleanInbound: SolarCleanMqttInbound,
) {

    suspend fun handleMessage(serialNumber: String, productType: ProductType, message: String) {
        try {
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
            Log.d(TAG, "接收到事件: $eventType, sn=$serialNumber, product=$productType")

            when (productType) {
                ProductType.FireBucket -> fireBucketInbound.handleEvent(serialNumber, eventType, json)
                ProductType.SolarClean -> solarCleanInbound.handleEvent(serialNumber, eventType, json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 MQTT 消息失败: ${e.message}", e)
        }
    }

    fun cleanup() {
        fireBucketInbound.cleanup()
        solarCleanInbound.cleanup()
    }

    private companion object {
        const val TAG = "MqttEventHandler"
    }
}
