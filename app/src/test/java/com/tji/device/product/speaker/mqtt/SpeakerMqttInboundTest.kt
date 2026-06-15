package com.tji.device.product.speaker.mqtt

import com.tji.device.product.speaker.repository.SpeakerRepo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
                  "currentFile": "welcome.hadp",
                  "volume": 124,
                  "servoAngle": -15,
                  "network": "wifi",
                  "lastError": "低电量",
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
        assertEquals("welcome.hadp", state.currentFile)
        assertEquals(100, state.volume)
        assertEquals(-15, state.servoAngle)
        assertEquals("wifi", state.network)
        assertEquals("低电量", state.lastError)
        assertEquals(1710000000000L, state.timestamp)

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
