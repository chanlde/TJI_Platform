package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SpeakerPendingRecordSaveTrackerTest {
    @Test
    fun staleTaskCannotClearReplacement() {
        val tracker = SpeakerPendingRecordSaveTracker()
        val first = pendingSave("record-1", startedAt = 1L)
        val second = pendingSave("record-2", startedAt = 2L)

        assertNull(tracker.replace(first))
        assertSame(first, tracker.replace(second))

        assertNull(tracker.takeIfCurrent(first))
        assertSame(second, tracker.current())
    }

    @Test
    fun duplicateCompletionCanClaimTaskOnlyOnce() {
        val tracker = SpeakerPendingRecordSaveTracker()
        val pending = pendingSave("record-1")
        tracker.replace(pending)

        assertSame(pending, tracker.takeIfCurrent(pending))
        assertNull(tracker.takeIfCurrent(pending))
        assertNull(tracker.current())
    }

    @Test
    fun concurrentCompletionAndTimeoutHaveSingleWinner() = runBlocking {
        val tracker = SpeakerPendingRecordSaveTracker()
        val pending = pendingSave("record-1")
        tracker.replace(pending)

        val results = List(100) {
            async(Dispatchers.Default) {
                tracker.takeIfCurrent(pending)
            }
        }.awaitAll()

        assertEquals(1, results.count { it === pending })
        assertEquals(99, results.count { it == null })
        assertNull(tracker.current())
    }

    @Test
    fun staleCompletionCannotCancelReplacementConfirmationJob() {
        val tracker = SpeakerPendingRecordSaveTracker()
        val first = pendingSave("record-1", startedAt = 1L)
        val second = pendingSave("record-2", startedAt = 2L)
        val firstConfirmation = Job()
        val secondConfirmation = Job()

        tracker.replace(first)
        tracker.replaceProgressConfirmationJob(first, firstConfirmation)
        tracker.replace(second)
        tracker.replaceProgressConfirmationJob(second, secondConfirmation)

        assertNull(tracker.takeIfCurrent(first))
        assertEquals(false, secondConfirmation.isCancelled)

        assertSame(second, tracker.takeIfCurrent(second))
        assertEquals(true, secondConfirmation.isCancelled)
    }

    private fun pendingSave(recordId: String, startedAt: Long = 1L) =
        PendingRecordSave(
            serialNumber = "speaker-1",
            recordId = recordId,
            commandMsgId = "command-$recordId",
            record = SpeakerRecord(recordId = recordId, name = recordId),
            startedAt = startedAt
        )
}
