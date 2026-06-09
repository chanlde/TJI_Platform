package com.tji.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MqttConfigTest {

    @Test
    fun defaultBrokerReadsGeneratedBuildConfig() {
        val config = MQTTConfig.default()

        assertEquals(BuildConfig.TJI_MQTT_BROKER_HOST, config.serverHost)
        assertEquals(BuildConfig.TJI_MQTT_BROKER_PORT, config.serverPort)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertFalse(config.enableTLS)
    }

    @Test
    fun radioLegacyCredentialsAreNotHardcodedInSourceDefaults() {
        val config = MQTTConfig.radioDetectionLegacy(clientId = "radio-client")

        assertEquals("radio-client", config.clientId)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_HOST, config.serverHost)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_PORT, config.serverPort)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_USERNAME, config.username)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_PASSWORD, config.password)
    }
}
