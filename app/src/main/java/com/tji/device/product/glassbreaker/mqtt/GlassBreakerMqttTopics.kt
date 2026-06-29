package com.tji.device.product.glassbreaker.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object GlassBreakerMqttTopics : MqttTopicLayout {
    private const val PREFIX = "GlassBreaker/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"
}
