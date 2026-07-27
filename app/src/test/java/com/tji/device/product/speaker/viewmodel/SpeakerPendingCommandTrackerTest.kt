package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerAck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerPendingCommandTrackerTest {
    @Test
    fun acknowledgementCompletesWaiterAndReturnsCommandLabel() = runBlocking {
        val tracker = SpeakerPendingCommandTracker()
        val waiter = CompletableDeferred<SpeakerAck>()
        tracker.track("cmd-1", "开始喊话", waiter)
        val ack = SpeakerAck(
            msgId = "cmd-1",
            ofType = "startTalk",
            ofCmd = 1,
            ok = true,
            code = 0,
            message = "ok",
            timestamp = null
        )

        assertEquals("开始喊话", tracker.acknowledge(ack))
        assertEquals(ack, waiter.await())
        assertEquals(0, tracker.size())
    }

    @Test
    fun timeoutRemovalMakesLateAckIrrelevant() {
        val tracker = SpeakerPendingCommandTracker()
        tracker.track("cmd-1", "音量设置")

        assertTrue(tracker.remove("cmd-1"))
        assertFalse(tracker.remove("cmd-1"))
        assertNull(
            tracker.acknowledge(
                SpeakerAck(
                    msgId = "cmd-1",
                    ofType = "setVolume",
                    ofCmd = 2,
                    ok = true,
                    code = 0,
                    message = "ok",
                    timestamp = null
                )
            )
        )
    }

    @Test
    fun concurrentAckAndTimeoutCleanupLeavesNoPendingCommands() = runBlocking {
        val tracker = SpeakerPendingCommandTracker()
        repeat(500) { tracker.track("cmd-$it", "命令 $it") }

        (0 until 500).map { index ->
            async(Dispatchers.Default) {
                if (index % 2 == 0) {
                    tracker.acknowledge(
                        SpeakerAck(
                            msgId = "cmd-$index",
                            ofType = "test",
                            ofCmd = 0,
                            ok = true,
                            code = 0,
                            message = "ok",
                            timestamp = null
                        )
                    )
                } else {
                    tracker.remove("cmd-$index")
                }
            }
        }.awaitAll()

        assertEquals(0, tracker.size())
    }
}
