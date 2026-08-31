package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.runCatchingPreservingCancellation
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerAudioTransport
import com.tji.device.product.speaker.audio.SpeakerMediaTransferMode
import com.tji.device.product.speaker.audio.SpeakerMediaTransferRequest
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import com.tji.device.product.speaker.core.SpeakerLogger
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerDeviceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns microphone capture, feedback pause/resume and delivery for the two PTT modes. */
internal class SpeakerPushToTalkCoordinator(
    private val scope: CoroutineScope,
    private val audioTransport: SpeakerAudioTransport,
    private val mcuMicrophoneController: SpeakerMcuMicrophoneController,
    private val devices: StateFlow<List<SpeakerDeviceState>>,
    private val outputQuality: StateFlow<SpeakerAudioQuality>,
    private val talkState: MutableStateFlow<SpeakerTalkState>,
    private val requireOnline: (String, String) -> Boolean,
    private val cancelPlayback: () -> Unit,
    private val transferRecord: suspend (SpeakerRecordTransferRequest) -> Unit
) {
    private var recordJob: Job? = null
    private var deliveryJob: Job? = null
    private var feedbackPauseJob: Job? = null
    private var feedbackWasPaused = false
    private var buffer: ByteArrayOutputStream? = null
    private var saveName: String? = null
    private var captureQuality: SpeakerAudioQuality? = null
    private val targetLock = SpeakerPttTargetLock()

    fun startDirect(serialNumber: String) {
        startCapture(
            serialNumber = serialNumber,
            action = "开始按住喊话",
            quality = SpeakerAudioQuality.High,
            mode = SpeakerTalkMode.Recording,
            recordName = null,
            failurePrefix = "按住录音失败"
        )
    }

    fun startSave(serialNumber: String, defaultName: String?) {
        startCapture(
            serialNumber = serialNumber,
            action = "开始保存录音",
            quality = outputQuality.value,
            mode = SpeakerTalkMode.RecordingToStore,
            recordName = defaultName?.trim()?.takeIf { it.isNotBlank() } ?: defaultRecordName(),
            failurePrefix = "保存录音失败"
        )
    }

    private fun startCapture(
        serialNumber: String,
        action: String,
        quality: SpeakerAudioQuality,
        mode: SpeakerTalkMode,
        recordName: String?,
        failurePrefix: String
    ) {
        if (!requireOnline(serialNumber, action)) return
        if (recordJob?.isActive == true || deliveryJob?.isActive == true) return
        if (!targetLock.claim(serialNumber)) return
        cancelPlayback()
        if (!beginFeedbackPause(serialNumber)) {
            targetLock.clearIfOwnedBy(serialNumber)
            return
        }
        captureQuality = quality
        saveName = recordName
        buffer = ByteArrayOutputStream()
        talkState.value = SpeakerTalkState(mode = mode)
        recordJob = scope.launch(Dispatchers.IO) {
            val failure = runCatchingPreservingCancellation {
                audioTransport.captureMicrophoneFrames(quality.sampleRate, ::appendFrame)
            }.exceptionOrNull()
            if (failure !is CancellationException) {
                finishInterrupted(
                    serialNumber,
                    failure?.toSpeakerUserVisibleMessage(failurePrefix) ?: "录音采集已中断"
                )
            }
        }
    }

    fun finishDirect() {
        val captureJob = recordJob ?: return
        val serialNumber = targetLock.release() ?: return cancel()
        recordJob = null
        deliveryJob = scope.launch(Dispatchers.IO) {
            captureJob.cancelAndJoin()
            val feedbackRestored = withContext(NonCancellable) {
                restoreFeedback(serialNumber)
            }
            val pcm = takePcm()
            val quality = takeQuality()
            if (!feedbackRestored) return@launch failFeedbackRestore()
            if (pcm.isEmpty()) return@launch failTooShort()
            talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Sending)
            runCatchingPreservingCancellation {
                check(SpeakerCoreAudioEngine.hasPushToTalkSpeech(pcm, quality.sampleRate)) {
                    "未检测到有效语音，请靠近手机麦克风后重试"
                }
                val processedPcm = SpeakerCoreAudioEngine.processPushToTalk(pcm, quality.sampleRate)
                val playbackPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = processedPcm,
                    durationMs = SpeakerAudioConfig.Timing.RECORDED_LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val timestamp = System.currentTimeMillis()
                val sessionId = speakerTransferRouteId("PTT", serialNumber, timestamp)
                val talkId = speakerTransferRouteId("TALK", serialNumber, timestamp)
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = playbackPcm,
                    recordId = talkId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs,
                    bitrate = SpeakerAudioConfig.DirectPtt.BITRATE
                )
                val volume = devices.value.firstOrNull { it.serialNumber == serialNumber }
                    ?.volume ?: DEFAULT_SPEAKER_VOLUME
                audioTransport.sendMedia(
                    SpeakerMediaTransferRequest(
                        deviceId = serialNumber,
                        sessionId = sessionId,
                        recordId = talkId,
                        name = "喊话",
                        createdAt = isoNow(),
                        opusFile = opusFile,
                        mode = SpeakerMediaTransferMode.PlayTemporary,
                        volume = volume,
                        visible = false
                    )
                )
                talkState.value = talkState.value.copy(mode = SpeakerTalkMode.Idle)
            }.onFailure { throwable ->
                talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toSpeakerUserVisibleMessage("语音发送失败")
                )
            }
        }
    }

    fun finishSave() {
        val captureJob = recordJob ?: return
        val serialNumber = targetLock.release() ?: return cancel()
        recordJob = null
        deliveryJob = scope.launch(Dispatchers.IO) {
            captureJob.cancelAndJoin()
            val feedbackRestored = withContext(NonCancellable) {
                restoreFeedback(serialNumber)
            }
            val pcm = takePcm()
            val recordName = saveName ?: defaultRecordName()
            saveName = null
            val quality = takeQuality()
            if (!feedbackRestored) return@launch failFeedbackRestore()
            if (pcm.isEmpty()) return@launch failTooShort()
            talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.SavingRecord, progress = 0.10f)
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                talkState.value = talkState.value.copy(progress = 0.25f)
                val timestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("REC", serialNumber, timestamp)
                val storeTaskId = speakerTransferRouteId("STORE", serialNumber, timestamp)
                check(SpeakerCoreAudioEngine.hasPushToTalkSpeech(pcm, quality.sampleRate)) {
                    "未检测到有效语音，请靠近手机麦克风后重试"
                }
                val processedPcm = SpeakerCoreAudioEngine.processPushToTalk(pcm, quality.sampleRate)
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = processedPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                transferRecord(
                    SpeakerRecordTransferRequest(
                        serialNumber = serialNumber,
                        recordId = recordId,
                        storeTaskId = storeTaskId,
                        createdAt = isoNow(),
                        recordName = recordName,
                        opusFile = opusFile,
                        label = "录音",
                        downloadMsgPrefix = "record-download",
                        autoPlayVolume = null,
                        autoPlayLabel = "播放录音",
                        startedAt = startedAt
                    )
                )
            }.onFailure { throwable ->
                talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toSpeakerUserVisibleMessage("保存录音失败")
                )
            }
        }
    }

    fun cancel(serialNumber: String? = null) {
        val target = targetLock.clearAndGetIfOwnedBy(serialNumber) ?: return
        recordJob?.cancel()
        recordJob = null
        clearCapture()
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
        if (feedbackWasPaused || feedbackPauseJob != null) scheduleFeedbackRestore(target)
    }

    fun stop() {
        deliveryJob?.cancel()
        deliveryJob = null
        cancel()
    }

    /** Page/session teardown: the MCU listener owner is stopping, so never schedule a resume. */
    fun unbind() {
        deliveryJob?.cancel()
        deliveryJob = null
        recordJob?.cancel()
        recordJob = null
        feedbackPauseJob?.cancel()
        feedbackPauseJob = null
        feedbackWasPaused = false
        targetLock.clearAndGetIfOwnedBy(null)
        clearCapture()
        talkState.value = SpeakerTalkState()
    }

    private fun appendFrame(frame: ByteArray) {
        val activeBuffer = buffer ?: return
        val sampleRate = captureQuality?.sampleRate ?: return
        val remaining = sampleRate * Short.SIZE_BYTES * MAX_PTT_DURATION_SECONDS - activeBuffer.size()
        if (remaining > 0) activeBuffer.write(frame, 0, minOf(frame.size, remaining))
    }

    private fun finishInterrupted(serialNumber: String, message: String) {
        if (!targetLock.clearIfOwnedBy(serialNumber)) return
        recordJob = null
        clearCapture()
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
        if (feedbackWasPaused || feedbackPauseJob != null) scheduleFeedbackRestore(serialNumber)
    }

    private fun beginFeedbackPause(serialNumber: String): Boolean {
        if (feedbackPauseJob != null || feedbackWasPaused) return failFeedbackBusy()
        when (mcuMicrophoneController.beginPushToTalkPause(serialNumber)) {
            SpeakerPushToTalkPauseResult.NotListening -> {
                feedbackWasPaused = false
                return true
            }
            SpeakerPushToTalkPauseResult.Busy -> {
                feedbackWasPaused = false
                return failFeedbackBusy()
            }
            SpeakerPushToTalkPauseResult.Paused -> feedbackWasPaused = true
        }
        feedbackPauseJob = scope.launch(Dispatchers.IO) {
            if (!mcuMicrophoneController.confirmPushToTalkPause(serialNumber)) {
                SpeakerLogger.warn(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "MCU 未确认暂停回传；手机端仍保持暂停以保护录音"
                )
            }
        }
        return true
    }

    private fun failFeedbackBusy(): Boolean {
        talkState.value = SpeakerTalkState(
            mode = SpeakerTalkMode.Idle,
            error = "设备麦克风监听正在切换，请稍后再按住喊话"
        )
        return false
    }

    private suspend fun restoreFeedback(serialNumber: String): Boolean {
        val pauseJob = feedbackPauseJob
        val shouldResume = feedbackWasPaused
        feedbackPauseJob = null
        feedbackWasPaused = false
        pauseJob?.join()
        return !shouldResume || mcuMicrophoneController.resumeAfterPushToTalk(serialNumber)
    }

    private fun scheduleFeedbackRestore(serialNumber: String) {
        lateinit var cleanup: Job
        cleanup = scope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) { restoreFeedback(serialNumber) }
            } finally {
                if (deliveryJob === cleanup) deliveryJob = null
            }
        }
        deliveryJob = cleanup
    }

    private fun takePcm(): ByteArray = (buffer?.toByteArray() ?: ByteArray(0)).also { buffer = null }

    private fun takeQuality(): SpeakerAudioQuality =
        (captureQuality ?: SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY).also { captureQuality = null }

    private fun clearCapture() {
        buffer = null
        saveName = null
        captureQuality = null
    }

    private fun failFeedbackRestore() {
        talkState.value = SpeakerTalkState(
            mode = SpeakerTalkMode.Idle,
            error = "设备麦克风监听恢复失败，请关闭监听后重新开启"
        )
    }

    private fun failTooShort() {
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = "录音时间太短")
    }

    private fun defaultRecordName(): String =
        "录音 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA).format(Date())

    private companion object {
        const val MAX_PTT_DURATION_SECONDS = 60
    }
}
