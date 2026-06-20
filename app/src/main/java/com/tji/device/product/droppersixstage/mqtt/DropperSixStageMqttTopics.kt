package com.tji.device.product.droppersixstage.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object DropperSixStageMqttTopics : MqttTopicLayout {
    private const val PREFIX = "SixStageDropper/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"
}
