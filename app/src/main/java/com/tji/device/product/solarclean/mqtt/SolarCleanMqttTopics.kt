package com.tji.device.product.solarclean.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/** 光伏清洗：平台统一 topic 布局，payload 字段由 SolarCleanMqttInbound 自己解析。 */
object SolarCleanMqttTopics : MqttTopicLayout {

    private const val PREFIX = "SolarClean/devices/"

    override fun lifecycleTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/lifecycle"

    override fun statusTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/status"

    override fun controlTopic(serialNumber: String): String =
        "${PREFIX}$serialNumber/control"
}
