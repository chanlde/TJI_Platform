package com.tji.network

/**
 * **仅描述与 MQTT Broker 的连接**（主机、端口、账号、TLS 等），不包含任何产品线主题前缀。
 *
 * 各产品线设备主题（lifecycle/status/control）由 App 模块按产品线分流，例如：
 * `FireBucket/devices/{sn}/…`、`SolarClean/devices/{sn}/…`，见 `app` 模块内 `mqttTopicsFor` /
 * `FireBucketMqttTopics`、`SolarCleanMqttTopics`。
 * 每条产品线仍使用 `lifecycle` / `status` / `control` 三段 topic；产品自己的 payload 字段在产品包内解析。
 */
data class MQTTConfig(
    val serverHost: String,
    val serverPort: Int,
    val clientId: String,
    val username: String,
    val password: String,
    val enableTLS: Boolean = true,
    val keepAliveInterval: Int = 30,
    val cleanSession: Boolean = true,
    val qos: Int = 1,
) {
    companion object {
        /**
         * 默认连接独立 MQTT Broker（[NetworkEndpoints]）。
         * 勿把 REST 的 `api.tjinnovations.cloud` 当作 Broker：1883 在 API 域名上通常不可用。
         */
        fun default(): MQTTConfig = MQTTConfig(
            serverHost = NetworkEndpoints.MqttBrokerHost,
            serverPort = NetworkEndpoints.MqttBrokerPort,
            clientId = "",
            username = "",
            password = "",
            enableTLS = false,
            keepAliveInterval = 30,
            cleanSession = true,
            qos = 1,
        )
    }
}
