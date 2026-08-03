package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerFeedbackRuntimeStats
import com.tji.device.product.speaker.audio.SpeakerFeedbackSession
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerAck
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理 MCU 板载麦克风监听的完整生命周期。
 *
 * 职责仅包括 relay 注册、cmd=116 租约续期、UDP 接收状态和关闭清理，
 * 不参与手机麦克风录音，也不把流式回传保存为 Ogg Opus 文件。
 */
internal class SpeakerMcuMicrophoneController(
    private val scope: CoroutineScope,
    private val receiver: SpeakerFeedbackReceiver,
    private val sendCommand: suspend (serialNumber: String, command: SpeakerCommand) -> Unit,
    private val sendCommandAndAwaitAck: suspend (
        serialNumber: String,
        command: SpeakerCommand,
        label: String
    ) -> SpeakerAck?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFactory: (String) -> SpeakerFeedbackSession = ::newMcuMicrophoneSession
) {
    private val _state = MutableStateFlow(SpeakerMcuMicrophoneState())
    val state: StateFlow<SpeakerMcuMicrophoneState> = _state.asStateFlow()

    private var listeningJob: Job? = null
    private var listeningGeneration: Long = 0L
    private var activeSerialNumber: String? = null
    private var activeSession: SpeakerFeedbackSession? = null
    @Volatile
    private var pausedForPushToTalk = false
    private val pushToTalkTransition = Mutex()

    fun start(serialNumber: String) {
        if (listeningJob?.isActive == true) return
        val generation = ++listeningGeneration
        val session = runCatching { sessionFactory(serialNumber) }
            .getOrElse {
                _state.value = SpeakerMcuMicrophoneState(
                    phase = SpeakerMcuMicrophonePhase.Failed,
                    error = it.toSpeakerUserVisibleMessage("设备麦克风监听参数无效")
                )
                return
            }
        _state.value = SpeakerMcuMicrophoneState(phase = SpeakerMcuMicrophonePhase.Connecting)
        activeSerialNumber = serialNumber
        activeSession = session
        pausedForPushToTalk = false

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            var enableCommandAttempted = false
            var failure: String? = null
            try {
                receiver.listen(
                    session = session,
                    onRegistered = {
                        enableCommandAttempted = true
                        check(sendEnableCommandAndAwaitAck(
                            serialNumber, session, "mcu_mic_on", "开启设备麦克风监听"
                        )) { "MCU 未确认开启麦克风回传" }
                        _state.value = _state.value.copy(
                            phase = SpeakerMcuMicrophonePhase.Listening,
                            error = null
                        )
                    },
                    onLeaseRefresh = {
                        if (!pausedForPushToTalk) {
                            sendEnableCommand(serialNumber, session, "mcu_mic_keepalive")
                        }
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
                /*
                 * A cancelled job can reach here after the user has already
                 * started a replacement session.  Only the current generation
                 * may send the device-wide OFF command or clear UI/job state.
                 */
                if (enableCommandAttempted && generation == listeningGeneration) {
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
                if (generation == listeningGeneration) {
                    listeningJob = null
                    activeSerialNumber = null
                    activeSession = null
                    pausedForPushToTalk = false
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

    /**
     * 立即停止手机端播放并锁存临时暂停状态。返回 [SpeakerPushToTalkPauseResult.Paused]
     * 表示原本正在监听；调用方随后确认 MCU 停流，并在录音结束后恢复。
     */
    fun beginPushToTalkPause(serialNumber: String): SpeakerPushToTalkPauseResult = synchronized(this) {
        if (activeSerialNumber != serialNumber ||
            _state.value.phase == SpeakerMcuMicrophonePhase.Idle ||
            _state.value.phase == SpeakerMcuMicrophonePhase.Failed) {
            return@synchronized SpeakerPushToTalkPauseResult.NotListening
        }
        if (_state.value.phase != SpeakerMcuMicrophonePhase.Listening || pausedForPushToTalk) {
            return@synchronized SpeakerPushToTalkPauseResult.Busy
        }
        pausedForPushToTalk = true
        receiver.pauseForPushToTalk()
        _state.value = _state.value.copy(temporarilyPaused = true)
        SpeakerPushToTalkPauseResult.Paused
    }

    /** 发送 cmd116 OFF 并等待 MCU 确认；失败时仍保持本地暂停，防止污染录音。 */
    suspend fun confirmPushToTalkPause(serialNumber: String): Boolean =
        pushToTalkTransition.withLock {
            if (!pausedForPushToTalk || activeSerialNumber != serialNumber) return@withLock false
            val command = SpeakerCommand.SetMcuMicrophoneFeedback(
                msgId = DeviceCommandId.next("speaker", "mcu_mic_ptt_pause"),
                enabled = false
            )
            sendCommandAndAwaitAck(serialNumber, command, "喊话期间暂停麦克风回传")?.ok == true
        }

    /** 重新开启同一回传会话，等待 MCU ACK 以及 App 端 320 ms 预缓冲完成。 */
    suspend fun resumeAfterPushToTalk(serialNumber: String): Boolean =
        pushToTalkTransition.withLock {
            val session = activeSession
            if (!pausedForPushToTalk) {
                return@withLock activeSerialNumber == serialNumber &&
                    listeningJob?.isActive == true &&
                    _state.value.phase == SpeakerMcuMicrophonePhase.Listening
            }
            if (activeSerialNumber != serialNumber || session == null ||
                listeningJob?.isActive != true) return@withLock false
            if (!sendEnableCommandAndAwaitAck(
                    serialNumber,
                    session,
                    "mcu_mic_ptt_resume",
                    "恢复设备麦克风监听"
                )) return@withLock false
            return@withLock runCatching {
                receiver.resumeAfterPushToTalk()
                pausedForPushToTalk = false
                _state.value = _state.value.copy(temporarilyPaused = false, error = null)
                true
            }.getOrElse { false }
        }

    fun setPlaybackGain(gain: Float) {
        receiver.setPlaybackGain(gain)
    }

    fun startDebugCapture(): Boolean = receiver.startDebugCapture()

    fun stopDebugCapture() {
        receiver.stopDebugCapture()
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

    private suspend fun sendEnableCommandAndAwaitAck(
        serialNumber: String,
        session: SpeakerFeedbackSession,
        action: String,
        label: String
    ): Boolean {
        val command = SpeakerCommand.SetMcuMicrophoneFeedback(
            msgId = DeviceCommandId.next("speaker", action),
            enabled = true,
            sessionId = session.sessionId,
            talkId = session.talkId,
            ttlMs = MCU_MICROPHONE_LEASE_MS
        )
        return sendCommandAndAwaitAck(serialNumber, command, label)?.ok == true
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

internal enum class SpeakerPushToTalkPauseResult {
    NotListening,
    Paused,
    Busy
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
