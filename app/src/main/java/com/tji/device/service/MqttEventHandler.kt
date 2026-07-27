package com.tji.device.service

import android.util.Log
import com.tji.device.BuildConfig
import com.tji.device.data.model.ProductType
import com.tji.device.di.ProductModuleRegistry
import com.tji.device.product.ota.ProductOtaMqttParser
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

/**
 * 解析 MQTT 字符串后，按 **订阅时已登记** 的 [ProductType] 转发到对应产品 inbound（不在此做 infer）。
 */
interface MqttMessageHandler {
    suspend fun handleMessage(
        serialNumber: String,
        productType: ProductType,
        message: String,
        isRetained: Boolean = false
    )

    fun cleanup()
}

class MqttEventHandler(
    private val productModules: ProductModuleRegistry,
    private val productOtaRuntimeRepository: ProductOtaRuntimeRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : MqttMessageHandler {

    override suspend fun handleMessage(
        serialNumber: String,
        productType: ProductType,
        message: String,
        isRetained: Boolean
    ) {
        try {
            val productHandler = productModules.mqttHandlerFor(productType)
            if (productHandler == null) {
                Log.w(TAG, "未注册 MQTT 产品处理器: sn=$serialNumber product=$productType")
                return
            }
            val trimmedMessage = message.trim()
            if (trimmedMessage.equals("online", ignoreCase = true) ||
                trimmedMessage.equals("offline", ignoreCase = true)
            ) {
                productOtaRuntimeRepository?.updateLifecycle(
                    productType = productType,
                    serialNumber = serialNumber,
                    eventType = trimmedMessage.lowercase(),
                    timestamp = nowMillis(),
                    isRetained = isRetained
                )
                if (productHandler.handleRawMessage(serialNumber, message, isRetained, parsedJson = null)) {
                    return
                }
                handlePlainLifecycleMessage(
                    serialNumber = serialNumber,
                    productType = productType,
                    eventType = trimmedMessage.lowercase(),
                    isRetained = isRetained,
                    cacheCommonRuntime = false
                )
                return
            }

            // 某些产品（例如无线电检测）自行解析全部原始消息。通用 OTA 信息必须在
            // raw handler 提前返回之前提取，否则该产品的升级状态永远不会进入统一仓库。
            val json = if (trimmedMessage.startsWith("{")) {
                runCatching { JSONObject(trimmedMessage) }.getOrNull()
            } else {
                null
            }
            if (json == null) {
                if (productHandler.handleRawMessage(
                        serialNumber,
                        message,
                        isRetained,
                        parsedJson = null
                    )
                ) {
                    return
                }
                Log.w(TAG, "MQTT 消息不是有效 JSON: sn=$serialNumber product=$productType")
                return
            }
            val eventType = ProductOtaMqttParser.resolveEventType(json)
            if (eventType.isNotBlank()) {
                cacheCommonOtaRuntime(productType, serialNumber, eventType, json, isRetained)
            }
            if (productHandler.handleRawMessage(serialNumber, message, isRetained, parsedJson = json)) {
                return
            }

            if (eventType.isBlank()) {
                Log.w(TAG, "MQTT 消息缺少 event_type/type: sn=$serialNumber product=$productType")
                return
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "接收到事件: $eventType, sn=$serialNumber, " +
                        "product=$productType, retain=$isRetained"
                )
            }

            productHandler.handleJsonEvent(serialNumber, eventType, json, isRetained)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "解析 MQTT 消息失败: ${e.message}", e)
        }
    }

    private fun cacheCommonOtaRuntime(
        productType: ProductType,
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        val repository = productOtaRuntimeRepository ?: return
        when (eventType) {
            "deviceInfo" -> ProductOtaMqttParser.parseDeviceInfo(json)?.let {
                repository.updateDeviceInfo(productType, serialNumber, it)
            }
            "otaStatus" -> ProductOtaMqttParser.parseOtaStatus(json)?.let {
                repository.updateOtaStatus(productType, serialNumber, it)
            }
            "online",
            "offline" -> repository.updateLifecycle(
                productType = productType,
                serialNumber = serialNumber,
                eventType = eventType,
                timestamp = lifecycleTimestamp(
                    deviceTimestamp = ProductOtaMqttParser.parseTimestamp(json),
                    receivedAtMillis = nowMillis()
                ),
                isRetained = isRetained
            )
        }
    }

    private suspend fun handlePlainLifecycleMessage(
        serialNumber: String,
        productType: ProductType,
        eventType: String,
        isRetained: Boolean,
        cacheCommonRuntime: Boolean = true
    ) {
        val receivedAtMillis = nowMillis()
        val json = JSONObject().apply {
            put("type", eventType)
            put("ts", receivedAtMillis)
        }
        Log.d(TAG, "接收到纯文本生命周期事件: $eventType, sn=$serialNumber, product=$productType, retain=$isRetained")
        if (cacheCommonRuntime) {
            productOtaRuntimeRepository?.updateLifecycle(
                productType = productType,
                serialNumber = serialNumber,
                eventType = eventType,
                timestamp = json.optLong("ts"),
                isRetained = isRetained
            )
        }
        val productHandler = productModules.mqttHandlerFor(productType)
        if (productHandler == null) {
            Log.w(TAG, "未注册生命周期产品处理器: sn=$serialNumber product=$productType")
            return
        }
        productHandler.handleJsonEvent(serialNumber, eventType, json, isRetained)
    }

    override fun cleanup() {
        productModules.cleanup()
    }

    private companion object {
        const val TAG = "MqttEventHandler"
    }
}

/**
 * 只有 Unix 毫秒能和 App 的接收墙钟比较。MCU uptime/Unix 秒属于不同时间域，
 * 用本地接收时间代替，避免一条合法生命周期消息因时钟域不同被永久判旧。
 */
internal fun lifecycleTimestamp(deviceTimestamp: Long?, receivedAtMillis: Long): Long =
    deviceTimestamp?.takeIf { it >= MIN_LIFECYCLE_UNIX_MILLIS } ?: receivedAtMillis

private const val MIN_LIFECYCLE_UNIX_MILLIS = 1_000_000_000_000L
