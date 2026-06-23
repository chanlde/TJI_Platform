package com.tji.device.product.speaker.repository

import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeakerRepositoryTest {

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
        assertEquals(1024, status?.totalBytes)
        assertEquals(512, status?.freeBytes)
    }

    private fun record(recordId: String, createdMs: Long): SpeakerRecord =
        SpeakerRecord(recordId = recordId, name = recordId, createdMs = createdMs)

    private companion object {
        const val SERIAL = "T5RC26UI2"
    }
}
