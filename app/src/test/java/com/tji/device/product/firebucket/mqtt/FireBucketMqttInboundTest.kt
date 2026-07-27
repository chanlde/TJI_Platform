package com.tji.device.product.firebucket.mqtt

import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireBucketMqttInboundTest {
    @Test
    fun startupBeginsHeartbeatTimeoutInsteadOfRemainingOnlineForever() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(
            linkDeviceRepo = repository,
            heartbeatTimeoutMillis = 20L
        )

        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())
        assertTrue(repository.links.value.single().isOnline)

        delay(60L)

        assertFalse(repository.links.value.single().isOnline)
        inbound.cleanup()
    }

    @Test
    fun retainedStartupRestoresSnapshotWithoutClaimingDevicesAreOnline() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(repository)

        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "LinkDeviceStartup",
            json = startupPayload(),
            isRetained = true
        )

        val link = repository.links.value.single()
        assertFalse(link.isOnline)
        assertFalse(link.subDevices.single().isOnline)
        inbound.cleanup()
    }

    @Test
    fun retainedStartupCannotOverwriteNewerRealtimeState() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(repository)
        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())
        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "SubDeviceStatusChanged",
            json = startupPayload()
                .getJSONArray("subDevices")
                .getJSONObject(0)
                .put("currentAngle", 55)
        )

        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "LinkDeviceStartup",
            json = startupPayload(),
            isRetained = true
        )

        val link = repository.links.value.single()
        assertTrue(link.isOnline)
        assertEquals(55.0, link.subDevices.single().currentAngle, 0.0)
        inbound.cleanup()
    }

    @Test
    fun partialSubDeviceStatusPreservesUnreportedTelemetry() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(repository)
        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())

        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "SubDeviceStatusChanged",
            json = JSONObject()
                .put("serial_number", "SWITCH-001")
                .put("currentAngle", 55)
                .put("uptime", 2)
        )

        val switch = repository.links.value.single().subDevices.single()
        assertEquals(55.0, switch.currentAngle, 0.0)
        assertEquals(1.0, switch.currentCurrent, 0.0)
        assertEquals(8.0, switch.inputVoltage, 0.0)
        assertEquals(2, switch.uptime)
        assertTrue(switch.isOnline)
        inbound.cleanup()
    }

    @Test
    fun heartbeatBeforeRetainedStartupIsNotLost() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(repository)

        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "LinkDeviceHeartbeat",
            json = JSONObject()
                .put("serial_number", LINK_SERIAL)
                .put("isOnline", true)
        )
        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "LinkDeviceStartup",
            json = startupPayload(),
            isRetained = true
        )

        assertTrue(repository.links.value.single().isOnline)
        inbound.cleanup()
    }

    @Test
    fun retainedStartupAfterHandlerRestartDoesNotReuseOldOnlineSignal() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(repository)
        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())
        assertTrue(repository.links.value.single().isOnline)

        inbound.cleanup()
        inbound.handleEvent(
            linkSn = LINK_SERIAL,
            eventType = "LinkDeviceStartup",
            json = startupPayload(),
            isRetained = true
        )

        assertFalse(repository.links.value.single().isOnline)
        inbound.cleanup()
    }

    @Test
    fun cleanupCancelsOldTimerAndAllowsTrackingAfterServiceRestart() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(
            linkDeviceRepo = repository,
            heartbeatTimeoutMillis = 20L
        )

        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())
        inbound.cleanup()
        delay(60L)
        assertTrue(repository.links.value.single().isOnline)

        inbound.handleEvent(LINK_SERIAL, "LinkDeviceStartup", startupPayload())
        delay(60L)

        assertFalse(repository.links.value.single().isOnline)
        inbound.cleanup()
    }

    private fun startupPayload() = JSONObject(
        """
        {
          "event_type": "LinkDeviceStartup",
          "serial_number": "$LINK_SERIAL",
          "deviceName": "消防吊桶 Link",
          "deviceType": "Link",
          "manufacturer": "TJI",
          "deviceModel": "FB",
          "isOnline": true,
          "hwVersion": "1",
          "swVersion": "1",
          "uptime": 1,
          "deviceConfig": "",
          "timestamp": "1",
          "subDevices": [{
            "serial_number": "SWITCH-001",
            "deviceName": "吊桶",
            "deviceType": "HydroSwitch",
            "isOnline": true,
            "currentAngle": 10,
            "currentCurrent": 1,
            "inputVoltage": 8,
            "servoMinAngle": 0,
            "servoMaxAngle": 90,
            "uptime": 1
          }]
        }
        """.trimIndent()
    )

    private companion object {
        const val LINK_SERIAL = "LINK-001"
    }
}
