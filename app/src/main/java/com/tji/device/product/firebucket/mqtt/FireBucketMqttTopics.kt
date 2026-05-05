package com.tji.device.product.firebucket.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/** 消防吊桶：`FireBucket/devices/{sn}/…` */
object FireBucketMqttTopics : MqttTopicLayout {

    private const val PREFIX = "FireBucket/devices/"

    override fun lifecycleTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/lifecycle"

    override fun statusTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/status"

    override fun controlTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/control"
}
