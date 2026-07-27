package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerFeedbackRuntimeStats
import com.tji.device.product.speaker.audio.SpeakerFeedbackSession
import com.tji.device.product.speaker.model.SpeakerCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class SpeakerMcuMicrophoneControllerTest {
    @Test
    fun registrationStartsMcuLeaseStatsFlowAndStopAlwaysSendsDisable() = runBlocking {
        val receiver = ControllableFeedbackReceiver()
        val commands = Collections.synchronizedList(mutableListOf<SpeakerCommand>())
        val controller = SpeakerMcuMicrophoneController(
            scope = this,
            receiver = receiver,
            sendCommand = { _, command -> commands += command },
            dispatcher = Dispatchers.Unconfined,
            sessionFactory = {
                SpeakerFeedbackSession("TEWNHZDBK", "FB_1", "MON_1")
            }
        )

        controller.start("TEWNHZDBK")
        receiver.started.await()
        assertEquals(SpeakerMcuMicrophonePhase.Connecting, controller.state.value.phase)

        receiver.onRegistered()
        assertEquals(SpeakerMcuMicrophonePhase.Listening, controller.state.value.phase)
        receiver.onStats(
            SpeakerFeedbackRuntimeStats(
                packetsReceived = 10,
                packetsPlayed = 9,
                packetsConcealed = 1
            )
        )
        receiver.onLeaseRefresh()
        assertEquals(10L, controller.state.value.packetsReceived)
        assertEquals(1L, controller.state.value.packetsConcealed)
        assertEquals(2, commands.filterIsInstance<SpeakerCommand.SetMcuMicrophoneFeedback>().count { it.enabled })

        controller.stop()
        withTimeout(1_000) {
            while (controller.state.value.phase != SpeakerMcuMicrophonePhase.Idle) delay(5)
        }

        val feedbackCommands = commands.filterIsInstance<SpeakerCommand.SetMcuMicrophoneFeedback>()
        assertEquals(3, feedbackCommands.size)
        assertTrue(feedbackCommands[0].enabled)
        assertTrue(feedbackCommands[1].enabled)
        assertFalse(feedbackCommands[2].enabled)
    }

    @Test
    fun receiverFailureIsVisibleAndStillDisablesMcu() = runBlocking {
        val commands = Collections.synchronizedList(mutableListOf<SpeakerCommand>())
        val receiver = object : SpeakerFeedbackReceiver {
            override suspend fun listen(
                session: SpeakerFeedbackSession,
                onRegistered: suspend () -> Unit,
                onLeaseRefresh: suspend () -> Unit,
                onStats: (SpeakerFeedbackRuntimeStats) -> Unit
            ) {
                onRegistered()
                error("broken stream")
            }
        }
        val controller = SpeakerMcuMicrophoneController(
            scope = this,
            receiver = receiver,
            sendCommand = { _, command -> commands += command },
            dispatcher = Dispatchers.Unconfined,
            sessionFactory = {
                SpeakerFeedbackSession("TEWNHZDBK", "FB_1", "MON_1")
            }
        )

        controller.start("TEWNHZDBK")
        withTimeout(1_000) {
            while (controller.state.value.phase != SpeakerMcuMicrophonePhase.Failed) delay(5)
        }

        assertEquals("设备麦克风监听失败", controller.state.value.error)
        val feedbackCommands = commands.filterIsInstance<SpeakerCommand.SetMcuMicrophoneFeedback>()
        assertEquals(2, feedbackCommands.size)
        assertTrue(feedbackCommands.first().enabled)
        assertFalse(feedbackCommands.last().enabled)
    }

    private class ControllableFeedbackReceiver : SpeakerFeedbackReceiver {
        val started = CompletableDeferred<Unit>()
        lateinit var onRegistered: suspend () -> Unit
        lateinit var onLeaseRefresh: suspend () -> Unit
        lateinit var onStats: (SpeakerFeedbackRuntimeStats) -> Unit

        override suspend fun listen(
            session: SpeakerFeedbackSession,
            onRegistered: suspend () -> Unit,
            onLeaseRefresh: suspend () -> Unit,
            onStats: (SpeakerFeedbackRuntimeStats) -> Unit
        ) {
            this.onRegistered = onRegistered
            this.onLeaseRefresh = onLeaseRefresh
            this.onStats = onStats
            started.complete(Unit)
            awaitCancellation()
        }
    }
}
