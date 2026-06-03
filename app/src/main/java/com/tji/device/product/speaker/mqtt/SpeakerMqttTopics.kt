package com.tji.device.product.speaker.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object SpeakerMqttTopics : MqttTopicLayout {
    private const val PREFIX = "Speaker/devices/"

    override fun lifecycleTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/lifecycle"

    override fun statusTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/status"

    override fun controlTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/control"
}
