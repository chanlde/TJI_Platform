package com.tji.device.product.droppersixstage.mqtt

import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropperSixStageMqttInboundTest {

    @Test
    fun parsesPartialStagePayloadIntoStableSixStageList() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """
                {
                  "name": "六段抛投 01",
                  "battery": 88,
                  "firmware_version": "1.0.2",
                  "stages": [
                    { "stage": 2, "open": true, "loaded": false },
                    { "stage": 5, "open": true, "loaded": true }
                  ]
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals(SERIAL, state.serialNumber)
        assertEquals("六段抛投 01", state.name)
        assertEquals(true, state.isOnline)
        assertEquals(88, state.batteryPercent)
        assertEquals("1.0.2", state.firmwareVersion)
        assertEquals(6, state.stages.size)
        assertEquals(false, state.stages.first { it.index == 1 }.isOpen)
        assertEquals(true, state.stages.first { it.index == 2 }.isOpen)
        assertEquals(false, state.stages.first { it.index == 2 }.payloadLoaded)
        assertEquals(true, state.stages.first { it.index == 5 }.isOpen)
        assertEquals(true, state.stages.first { it.index == 5 }.payloadLoaded)
    }

    @Test
    fun retainedStateDoesNotMarkDeviceOnline() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":1,"open":true}]}"""),
            isRetained = true
        )

        assertEquals(false, repo.devices.value.single().isOnline)
    }

    @Test
    fun identityUsesPayloadDeviceIdAndFirmwareMetadata() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "identity",
            json = JSONObject(
                """
                {
                  "deviceId": "DROP-PAYLOAD-001",
                  "name": "六段抛投",
                  "online": true,
                  "fw": "2.1.0",
                  "ts": 1710000000000
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals("DROP-PAYLOAD-001", state.serialNumber)
        assertEquals("六段抛投", state.name)
        assertEquals(true, state.isOnline)
        assertEquals("2.1.0", state.firmwareVersion)
        assertEquals(1710000000000L, state.timestamp)
    }

    @Test
    fun retainedIdentityCannotMarkDeviceOnline() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "identity",
            json = JSONObject("""{"deviceId":"$SERIAL","online":true}"""),
            isRetained = true
        )

        assertEquals(false, repo.devices.value.single().isOnline)
    }

    @Test
    fun offlineLifecycleKeepsStateAndMarksDeviceOffline() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":4,"open":true}],"battery":72}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"ts":1710000000001}""")
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(true, state.stages.first { it.index == 4 }.isOpen)
        assertEquals(72, state.batteryPercent)
        assertEquals(1710000000001L, state.timestamp)
    }

    @Test
    fun staleStateAfterNewerOfflineCannotReviveOrOverwriteDevice() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)
        inbound.handleEvent(
            SERIAL,
            "state",
            JSONObject("""{"battery":72,"stages":[{"stage":4,"open":true}],"ts":200}""")
        )
        inbound.handleEvent(SERIAL, "offline", JSONObject("""{"ts":300}"""))

        inbound.handleEvent(
            SERIAL,
            "state",
            JSONObject("""{"battery":10,"stages":[{"stage":4,"open":false}],"ts":100}""")
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(72, state.batteryPercent)
        assertEquals(true, state.stages.first { it.index == 4 }.isOpen)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun malformedOrOutOfRangeStagePayloadKeepsStableDefaultStages() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """
                {
                  "stages": [
                    "bad",
                    { "stage": 8, "open": true },
                    { "stage": 2, "open": true }
                  ]
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals(6, state.stages.size)
        assertEquals(true, state.stages.first { it.index == 2 }.isOpen)
        assertEquals(false, state.stages.first { it.index == 1 }.isOpen)
        assertEquals(false, state.stages.any { it.index == 8 })
    }

    @Test
    fun successfulStageAckDoesNotGuessStateWithoutAnExplicitOpenCloseValue() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":3,"open":false}]}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "ack",
            json = JSONObject("""{"msgId":"stage-3-close","ok":true,"stage":3}""")
        )

        val state = repo.devices.value.single()
        assertEquals("stage-3-close", state.lastAck?.msgId)
        assertEquals(true, state.lastAck?.ok)
        assertEquals(false, state.stages.first { it.index == 3 }.isOpen)
    }

    @Test
    fun failedStageAckDoesNotChangeStageState() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":3,"open":false}]}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "ack",
            json = JSONObject("""{"msgId":"stage-3-open","ok":false,"stage":3,"msg":"busy"}""")
        )

        val state = repo.devices.value.single()
        assertEquals("stage-3-open", state.lastAck?.msgId)
        assertEquals(false, state.lastAck?.ok)
        assertEquals("busy", state.lastAck?.message)
        assertEquals(false, state.stages.first { it.index == 3 }.isOpen)
    }

    @Test
    fun retainedStateDoesNotOverrideOnlineStateButCanRefreshTelemetry() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"ts":1710000000000}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"battery":55,"firmware_version":"1.0.9","stages":[{"stage":1,"open":true}]}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(55, state.batteryPercent)
        assertEquals("1.0.9", state.firmwareVersion)
        assertEquals(true, state.stages.first { it.index == 1 }.isOpen)
    }

    @Test
    fun partialStageUpdatePreservesUnreportedStageState() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """{"stages":[{"stage":2,"open":true,"loaded":true},{"stage":5,"open":true,"loaded":false}],"ts":100}"""
            )
        )

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":2,"loaded":false}],"battery":64,"ts":200}""")
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.stages.first { it.index == 2 }.isOpen)
        assertEquals(false, state.stages.first { it.index == 2 }.payloadLoaded)
        assertEquals(true, state.stages.first { it.index == 5 }.isOpen)
        assertEquals(false, state.stages.first { it.index == 5 }.payloadLoaded)
        assertEquals(64, state.batteryPercent)
    }

    @Test
    fun identityRefreshDoesNotResetStageState() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"stages":[{"stage":4,"open":true,"loaded":true}],"ts":100}""")
        )

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "identity",
            json = JSONObject("""{"deviceId":"$SERIAL","fw":"2.0.0","ts":200}""")
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.stages.first { it.index == 4 }.isOpen)
        assertEquals(true, state.stages.first { it.index == 4 }.payloadLoaded)
        assertEquals("2.0.0", state.firmwareVersion)
    }

    @Test
    fun malformedOptionalNumbersRemainUnknownInsteadOfBecomingZero() = runBlocking {
        val repo = DropperSixStageRepo()
        val inbound = DropperSixStageMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"battery":"unknown","ts":"invalid"}""")
        )

        val state = repo.devices.value.single()
        assertNull(state.batteryPercent)
        assertNull(state.timestamp)
    }

    private companion object {
        const val SERIAL = "DROP-6-001"
    }
}
