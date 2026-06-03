package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttTopics
import com.tji.device.product.firebucket.mqtt.FireBucketMqttTopics
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import com.tji.device.product.speaker.mqtt.SpeakerMqttTopics
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttTopics

/**
 * 每个产品一套 MQTT 主题前缀（lifecycle / status / control）。
 */
interface MqttTopicLayout {
    fun lifecycleTopic(serialNumber: String): String
    fun statusTopic(serialNumber: String): String
    fun controlTopic(serialNumber: String): String
}

fun mqttTopicsFor(productType: ProductType): MqttTopicLayout = when (productType) {
    ProductType.FireBucket -> FireBucketMqttTopics
    ProductType.SolarClean -> SolarCleanMqttTopics
    ProductType.DropperSixStage -> DropperSixStageMqttTopics
    ProductType.RadioDetection -> RadioDetectionMqttTopics
    ProductType.Speaker -> SpeakerMqttTopics
}
