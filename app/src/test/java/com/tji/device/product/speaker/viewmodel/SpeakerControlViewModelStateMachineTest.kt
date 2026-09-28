package com.tji.device.product.speaker.viewmodel

import com.tji.device.MainDispatcherRule
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioTransport
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerFeedbackRuntimeStats
import com.tji.device.product.speaker.audio.SpeakerFeedbackSession
import com.tji.device.product.speaker.audio.SpeakerMediaTransferRequest
import com.tji.device.product.speaker.audio.SpeakerTtsEngine
import com.tji.device.product.speaker.audio.SpeakerTtsVoiceInventory
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakerControlViewModelStateMachineTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pushToTalkMovesFromIdleToRecordingAndCancelReturnsIdle() {
        val repository = SpeakerRepo()
        runBlocking { repository.updateOnlineStatus(SERIAL, true, timestamp = 1L) }
        val viewModel = viewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.bindDevice(SERIAL)

        viewModel.startPushToTalkRecord(SERIAL)
        assertEquals(SpeakerTalkMode.Recording, viewModel.talkState.value.mode)

        viewModel.cancelPushToTalkRecord(SERIAL)
        assertEquals(SpeakerTalkMode.Idle, viewModel.talkState.value.mode)
        viewModel.unbindDevice(SERIAL)
    }

    @Test
    fun offlineDeviceCannotEnterPushToTalkState() {
        val viewModel = viewModel(SpeakerRepo())
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.bindDevice(SERIAL)

        viewModel.startPushToTalkRecord(SERIAL)

        assertEquals(SpeakerTalkMode.Idle, viewModel.talkState.value.mode)
        assertTrue(viewModel.talkState.value.error.orEmpty().contains("设备离线"))
        assertEquals(SpeakerCommandFeedbackStatus.Failed, viewModel.feedback.value.status)
        viewModel.unbindDevice(SERIAL)
    }

    @Test
    fun unbindCancelsPageAudioStateAndClearsVisibleFeedback() {
        val repository = SpeakerRepo()
        runBlocking { repository.updateOnlineStatus(SERIAL, true, timestamp = 1L) }
        val viewModel = viewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.bindDevice(SERIAL)
        viewModel.startPushToTalkRecord(SERIAL)
        assertEquals(SpeakerTalkMode.Recording, viewModel.talkState.value.mode)

        viewModel.unbindDevice(SERIAL)

        assertEquals(SpeakerTalkState(), viewModel.talkState.value)
        assertEquals(SpeakerCommandFeedback(), viewModel.feedback.value)
    }

    private fun viewModel(repository: SpeakerRepo) = SpeakerControlViewModel(
        stateRepository = repository,
        controlRepository = object : SpeakerControlRepository {
            override suspend fun sendCommand(serialNumber: String, command: SpeakerCommand) = Unit
        },
        audioRelay = object : SpeakerAudioTransport {
            override suspend fun captureMicrophoneFrames(
                sampleRate: Int,
                onFrame: suspend (ByteArray) -> Unit
            ) = awaitCancellation()

            override suspend fun sendMedia(request: SpeakerMediaTransferRequest) = Unit
        },
        ttsSynthesizer = object : SpeakerTtsEngine {
            override suspend fun synthesizeToPcm(
                text: String,
                voicePreset: SpeakerTtsVoicePreset,
                targetSampleRate: Int
            ): ByteArray = ByteArray(0)

            override suspend fun inspectChineseVoices() = SpeakerTtsVoiceInventory(
                engineName = "fake",
                languageResult = 0,
                allVoiceCount = 0,
                chineseVoices = emptyList(),
                availablePresets = listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)
            )
        },
        feedbackReceiver = object : SpeakerFeedbackReceiver {
            override suspend fun listen(
                session: SpeakerFeedbackSession,
                onRegistered: suspend () -> Unit,
                onLeaseRefresh: suspend () -> Unit,
                onStats: (SpeakerFeedbackRuntimeStats) -> Unit
            ) = awaitCancellation()
        }
    )

    private companion object {
        const val SERIAL = "SPEAKER-01"
    }
}
