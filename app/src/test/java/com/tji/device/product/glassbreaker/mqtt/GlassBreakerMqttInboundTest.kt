package com.tji.device.product.glassbreaker.mqtt

import com.tji.device.product.glassbreaker.model.GlassBreakerFireState
import com.tji.device.product.glassbreaker.model.GlassBreakerLockState
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassBreakerMqttInboundTest {

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
        assertEquals(false, state.isOnline)
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
    fun statusDoesNotMarkDeviceOnline() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"online":true,"lockState":"unlocked","selectedChannel":1}""")
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(1, state.selectedChannel)
    }

    @Test
    fun retainedLifecycleOnlineMarksDeviceOnline() = runBlocking {
        val repo = GlassBreakerRepo()
        val inbound = GlassBreakerMqttInbound(repo)

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

    private companion object {
        const val SERIAL = "T0000001"
    }
}
