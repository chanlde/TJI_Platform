package com.tji.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttConnectionConfigTest {

    @Test
    fun defaultBrokerReadsGeneratedBuildConfig() {
        val config = MqttConnectionConfig.default()

        assertEquals(BuildConfig.TJI_MQTT_BROKER_HOST, config.serverHost)
        assertEquals(BuildConfig.TJI_MQTT_BROKER_PORT, config.serverPort)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertEquals(BuildConfig.TJI_MQTT_TLS_ENABLED, config.enableTLS)
    }

    @Test
    fun radioLegacyCredentialsAreNotHardcodedInSourceDefaults() {
        val config = MqttConnectionConfig.radioDetectionLegacy(clientId = "radio-client")

        assertEquals("radio-client", config.clientId)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_HOST, config.serverHost)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_PORT, config.serverPort)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_USERNAME, config.username)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_PASSWORD, config.password)
        assertEquals(BuildConfig.TJI_RADIO_LEGACY_MQTT_TLS_ENABLED, config.enableTLS)
    }

    @Test
    fun tlsFlagControlsDefaultTrustAndHostnameVerifiedTransport() {
        val plain = MqttConnectionConfig.default().copy(
            serverHost = "broker.example.com",
            serverPort = 1883,
            enableTLS = false
        ).toTransportConfig()
        val tls = MqttConnectionConfig.default().copy(
            serverHost = "broker.example.com",
            serverPort = 8883,
            enableTLS = true
        ).toTransportConfig()

        assertNull(plain.sslConfig.orElse(null))
        assertNotNull(tls.sslConfig.orElse(null))
        assertEquals("broker.example.com", tls.serverAddress.hostString)
        assertEquals(8883, tls.serverAddress.port)
    }

    @Test
    fun inboundPayloadLimitAcceptsNormalFramesAndRejectsOversizedFrames() {
        assertTrue(isMqttPayloadWithinLimit(0))
        assertTrue(isMqttPayloadWithinLimit(1_048_576))
        assertFalse(isMqttPayloadWithinLimit(1_048_577))
    }
}
