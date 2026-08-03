package com.tji.device.product.droppersixstage.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

object DropperSixStageMqttTopics : MqttTopicLayout {
    private const val PREFIX = "FC100_FireDrop/devices/"
    private const val COMPATIBILITY_PREFIX = "SixStageDropper/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"

    override fun lifecycleTopics(deviceId: String): List<String> = listOf(
        lifecycleTopic(deviceId),
        "${COMPATIBILITY_PREFIX}$deviceId/lifecycle"
    )

    override fun statusTopics(deviceId: String): List<String> = listOf(
        statusTopic(deviceId),
        "${COMPATIBILITY_PREFIX}$deviceId/status"
    )
}
