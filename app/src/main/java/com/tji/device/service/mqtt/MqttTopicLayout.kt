package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductType
import com.tji.device.data.model.ProductCatalog
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttTopics
import com.tji.device.product.firebucket.mqtt.FireBucketMqttTopics
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import com.tji.device.product.speaker.mqtt.SpeakerMqttTopics
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttTopics

/**
 * 每个产品一套 MQTT 主题前缀（lifecycle / status / control）。
 *
 * 平台正式协议统一称设备身份为 `deviceId`。部分旧接口、UI 状态和产品内部模型仍保留
 * `serialNumber` 命名，传入本接口前应已经映射成正式协议中的同一个设备身份值。
 */
interface MqttTopicLayout {
    fun lifecycleTopic(deviceId: String): String
    fun statusTopic(deviceId: String): String
    fun controlTopic(deviceId: String): String
}

fun mqttTopicsFor(productType: ProductType): MqttTopicLayout = when (productType) {
    ProductType.FireBucket -> FireBucketMqttTopics
    ProductType.SolarClean -> SolarCleanMqttTopics
    ProductType.DropperSixStage -> DropperSixStageMqttTopics
    ProductType.RadioDetection -> RadioDetectionMqttTopics
    ProductType.Speaker -> SpeakerMqttTopics
    ProductType.BreakWindowProjectile -> PlaceholderProductMqttTopics(ProductCatalog.productCodeOf(productType))
    ProductType.Searchlight -> PlaceholderProductMqttTopics(ProductCatalog.productCodeOf(productType))
}

private class PlaceholderProductMqttTopics(
    private val productCode: String
) : MqttTopicLayout {
    override fun lifecycleTopic(deviceId: String): String = "$productCode/devices/$deviceId/lifecycle"
    override fun statusTopic(deviceId: String): String = "$productCode/devices/$deviceId/status"
    override fun controlTopic(deviceId: String): String = "$productCode/devices/$deviceId/control"
}
