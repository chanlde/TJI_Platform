package com.tji.device.product.glassbreaker.mqtt

import com.tji.device.product.glassbreaker.model.GlassBreakerFireState
import com.tji.device.product.glassbreaker.model.GlassBreakerLockState
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlassBreakerMqttInboundTest {
    @Test
    fun deviceInvalidationClearsPreviousSelectionAndBattery() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)
        inbound.handleEvent(
            SERIAL, "state",
            JSONObject("""{"ts":1,"lockState":"unlocked","selectedChannel":2,"batteryPercent":80}""")
        )
        inbound.handleEvent(
            SERIAL, "state",
            JSONObject(
                """{
                    "ts":2,
                    "lockState":"unlocked",
                    "selectionValid":false,
                    "selectedChannel":null,
                    "batteryPercentValid":false,
                    "batteryPercent":null
                }"""
            )
        )
        assertNull(repo.devices.value.single().selectedChannel)
        assertNull(repo.devices.value.single().batteryPercent)
    }


    @Test
    fun parsesGlassBreakerStatePayload() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """
                {
                  "v": 1,
                  "type": "state",
                  "deviceId": "T0000001",
                  "online": true,
                  "lockState": "unlocked",
                  "selectedChannel": 2,
                  "laserEnabled": true,
                  "fireState": "idle",
                  "armRemainingMs": 28000,
                  "firmwareVersion": "0.0.9.0",
                  "innerVersion": 9,
                  "ts": 12345
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals("T0000001", state.serialNumber)
        assertEquals(true, state.isOnline)
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(2, state.selectedChannel)
        assertEquals(true, state.laserEnabled)
        assertEquals(GlassBreakerFireState.Idle, state.fireState)
        assertEquals(28000L, state.armRemainingMs)
        assertEquals("0.0.9.0", state.firmwareVersion)
        assertEquals(9, state.firmwareInnerVersion)
        assertEquals(12345L, state.timestamp)
    }

    @Test
    fun liveStatusMarksDeviceOnline() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"online":true,"lockState":"unlocked","selectedChannel":1}""")
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(1, state.selectedChannel)
    }

    @Test
    fun staleStateCannotOverwriteNewerDeviceSnapshot() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)
        inbound.handleEvent(
            SERIAL,
            "state",
            JSONObject("""{"lockState":"unlocked","selectedChannel":2,"ts":300}""")
        )

        inbound.handleEvent(
            SERIAL,
            "state",
            JSONObject("""{"lockState":"locked","selectedChannel":1,"ts":100}""")
        )

        val state = repo.devices.value.single()
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(2, state.selectedChannel)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun retainedLifecycleOnlineRestoresCurrentBrokerState() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"type":"offline","deviceId":"T0000001","ts":12000}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"type":"online","deviceId":"T0000001","ts":12345}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(12345L, state.timestamp)
    }

    @Test
    fun staleRetainedLifecycleOnlineCannotOverrideNewerOffline() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"type":"offline","deviceId":"T0000001","ts":12000}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"type":"online","deviceId":"T0000001","ts":11000}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(12000L, state.timestamp)
    }

    @Test
    fun retainedStatusRestoresTelemetryWithoutInventingOnlineState() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"online":true,"lockState":"unlocked","selectedChannel":1}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(1, state.selectedChannel)
    }

    @Test
    fun retainedLifecycleOfflineMarksDeviceOffline() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"type":"online","deviceId":"T0000001","ts":1000}"""),
            isRetained = true
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"type":"offline","deviceId":"T0000001","ts":2000}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(2000L, state.timestamp)
    }

    @Test
    fun ackMergesReturnedSafetyState() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "ack",
            json = JSONObject(
                """
                {
                  "type": "ack",
                  "msgId": "fire-1",
                  "ofCmd": 12,
                  "ofType": "FIRE_CHANNEL",
                  "ok": true,
                  "code": 0,
                  "msg": "accepted",
                  "lockState": "unlocked",
                  "selectedChannel": 1,
                  "laserEnabled": false,
                  "fireState": "firing"
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals("fire-1", state.lastAck?.msgId)
        assertEquals(true, state.lastAck?.ok)
        assertEquals(12, state.lastAck?.ofCmd)
        assertEquals("FIRE_CHANNEL", state.lastAck?.ofType)
        assertEquals(1, state.selectedChannel)
        assertEquals(false, state.laserEnabled)
        assertEquals(GlassBreakerFireState.Idle, state.fireState)
    }

    @Test
    fun rejectedFireAckMapsEnglishMessageForUser() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "ack",
            json = JSONObject("""{"msgId":"fire-2","ok":false,"code":400,"msg":"unsupported or rejected"}""")
        )

        val ack = repo.devices.value.single().lastAck
        assertEquals("fire-2", ack?.msgId)
        assertEquals(false, ack?.ok)
        assertEquals(400, ack?.code)
        assertEquals("unsupported or rejected", ack?.message)
        assertEquals("设备拒绝命令", ack?.userFacingMessage())
    }

    @Test
    fun malformedOptionalNumbersRemainUnknownInsteadOfBecomingZero() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"battery":"unknown","armRemainingMs":"bad","ts":"invalid"}""")
        )

        val state = repo.devices.value.single()
        assertNull(state.batteryPercent)
        assertNull(state.armRemainingMs)
        assertNull(state.timestamp)
    }

    @Test
    fun partialTelemetryDoesNotResetSafetyState() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """{"lockState":"unlocked","selectedChannel":3,"laserEnabled":true,"fireState":"armed","armRemainingMs":12000,"ts":100}"""
            )
        )

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"battery":86,"firmwareVersion":"1.2.3","ts":200}""")
        )

        val state = repo.devices.value.single()
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(3, state.selectedChannel)
        assertEquals(true, state.laserEnabled)
        assertEquals("armed", state.fireState)
        assertEquals(12000L, state.armRemainingMs)
        assertEquals(86, state.batteryPercent)
        assertEquals("1.2.3", state.firmwareVersion)
    }

    private companion object {
        const val SERIAL = "T0000001"
    }
}
