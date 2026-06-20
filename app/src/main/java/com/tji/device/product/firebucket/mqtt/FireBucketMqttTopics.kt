package com.tji.device.product.firebucket.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/** 消防吊桶：`FireBucket/devices/{deviceId}/...` */
object FireBucketMqttTopics : MqttTopicLayout {

    private const val PREFIX = "FireBucket/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"
}
