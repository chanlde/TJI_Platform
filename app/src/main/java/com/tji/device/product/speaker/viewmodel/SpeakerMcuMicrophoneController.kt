package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerFeedbackRuntimeStats
import com.tji.device.product.speaker.audio.SpeakerFeedbackSession
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import com.tji.device.product.speaker.model.SpeakerCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理 MCU 板载麦克风监听的完整生命周期。
 *
 * 职责仅包括 relay 注册、cmd=116 租约续期、UDP 接收状态和关闭清理，
 * 不参与手机麦克风录音，也不把流式回传保存为 HADP 文件。
 */
internal class SpeakerMcuMicrophoneController(
    private val scope: CoroutineScope,
    private val receiver: SpeakerFeedbackReceiver,
    private val sendCommand: suspend (serialNumber: String, command: SpeakerCommand) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFactory: (String) -> SpeakerFeedbackSession = ::newMcuMicrophoneSession
) {
    private val _state = MutableStateFlow(SpeakerMcuMicrophoneState())
    val state: StateFlow<SpeakerMcuMicrophoneState> = _state.asStateFlow()

    private var listeningJob: Job? = null

    fun start(serialNumber: String) {
        if (listeningJob?.isActive == true) return
        val session = runCatching { sessionFactory(serialNumber) }
            .getOrElse {
                _state.value = SpeakerMcuMicrophoneState(
                    phase = SpeakerMcuMicrophonePhase.Failed,
                    error = it.toSpeakerUserVisibleMessage("设备麦克风监听参数无效")
                )
                return
            }
        _state.value = SpeakerMcuMicrophoneState(phase = SpeakerMcuMicrophonePhase.Connecting)

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            var enableCommandAttempted = false
            var failure: String? = null
            try {
                receiver.listen(
                    session = session,
                    onRegistered = {
                        enableCommandAttempted = true
                        sendEnableCommand(serialNumber, session, "mcu_mic_on")
                        _state.value = _state.value.copy(
                            phase = SpeakerMcuMicrophonePhase.Listening,
                            error = null
                        )
                    },
                    onLeaseRefresh = {
                        sendEnableCommand(serialNumber, session, "mcu_mic_keepalive")
                    },
                    onStats = { stats ->
                        _state.value = _state.value.withStats(stats)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                failure = throwable.toSpeakerUserVisibleMessage("设备麦克风监听失败")
            } finally {
                if (enableCommandAttempted) {
                    withContext(NonCancellable) {
                        runCatching {
                            sendCommand(
                                serialNumber,
                                SpeakerCommand.SetMcuMicrophoneFeedback(
                                    msgId = DeviceCommandId.next("speaker", "mcu_mic_off"),
                                    enabled = false
                                )
                            )
                        }
                    }
                }
                listeningJob = null
                _state.value = if (failure == null) {
                    SpeakerMcuMicrophoneState()
                } else {
                    SpeakerMcuMicrophoneState(
                        phase = SpeakerMcuMicrophonePhase.Failed,
                        error = failure
                    )
                }
            }
        }
        listeningJob = job
        job.start()
    }

    fun stop() {
        val job = listeningJob ?: run {
            _state.value = SpeakerMcuMicrophoneState()
            return
        }
        _state.value = _state.value.copy(phase = SpeakerMcuMicrophonePhase.Stopping)
        job.cancel()
    }

    private suspend fun sendEnableCommand(
        serialNumber: String,
        session: SpeakerFeedbackSession,
        action: String
    ) {
        sendCommand(
            serialNumber,
            SpeakerCommand.SetMcuMicrophoneFeedback(
                msgId = DeviceCommandId.next("speaker", action),
                enabled = true,
                sessionId = session.sessionId,
                talkId = session.talkId,
                ttlMs = MCU_MICROPHONE_LEASE_MS
            )
        )
    }

    private fun SpeakerMcuMicrophoneState.withStats(
        stats: SpeakerFeedbackRuntimeStats
    ): SpeakerMcuMicrophoneState =
        copy(
            packetsReceived = stats.packetsReceived,
            packetsPlayed = stats.packetsPlayed,
            packetsConcealed = stats.packetsConcealed,
            packetsRejected = stats.packetsRejected,
            duplicatePackets = stats.duplicatePackets
        )

    private companion object {
        const val MCU_MICROPHONE_LEASE_MS = 30_000L
    }
}

private val mcuMicrophoneSessionSequence = AtomicLong(0L)

private fun newMcuMicrophoneSession(serialNumber: String): SpeakerFeedbackSession {
    val deviceId = serialNumber.trim()
    val deviceLabel = deviceId.filter(Char::isLetterOrDigit)
        .take(10)
        .ifBlank { "SPEAKER" }
        .uppercase(Locale.US)
    val suffix = (
        System.currentTimeMillis().toString(36) +
            mcuMicrophoneSessionSequence.incrementAndGet().toString(36)
        )
        .uppercase(Locale.US)
        .takeLast(12)
    return SpeakerFeedbackSession(
        deviceId = deviceId,
        sessionId = "FB_${deviceLabel}_$suffix".take(32),
        talkId = "MON_${deviceLabel}_$suffix".take(32)
    )
}
