package com.tji.device.product.speaker.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object SpeakerMqttTopics : MqttTopicLayout {
    private const val PREFIX = "Speaker/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"
}
