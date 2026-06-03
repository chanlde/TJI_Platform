package com.tji.device.product.droppersixstage.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object DropperSixStageMqttTopics : MqttTopicLayout {
    private const val PREFIX = "DropperSixStage/devices/"

    override fun lifecycleTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/lifecycle"

    override fun statusTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/status"

    override fun controlTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/control"
}
