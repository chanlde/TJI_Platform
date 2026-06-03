package com.tji.device.product.radiodetection.mqtt

import com.tji.device.service.mqtt.MqttTopicLayout

/**
 * 无线电检测 RID 上报主题。
 *
 * 设备文档中的实时 RID 数据进入 `spectrum-detection-client/{id}`。平台订阅器仍会同时订阅
 * lifecycle/status 两类主题，所以 lifecycle 暂保留平台式主题，真实 RID 走 statusTopic。
 */
object RadioDetectionMqttTopics : MqttTopicLayout {
    private const val LIFECYCLE_PREFIX = "RadioDetection/devices/"
    private const val RID_PREFIX = "spectrum-detection-client/"

    override fun lifecycleTopic(serialNumber: String): String =
        "${LIFECYCLE_PREFIX}$serialNumber/lifecycle"

    override fun statusTopic(serialNumber: String): String =
        "$RID_PREFIX$serialNumber"

    override fun controlTopic(serialNumber: String): String =
        "${LIFECYCLE_PREFIX}$serialNumber/control"

    fun rgbAckTopic(serialNumber: String): String =
        "${LIFECYCLE_PREFIX}$serialNumber/status"
}
