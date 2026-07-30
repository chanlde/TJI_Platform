package com.tji.device.product.speaker.mqtt

import com.tji.device.product.speaker.repository.SpeakerRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerMqttInboundTest {

    @Test
    fun statePayloadParsesCustomerRelevantDeviceFields() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """
                {
                  "name": "喊话器 01",
                  "playing": true,
                  "currentFile": "welcome.opus",
                  "volume": 124,
                  "servoAngle": -15,
                  "servo": {
                    "currentAngle": 45,
                    "targetAngle": 120,
                    "speedDps": 60,
                    "moving": true,
                    "sweepActive": true,
                    "stepMode": true,
                    "stepAngle": 10,
                    "minAngle": 30,
                    "maxAngle": 120,
                    "cyclesLeft": 5,
                    "cyclesDone": 1,
                    "infinite": false
                  },
                  "network": "wifi",
                  "lastError": "低电量",
                  "feedback": {
                    "active": true,
                    "packetMs": 20,
                    "aecActive": true,
                    "aecFrames": 123,
                    "aecReferenceMisses": 2,
                    "aecReferenceBuilt": 126,
                    "aecReferenceRead": 123,
                    "aecReferenceDrops": 1,
                    "aecReferenceQueued": 3,
                    "aecInputLevelQ15": 2400,
                    "aecOutputLevelQ15": 360,
                    "aecReferenceLevelQ15": 5000,
                    "aecResidualPermille": 150,
                    "aecLastUs": 3100,
                    "aecMaxUs": 4200,
                    "aecDeadlineMisses": 0,
                    "taskStackFreeWords": 912
                  },
                  "ts": 1710000000000
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals(SERIAL, state.serialNumber)
        assertEquals("喊话器 01", state.name)
        assertEquals(true, state.isOnline)
        assertEquals(true, state.playing)
        assertEquals("welcome.opus", state.currentFile)
        assertEquals(100, state.volume)
        assertEquals(-15, state.servoAngle)
        assertEquals(45, state.servo?.currentAngle)
        assertEquals(120, state.servo?.targetAngle)
        assertEquals(60, state.servo?.speedDps)
        assertEquals(true, state.servo?.moving)
        assertEquals(true, state.servo?.sweepActive)
        assertEquals(true, state.servo?.stepMode)
        assertEquals(10, state.servo?.stepAngle)
        assertEquals(30, state.servo?.minAngle)
        assertEquals(120, state.servo?.maxAngle)
        assertEquals(5, state.servo?.cyclesLeft)
        assertEquals(1, state.servo?.cyclesDone)
        assertEquals(false, state.servo?.infinite)
        assertEquals("wifi", state.network)
        assertEquals("低电量", state.lastError)
        assertEquals(true, state.mcuFeedback?.aecActive)
        assertEquals(20, state.mcuFeedback?.packetMs)
        assertEquals(123L, state.mcuFeedback?.aecFrames)
        assertEquals(2L, state.mcuFeedback?.aecReferenceMisses)
        assertEquals(126L, state.mcuFeedback?.aecReferenceBuilt)
        assertEquals(123L, state.mcuFeedback?.aecReferenceRead)
        assertEquals(1L, state.mcuFeedback?.aecReferenceDrops)
        assertEquals(3, state.mcuFeedback?.aecReferenceQueued)
        assertEquals(2_400, state.mcuFeedback?.aecInputLevelQ15)
        assertEquals(360, state.mcuFeedback?.aecOutputLevelQ15)
        assertEquals(5_000, state.mcuFeedback?.aecReferenceLevelQ15)
        assertEquals(150, state.mcuFeedback?.aecResidualPermille)
        assertEquals(3_100L, state.mcuFeedback?.aecLastUs)
        assertEquals(4_200L, state.mcuFeedback?.aecMaxUs)
        assertEquals(0L, state.mcuFeedback?.aecDeadlineMisses)
        assertEquals(912, state.mcuFeedback?.taskStackFreeWords)
        assertEquals(1710000000000L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun partialTelemetryDoesNotResetPlaybackOrControlState() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject(
                """{"playing":true,"talking":true,"currentTalkId":"talk-1","currentFile":"alarm.opus","volume":72,"servoAngle":45,"network":"wifi","ts":100}"""
            )
        )

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"lastError":"温度偏高","ts":200}""")
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.playing)
        assertEquals(true, state.talking)
        assertEquals("talk-1", state.currentTalkId)
        assertEquals("alarm.opus", state.currentFile)
        assertEquals(72, state.volume)
        assertEquals(45, state.servoAngle)
        assertEquals("wifi", state.network)
        assertEquals("温度偏高", state.lastError)
        assertEquals(200L, state.timestamp)
        inbound.cleanup()
    }

    @Test
    fun retainedStateRefreshesFieldsButDoesNotMarkOfflineDeviceOnline() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"volume":66,"playing":false,"ts":1710000000000}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(66, state.volume)
        assertEquals(false, state.playing)
        assertEquals(1710000000000L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun retainedLifecycleOnlineCannotReviveOfflineDevice() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"ts":1710000000000}""")
        )

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"ts":1710000000001}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(1710000000000L, state.timestamp)
        inbound.cleanup()
    }

    @Test
    fun malformedOptionalNumbersRemainUnknownInsteadOfBecomingZero() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"online":"unknown","servoAngle":"unknown","ts":"invalid"}""")
        )

        val state = repo.devices.value.single()
        assertNull(state.servoAngle)
        assertNull(state.timestamp)
        assertEquals(false, state.isOnline)
        inbound.cleanup()
    }

    @Test
    fun retainedStateRefreshesFieldsButPreservesCurrentOnlineState() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"ts":1710000000000}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"volume":66,"playing":false,"ts":1710000000001}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(66, state.volume)
        assertEquals(false, state.playing)
        assertEquals(1710000000001L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun explicitOfflineStateOverridesPreviousOnlineState() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "online",
            json = JSONObject("""{"ts":1710000000000}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"online":false,"volume":44,"ts":1710000000002}""")
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(44, state.volume)
        assertEquals(1710000000002L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun offlineLifecycleKeepsExistingStateAndMarksDeviceOffline() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "state",
            json = JSONObject("""{"name":"喊话器","volume":72,"playing":true}""")
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "offline",
            json = JSONObject("""{"ts":1710000000001}""")
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals("喊话器", state.name)
        assertEquals(72, state.volume)
        assertEquals(true, state.playing)
        assertEquals(1710000000001L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun staleStateAfterNewerOfflineCannotReviveOrOverwriteDevice() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)
        inbound.handleEvent(SERIAL, "state", JSONObject("""{"volume":72,"ts":200}"""))
        inbound.handleEvent(SERIAL, "offline", JSONObject("""{"ts":300}"""))

        inbound.handleEvent(SERIAL, "state", JSONObject("""{"volume":10,"ts":100}"""))

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(72, state.volume)
        assertEquals(300L, state.timestamp)
        inbound.cleanup()
    }

    @Test
    fun recordListDefaultsTotalToParsedRecordCountWhenServerOmitsTotal() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "record_list",
            json = JSONObject(
                """
                {
                  "items": [
                    { "recordId": "rec-1", "name": "起飞提醒", "fileSize": 1200, "durationMs": 2000 },
                    { "recordId": "rec-2", "fileSize": 2400, "durationMs": 4000 }
                  ],
                  "offset": -2,
                  "limit": 99,
                  "hasMore": true,
                  "ts": 1710000000100
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals(2, state.records.size)
        assertEquals("起飞提醒", state.records[0].name)
        assertEquals("rec-2", state.records[1].name)
        assertEquals(-2, state.recordOffset)
        assertEquals(8, state.recordLimit)
        assertEquals(2, state.recordTotal)
        assertEquals(true, state.recordHasMore)
        assertEquals(1710000000100L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun storageFailureKeepsPreviousCapacitySnapshotForStableUi() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "storage_status",
            json = JSONObject(
                """
                {
                  "ok": true,
                  "backend": "flash",
                  "totalBytes": 1000,
                  "freeBytes": 600,
                  "recordCount": 3,
                  "maxRecords": 8,
                  "ts": 1710000000200
                }
                """.trimIndent()
            )
        )
        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "storage_status",
            json = JSONObject("""{"ok":false,"code":503,"msg":"容量查询失败","ts":1710000000300}""")
        )

        val status = repo.devices.value.single().storageStatus
        assertEquals(false, status?.ok)
        assertEquals("容量查询失败", status?.message)
        assertEquals("flash", status?.backend)
        assertEquals(1000L, status?.totalBytes)
        assertEquals(600L, status?.freeBytes)
        assertEquals(3, status?.recordCount)
        assertEquals(8, status?.maxRecords)
        assertEquals(1710000000300L, repo.devices.value.single().timestamp)

        inbound.cleanup()
    }

    @Test
    fun recordEventUsesSafeDefaultsForCustomerFacingResult() = runBlocking {
        val repo = SpeakerRepo()
        val inbound = SpeakerMqttInbound(repo)

        inbound.handleEvent(
            serialNumber = SERIAL,
            eventType = "record_failed",
            json = JSONObject("""{"recordId":"rec-1","code":42,"msg":"存储空间不足","ts":1710000000400}""")
        )

        val event = repo.devices.value.single().lastRecordEvent
        assertEquals("record_failed", event?.type)
        assertEquals("rec-1", event?.recordId)
        assertEquals(false, event?.ok)
        assertEquals(42, event?.code)
        assertEquals("存储空间不足", event?.message)
        assertEquals(1710000000400L, event?.timestamp)

        inbound.cleanup()
    }

    private companion object {
        const val SERIAL = "SPK-001"
    }
}
