package com.tji.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttStatusMessageClassifierTest {

    @Test
    fun stateAndStatusAreTelemetry() {
        assertFalse(MqttStatusMessageClassifier.isPriority("""{"type":"state","battery":80}"""))
        assertFalse(MqttStatusMessageClassifier.isPriority("""{"type":" STATUS ","battery":80}"""))
    }

    @Test
    fun ackLifecycleAndOtaMessagesArePriority() {
        assertTrue(MqttStatusMessageClassifier.isPriority("""{"type":"ack","msgId":"cmd-1"}"""))
        assertTrue(MqttStatusMessageClassifier.isPriority("""{"type":"record_list","total":0}"""))
        assertTrue(MqttStatusMessageClassifier.isPriority("""{"event_type":"otaStatus","progress":50}"""))
    }

    @Test
    fun eventTypeTakesPrecedenceOverTypeRegardlessOfFieldOrder() {
        assertFalse(
            MqttStatusMessageClassifier.isPriority(
                """{"type":"ack","event_type":"state","payload":{"type":"ack"}}"""
            )
        )
        assertTrue(
            MqttStatusMessageClassifier.isPriority(
                """{"event_type":"","type":"ack"}"""
            )
        )
    }

    @Test
    fun escapedTopLevelValueIsDecodedButNestedTypeIsIgnored() {
        assertTrue(MqttStatusMessageClassifier.isPriority("""{"type":"a\u0063k"}"""))
        assertFalse(MqttStatusMessageClassifier.isPriority("""{"data":{"type":"ack"}}"""))
    }

    @Test
    fun malformedOrNonObjectPayloadIsNotPriority() {
        assertFalse(MqttStatusMessageClassifier.isPriority("not-json"))
        assertFalse(MqttStatusMessageClassifier.isPriority("""{"type":"ack"} trailing"""))
        assertFalse(MqttStatusMessageClassifier.isPriority("""["ack"]"""))
        assertFalse(MqttStatusMessageClassifier.isPriority("""{"type":{"name":"ack"}}"""))
    }
}
