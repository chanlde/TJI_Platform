package com.tji.device.product.firegun.protocol

import com.tji.device.product.firegun.mqtt.FireGunMqttTopics
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FireGunProtocolTest {
    @Test
    fun topicsFollowTheRequirementDocument() {
        assertEquals(
            "FireBucket/devices/E465B062174A5124/control",
            FireGunMqttTopics.controlTopic("E465B062174A5124")
        )
        assertEquals(
            "FireBucket/devices/E465B062174A5124/event",
            FireGunMqttTopics.eventTopic("E465B062174A5124")
        )
        assertEquals(
            "FireBucket/devices/E465B062174A5124/status",
            FireGunMqttTopics.statusTopic("E465B062174A5124")
        )
        assertEquals(
            "FireBucket/devices/E465B062174A5124/lifecycle",
            FireGunMqttTopics.lifecycleTopic("E465B062174A5124")
        )
    }

    @Test
    fun unlockRequestUsesDownstreamDeviceSerialAsPayloadTarget() {
        val json = FireGunProtocol.unlockRequest("9CCC0139EF88727D", "lock-000001")

        assertEquals("LockControlRequest", json.getString("event_type"))
        assertEquals("9CCC0139EF88727D", json.getString("serial_number"))
        assertEquals("lock-000001", json.getString("request_id"))
        assertEquals("unlock", json.getString("action"))
    }

    @Test
    fun actuatorRequestsAllowOnlyDocumentActions() {
        FireGunAction.entries.forEach { action ->
            val json = FireGunProtocol.actuatorRequest("SUB-001", "actuator-1", action)
            assertEquals("ActuatorControlRequest", json.getString("event_type"))
            assertEquals("SUB-001", json.getString("serial_number"))
            assertEquals(action.wireValue, json.getString("action"))
        }
    }

    @Test
    fun parsesDocumentResponses() {
        val lock = FireGunProtocol.parseResponse(
            """{
                "event_type":"LockControlResponse",
                "serial_number":"ESP-001",
                "request_id":"lock-1",
                "success":true,
                "action":"unlock",
                "state":"unlocking",
                "pulse_ms":500,
                "error":""
            }"""
        ) as FireGunResponse.Lock
        val actuator = FireGunProtocol.parseResponse(
            """{
                "event_type":"ActuatorControlResponse",
                "serial_number":"ESP-001",
                "request_id":"actuator-1",
                "success":false,
                "action":"open",
                "state":"stopped",
                "max_run_ms":90000,
                "error":"command_rejected"
            }"""
        ) as FireGunResponse.Actuator

        assertTrue(lock.success)
        assertEquals(500L, lock.pulseMs)
        assertNull(lock.error)
        assertFalse(actuator.success)
        assertEquals(90_000L, actuator.maxRunMs)
        assertEquals("command_rejected", actuator.error)
    }

    @Test
    fun parsesSubDeviceStatusWithoutConfusingItWithTheLinkSerial() {
        val status = FireGunProtocol.parseStatus(
            linkSerial = "E465B062174A5124",
            json = JSONObject(
                """{
                    "event_type":"SubDeviceStatusChanged",
                    "serial_number":"9CCC0139EF88727D",
                    "deviceName":"HydroActuator_9CCC01",
                    "deviceType":"HydroActuator",
                    "isOnline":true,
                    "currentAngle":0.0,
                    "currentCurrent":0.0,
                    "inputVoltage":11.140000343322754,
                    "servoMinAngle":0.0,
                    "servoMaxAngle":90.0,
                    "uptime":501,
                    "timestamp":946656371,
                    "rssi":0,
                    "lockState":"locked",
                    "actuatorState":"closed"
                }""".trimIndent()
            )
        )

        assertEquals("E465B062174A5124", status.linkSerial)
        assertEquals("9CCC0139EF88727D", status.serialNumber)
        assertTrue(status.isOnline)
        assertEquals(11.140000343322754, status.inputVoltage!!, 0.000001)
        assertEquals("locked", status.lockState)
        assertEquals("closed", status.actuatorState)
    }

    @Test
    fun parsesLinkStartupSnapshotAndHeartbeat() {
        val startup = FireGunProtocol.parseLinkStartup(
            topicLinkSerial = "E465B062174A5124",
            json = JSONObject(
                """{
                    "event_type":"LinkDeviceStartup",
                    "serial_number":"E465B062174A5124",
                    "deviceName":"HydroGunLink_V1",
                    "isOnline":true,
                    "hwVersion":"HydroGunLink_V1",
                    "swVersion":"1,0,11,20",
                    "uptime":196,
                    "timestamp":946656196,
                    "subDevices":[{
                        "serialNumber":"9CCC0139EF88727D",
                        "deviceName":"HydroActuator_9CCC01",
                        "deviceType":"HydroActuator",
                        "isOnline":true,
                        "currentAngle":0,
                        "inputVoltage":11.14,
                        "lockState":"locked",
                        "actuatorState":"closed"
                    }]
                }""".trimIndent()
            )
        )
        val heartbeat = FireGunProtocol.parseLinkHeartbeat(
            topicLinkSerial = "E465B062174A5124",
            json = JSONObject(
                """{
                    "event_type":"LinkDeviceHeartbeat",
                    "serial_number":"E465B062174A5124",
                    "isOnline":true,
                    "uptime":1435,
                    "timestamp":946657435
                }""".trimIndent()
            )
        )

        assertEquals("HydroGunLink_V1", startup.name)
        assertEquals("9CCC0139EF88727D", startup.deviceStatus?.serialNumber)
        assertEquals(1_435L, heartbeat.uptime)
        assertTrue(heartbeat.isOnline)
    }

    @Test
    fun rejectsUnknownActionsAndResponseTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            FireGunAction.fromWire("move")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FireGunProtocol.parseResponse("""{"event_type":"Unknown"}""")
        }
    }
}
