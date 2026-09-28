package com.tji.device.product.firegun.mqtt

import com.tji.device.product.firegun.repository.FireGunRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireGunMqttInboundTest {
    @Test
    fun storesFreshSubDeviceStatusUnderItsLinkSerial() = runTest {
        val repository = FireGunRepo()
        val inbound = FireGunMqttInbound(repository)

        inbound.handleEvent(
            topicLinkSerial = "E465B062174A5124",
            eventType = "SubDeviceStatusChanged",
            json = JSONObject(
                """{
                    "event_type":"SubDeviceStatusChanged",
                    "serial_number":"9CCC0139EF88727D",
                    "deviceName":"HydroActuator_9CCC01",
                    "deviceType":"HydroActuator",
                    "isOnline":true,
                    "currentAngle":0.0,
                    "currentCurrent":0.0,
                    "inputVoltage":11.14,
                    "servoMinAngle":0.0,
                    "servoMaxAngle":90.0,
                    "uptime":501,
                    "timestamp":946656371,
                    "rssi":0,
                    "lockState":"locked",
                    "actuatorState":"closed"
                }""".trimIndent()
            ),
            isRetained = false
        )

        val status = repository.links.value
            .getValue("E465B062174A5124")
            .deviceStatus!!
        assertEquals("9CCC0139EF88727D", status.serialNumber)
        assertTrue(status.isOnline)
        inbound.cleanup()
    }

    @Test
    fun retainedStartupProvidesSnapshotAndFreshHeartbeatControlsLinkOnlineState() = runTest {
        val repository = FireGunRepo()
        val inbound = FireGunMqttInbound(repository)
        val startup = JSONObject(
            """{
                "event_type":"LinkDeviceStartup",
                "serial_number":"E465B062174A5124",
                "deviceName":"HydroGunLink_V1",
                "isOnline":true,
                "uptime":196,
                "timestamp":946656196,
                "subDevices":[{
                    "serialNumber":"9CCC0139EF88727D",
                    "deviceName":"HydroActuator_9CCC01",
                    "deviceType":"HydroActuator",
                    "isOnline":true,
                    "lockState":"locked",
                    "actuatorState":"closed"
                }]
            }""".trimIndent()
        )

        inbound.handleEvent(
            "E465B062174A5124", "LinkDeviceStartup", startup, isRetained = true
        )
        assertTrue(repository.links.value.getValue("E465B062174A5124").deviceStatus != null)
        assertTrue(!repository.links.value.getValue("E465B062174A5124").isOnline)

        inbound.handleEvent(
            "E465B062174A5124",
            "LinkDeviceHeartbeat",
            JSONObject(
                """{
                    "event_type":"LinkDeviceHeartbeat",
                    "serial_number":"E465B062174A5124",
                    "isOnline":true,
                    "uptime":1435,
                    "timestamp":946657435
                }""".trimIndent()
            ),
            isRetained = false
        )

        assertTrue(repository.links.value.getValue("E465B062174A5124").isOnline)
        inbound.cleanup()
    }

    @Test
    fun missingHeartbeatsMarkTheLinkOffline() = runBlocking {
        val repository = FireGunRepo()
        val inbound = FireGunMqttInbound(repository, heartbeatTimeoutMillis = 20L)
        inbound.handleEvent(
            "E465B062174A5124",
            "LinkDeviceHeartbeat",
            JSONObject(
                """{
                    "event_type":"LinkDeviceHeartbeat",
                    "serial_number":"E465B062174A5124",
                    "isOnline":true
                }""".trimIndent()
            ),
            isRetained = false
        )

        delay(60L)

        assertTrue(!repository.links.value.getValue("E465B062174A5124").isOnline)
        inbound.cleanup()
    }
}
