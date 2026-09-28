package com.tji.device.product.firegun.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/** 消防喷枪沿用消防产品前缀；控制回执走 event，下方设备实时状态走 status。 */
object FireGunMqttTopics : MqttTopicLayout {
    private const val PREFIX = "FireBucket/devices/"

    override fun lifecycleTopic(deviceId: String): String =
        "$PREFIX${validatedDeviceId(deviceId)}/lifecycle"

    override fun statusTopic(deviceId: String): String =
        "$PREFIX${validatedDeviceId(deviceId)}/status"

    override fun controlTopic(deviceId: String): String =
        "$PREFIX${validatedDeviceId(deviceId)}/control"

    override fun lifecycleTopics(deviceId: String): List<String> = listOf(lifecycleTopic(deviceId))

    override fun statusTopics(deviceId: String): List<String> =
        listOf(eventTopic(deviceId), statusTopic(deviceId))

    fun eventTopic(deviceId: String): String =
        "$PREFIX${validatedDeviceId(deviceId)}/event"

    private fun validatedDeviceId(value: String): String {
        val deviceId = value.trim()
        require(deviceId.isNotEmpty()) { "Link 序列号不能为空" }
        require('/' !in deviceId && '+' !in deviceId && '#' !in deviceId) {
            "Link 序列号包含非法字符"
        }
        return deviceId
    }
}
