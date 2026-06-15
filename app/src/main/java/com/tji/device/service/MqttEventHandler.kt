package com.tji.device.service

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.di.ProductModuleRegistry
import com.tji.device.product.ota.ProductOtaMqttParser
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import org.json.JSONObject

/**
 * 解析 MQTT 字符串后，按 **订阅时已登记** 的 [ProductType] 转发到对应产品 inbound（不在此做 infer）。
 */
class MqttEventHandler(
    private val productModules: ProductModuleRegistry,
    private val productOtaRuntimeRepository: ProductOtaRuntimeRepository? = null
) {

    suspend fun handleMessage(
        serialNumber: String,
        productType: ProductType,
        message: String,
        isRetained: Boolean = false
    ) {
        try {
            val productHandler = productModules.mqttHandlerFor(productType)
            if (productHandler == null) {
                Log.w(TAG, "未注册 MQTT 产品处理器: sn=$serialNumber product=$productType")
                return
            }
            if (productHandler.handleRawMessage(serialNumber, message, isRetained)) {
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
            val eventType = ProductOtaMqttParser.resolveEventType(json)
            if (eventType.isBlank()) {
                Log.w(TAG, "MQTT 消息缺少 event_type/type: sn=$serialNumber product=$productType")
                return
            }
            Log.d(TAG, "接收到事件: $eventType, sn=$serialNumber, product=$productType, retain=$isRetained")

            cacheCommonOtaRuntime(productType, serialNumber, eventType, json)
            productHandler.handleJsonEvent(serialNumber, eventType, json, isRetained)
        } catch (e: Exception) {
            Log.e(TAG, "解析 MQTT 消息失败: ${e.message}", e)
        }
    }

    private fun cacheCommonOtaRuntime(
        productType: ProductType,
        serialNumber: String,
        eventType: String,
        json: JSONObject
    ) {
        val repository = productOtaRuntimeRepository ?: return
        when (eventType) {
            "deviceInfo" -> ProductOtaMqttParser.parseDeviceInfo(json)?.let {
                repository.updateDeviceInfo(productType, serialNumber, it)
            }
            "otaStatus" -> ProductOtaMqttParser.parseOtaStatus(json)?.let {
                repository.updateOtaStatus(productType, serialNumber, it)
            }
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
        val productHandler = productModules.mqttHandlerFor(productType)
        if (productHandler == null) {
            Log.w(TAG, "未注册生命周期产品处理器: sn=$serialNumber product=$productType")
            return
        }
        productHandler.handleJsonEvent(serialNumber, eventType, json, isRetained)
    }

    fun cleanup() {
        productModules.cleanup()
    }

    private companion object {
        const val TAG = "MqttEventHandler"
    }
}
