package com.tji.network

/**
 * 集中维护跨模块引用的网络端点，避免在 [NetWorkUtils]、[MQTTConfig] 等处重复硬编码。
 */
object NetworkEndpoints {
    val MqttBrokerHost: String = BuildConfig.TJI_MQTT_BROKER_HOST
    val MqttBrokerPort: Int = BuildConfig.TJI_MQTT_BROKER_PORT
}
