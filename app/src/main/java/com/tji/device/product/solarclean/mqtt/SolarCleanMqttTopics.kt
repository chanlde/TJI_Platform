package com.tji.device.product.solarclean.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/** 光伏清洗：平台统一 topic 布局，payload 字段由 SolarCleanMqttInbound 自己解析。 */
object SolarCleanMqttTopics : MqttTopicLayout {

    private const val PREFIX = "SolarClean/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "${PREFIX}$deviceId/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "${PREFIX}$deviceId/status"

    override fun controlTopic(deviceId: String): String =
        "${PREFIX}$deviceId/control"
}
