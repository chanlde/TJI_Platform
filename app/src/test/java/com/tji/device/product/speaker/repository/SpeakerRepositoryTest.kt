package com.tji.device.product.speaker.repository

import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeakerRepositoryTest {

    @Test
    fun stateUpdateCanMarkPreviouslyOnlineDeviceOffline() = runBlocking {
        val repo = SpeakerRepo()

        repo.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 1000L)
        repo.updateState(SpeakerDeviceState(serialNumber = SERIAL, isOnline = false, volume = 60, timestamp = 2000L))

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(60, state.volume)
        assertEquals(2000L, state.timestamp)
    }

    @Test
    fun firstPageRefreshReplacesStaleDeletedRecord() = runBlocking {
        val repo = SpeakerRepo()

        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(
                record("REC_5", 5),
                record("REC_4", 4),
                record("REC_DELETED", 3),
                record("REC_2", 2)
            ),
            offset = 0,
            limit = 4,
            total = 6,
            hasMore = true,
            timestamp = null
        )
        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(
                record("REC_5", 5),
                record("REC_4", 4),
                record("REC_2", 2),
                record("REC_1", 1)
            ),
            offset = 0,
            limit = 4,
            total = 5,
            hasMore = true,
            timestamp = null
        )

        val state = repo.devices.value.single()
        assertEquals(5, state.recordTotal)
        assertFalse(state.records.any { it.recordId == "REC_DELETED" })
    }

    @Test
    fun recordNotFoundDeleteEventRemovesLocalStaleRecord() = runBlocking {
        val repo = SpeakerRepo()

        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(record("REC_2", 2), record("REC_STALE", 1)),
            offset = 0,
            limit = 4,
            total = 2,
            hasMore = false,
            timestamp = null
        )
        repo.updateRecordEvent(
            serialNumber = SERIAL,
            event = SpeakerRecordEvent(
                type = "record_deleted",
                recordId = "REC_STALE",
                ok = false,
                code = 404,
                message = "record not found"
            )
        )

        val state = repo.devices.value.single()
        assertEquals(1, state.recordTotal)
        assertFalse(state.records.any { it.recordId == "REC_STALE" })
    }

    @Test
    fun temporarySavedEventDoesNotEnterRecordList() = runBlocking {
        val repo = SpeakerRepo()

        repo.updateRecordEvent(
            serialNumber = SERIAL,
            event = SpeakerRecordEvent(
                type = "record_saved",
                recordId = "PTT_PLAY_$SERIAL",
                ok = true,
                path = "ram://temporary",
                visible = false
            )
        )

        val state = repo.devices.value.single()
        assertEquals(0, state.recordTotal)
        assertFalse(state.records.any { it.recordId == "PTT_PLAY_$SERIAL" })
    }

    @Test
    fun busyStorageStatusKeepsPreviousCapacity() = runBlocking {
        val repo = SpeakerRepo()

        repo.updateStorageStatus(
            serialNumber = SERIAL,
            status = SpeakerStorageStatus(
                ok = true,
                totalBytes = 1024,
                freeBytes = 512,
                recordCount = 3,
                maxRecords = 32
            )
        )
        repo.updateStorageStatus(
            serialNumber = SERIAL,
            status = SpeakerStorageStatus(
                ok = false,
                code = 486,
                message = "record store active"
            )
        )

        val status = repo.devices.value.single().storageStatus
        assertEquals(true, status?.ok)
        assertEquals(1024L, status?.totalBytes)
        assertEquals(512L, status?.freeBytes)
    }

    @Test
    fun olderStorageStatusCannotReplaceNewerCapacity() = runBlocking {
        val repo = SpeakerRepo()
        repo.updateStorageStatus(
            serialNumber = SERIAL,
            status = SpeakerStorageStatus(
                ok = true,
                totalBytes = 2048,
                freeBytes = 1024,
                recordCount = 4,
                maxRecords = 32,
                timestamp = 300
            )
        )
        repo.updateStorageStatus(
            serialNumber = SERIAL,
            status = SpeakerStorageStatus(
                ok = true,
                totalBytes = 512,
                freeBytes = 128,
                recordCount = 1,
                maxRecords = 8,
                timestamp = 200
            )
        )

        val state = repo.devices.value.single()
        assertEquals(2048L, state.storageStatus?.totalBytes)
        assertEquals(1024L, state.storageStatus?.freeBytes)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun olderRecordPageCannotRegressStateOrderingTimestamp() = runBlocking {
        val repo = SpeakerRepo()
        repo.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 300)

        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(record("REC_1", 1)),
            offset = 0,
            limit = 20,
            total = 1,
            hasMore = false,
            timestamp = 200
        )

        val state = repo.devices.value.single()
        assertEquals(listOf("REC_1"), state.records.map { it.recordId })
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun olderRecordPageCannotRestoreARecordRemovedByNewerEvent() = runBlocking {
        val repo = SpeakerRepo()
        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(record("REC_DELETED", 1)),
            offset = 0,
            limit = 20,
            total = 1,
            hasMore = false,
            timestamp = 100
        )
        repo.updateRecordEvent(
            serialNumber = SERIAL,
            event = SpeakerRecordEvent(
                type = "record_deleted",
                recordId = "REC_DELETED",
                ok = true,
                timestamp = 300
            )
        )

        repo.updateRecords(
            serialNumber = SERIAL,
            records = listOf(record("REC_DELETED", 1)),
            offset = 0,
            limit = 20,
            total = 1,
            hasMore = false,
            timestamp = 200
        )

        val state = repo.devices.value.single()
        assertEquals(emptyList<String>(), state.records.map { it.recordId })
        assertEquals(0, state.recordTotal)
        assertEquals(300L, state.recordListTimestamp)
    }

    private fun record(recordId: String, createdMs: Long): SpeakerRecord =
        SpeakerRecord(recordId = recordId, name = recordId, createdMs = createdMs)

    private companion object {
        const val SERIAL = "T5RC26UI2"
    }
}
