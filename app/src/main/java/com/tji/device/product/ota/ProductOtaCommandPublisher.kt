package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import com.tji.device.service.mqtt.ProductMqttRouter
import com.tji.device.service.mqtt.mqttTopicsFor
import org.json.JSONObject

interface ProductOtaCommandPublisher {
    fun requestDeviceInfo(
        serialNumber: String,
        productType: ProductType,
        msgId: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )

    fun startOta(
        serialNumber: String,
        productType: ProductType,
        msgId: String,
        packageInfo: ProductOtaPackage,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )
}

class ProductOtaMqttCommandPublisher : ProductOtaCommandPublisher {
    override fun requestDeviceInfo(
        serialNumber: String,
        productType: ProductType,
        msgId: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        publish(
            serialNumber = serialNumber,
            productType = productType,
            payload = basePayload(msgId = msgId, cmd = CMD_GET_DEVICE_INFO, cmdName = "GET_DEVICE_INFO"),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override fun startOta(
        serialNumber: String,
        productType: ProductType,
        msgId: String,
        packageInfo: ProductOtaPackage,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val payload = basePayload(msgId = msgId, cmd = CMD_START_OTA, cmdName = "START_OTA").apply {
            put("deviceId", serialNumber)
            put("params", JSONObject().apply {
                put("targetVersion", packageInfo.targetVersion)
                packageInfo.targetInnerVersion?.let { put("targetInnerVersion", it) }
                packageInfo.hardwareVersion?.let { put("hardwareVersion", it) }
                put("fileSize", packageInfo.fileSize)
                put("sha256", packageInfo.sha256)
                put("downloadUrl", packageInfo.downloadUrl)
                packageInfo.signature?.let { put("signature", it) }
            })
            // 过渡期兼容旧 MCU flat snake_case 字段；正式字段在 params 内。
            put("target_version", packageInfo.targetVersion)
            put("download_url", packageInfo.downloadUrl)
            put("file_size", packageInfo.fileSize)
            put("sha256", packageInfo.sha256)
            packageInfo.targetInnerVersion?.let { put("target_inner_version", it) }
            packageInfo.hardwareVersion?.let { put("hardware_version", it) }
            packageInfo.signature?.let { put("signature", it) }
        }
        publish(
            serialNumber = serialNumber,
            productType = productType,
            payload = payload,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun publish(
        serialNumber: String,
        productType: ProductType,
        payload: JSONObject,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        ProductMqttRouter.managerFor(productType).publish(
            topic = mqttTopicsFor(productType).controlTopic(serialNumber),
            message = payload.toString(),
            qos = 0,
            queueWhenDisconnected = false,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun basePayload(msgId: String, cmd: Int, cmdName: String): JSONObject {
        return JSONObject().apply {
            put("v", 1)
            put("msgId", msgId)
            put("cmdId", msgId)
            put("ts", System.currentTimeMillis())
            put("cmd", cmd)
            put("cmdName", cmdName)
        }
    }

    private companion object {
        const val CMD_GET_DEVICE_INFO = 1
        const val CMD_START_OTA = 20
    }
}
