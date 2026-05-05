package com.tji.network

/**
 * 集中维护跨模块引用的网络端点，避免在 [NetWorkUtils]、[MQTTConfig] 等处重复硬编码。
 */
object NetworkEndpoints {
    const val MqttBrokerHost: String = "129.211.180.25"
    const val MqttBrokerPort: Int = 1883
}
