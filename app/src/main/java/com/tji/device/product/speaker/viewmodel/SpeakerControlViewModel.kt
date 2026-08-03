package com.tji.device.product.speaker.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.common.runCatchingPreservingCancellation
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerMediaTransferMode
import com.tji.device.product.speaker.audio.SpeakerMediaTransferRequest
import com.tji.device.product.speaker.audio.SpeakerOpusFile
import com.tji.device.product.speaker.audio.SpeakerMicrophoneFormat
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.core.SpeakerLogger
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeakerControlViewModel(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer,
    feedbackReceiver: SpeakerFeedbackReceiver
) : ViewModel() {
    val devices: StateFlow<List<SpeakerDeviceState>> = stateRepository.devices

    private val _feedback = MutableStateFlow(SpeakerCommandFeedback())
    val feedback: StateFlow<SpeakerCommandFeedback> = _feedback.asStateFlow()
    private val commandCoordinator = SpeakerCommandCoordinator(
        scope = viewModelScope,
        repository = controlRepository,
        feedback = _feedback,
        requireOnline = ::requireOnlineDevice,
        clearFeedbackAfter = ::clearFeedbackAfter
    )
    private val deviceActions = SpeakerDeviceActions(commandCoordinator) { message ->
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }
    private val mcuMicrophoneController = SpeakerMcuMicrophoneController(
        scope = viewModelScope,
        receiver = feedbackReceiver,
        sendCommand = controlRepository::sendCommand,
        sendCommandAndAwaitAck = commandCoordinator::sendAndAwaitAck
    )
    val mcuMicrophoneState: StateFlow<SpeakerMcuMicrophoneState> =
        mcuMicrophoneController.state

    private val _talkState = MutableStateFlow(SpeakerTalkState())
    val talkState: StateFlow<SpeakerTalkState> = _talkState.asStateFlow()
    private val recordSaveCoordinator = SpeakerRecordSaveCoordinator(
        scope = viewModelScope,
        stateRepository = stateRepository,
        controlRepository = controlRepository,
        audioRelay = audioRelay,
        commands = commandCoordinator,
        deviceActions = deviceActions,
        devices = devices,
        feedback = _feedback,
        talkState = _talkState,
        clearFeedbackAfter = ::clearFeedbackAfter
    )

    private val _outputGain = MutableStateFlow(SpeakerAudioConfig.Gain.DEFAULT_OUTPUT_GAIN)
    val outputGain: StateFlow<Float> = _outputGain.asStateFlow()
    private val _mcuMicrophoneGain =
        MutableStateFlow(SpeakerAudioConfig.Gain.MCU_MONITOR_OUTPUT_GAIN)
    val mcuMicrophoneGain: StateFlow<Float> = _mcuMicrophoneGain.asStateFlow()
    private val _mcuMicrophoneCaptureActive = MutableStateFlow(false)
    val mcuMicrophoneCaptureActive: StateFlow<Boolean> =
        _mcuMicrophoneCaptureActive.asStateFlow()

    private val ttsCoordinator = SpeakerTtsCoordinator(viewModelScope, ttsSynthesizer)
    val ttsVoicePreset: StateFlow<SpeakerTtsVoicePreset> = ttsCoordinator.voicePreset
    val availableTtsVoicePresets: StateFlow<List<SpeakerTtsVoicePreset>> =
        ttsCoordinator.availableVoicePresets

    private val _outputQuality = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY)
    val outputQuality: StateFlow<SpeakerAudioQuality> = _outputQuality.asStateFlow()

    private val latestAudioOperation = LatestAudioOperation()
    private var pttRecordJob: Job? = null
    private var pttDeliveryJob: Job? = null
    private var pttFeedbackPauseJob: Job? = null
    private var pttFeedbackWasPaused = false
    private var pttBuffer: ByteArrayOutputStream? = null
    private var pttSaveName: String? = null
    private var pttCaptureQuality: SpeakerAudioQuality? = null
    private val pttTargetLock = SpeakerPttTargetLock()
    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach(commandCoordinator::handleAck)
            }
        }
        viewModelScope.launch {
            stateRepository.recordEvents.collect { envelope ->
                recordSaveCoordinator.handleEvent(envelope.serialNumber, envelope.event)
            }
        }
        viewModelScope.launch {
            mcuMicrophoneState.collect { state ->
                if (state.phase != SpeakerMcuMicrophonePhase.Listening) {
                    _mcuMicrophoneCaptureActive.value = false
                }
            }
        }
    }

    // 基础控制
    fun stop(serialNumber: String) {
        stopAllAudioOperations()
        send(serialNumber, SpeakerCommand.Stop(newMsgId("stop")), "停止")
    }

    fun setVolume(serialNumber: String, volume: Int) {
        val normalizedVolume = volume.coerceIn(0, 100)
        _outputGain.value = percentToOutputGain(normalizedVolume)
        SpeakerLogger.debug(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "speaker volume commit serialNumber=$serialNumber volumePercent=$normalizedVolume"
        )
        deviceActions.setVolume(serialNumber, normalizedVolume)
    }

    fun setTtsVoicePreset(preset: SpeakerTtsVoicePreset) {
        ttsCoordinator.selectVoice(preset)
    }

    fun setOutputQuality(quality: SpeakerAudioQuality) {
        _outputQuality.value = quality
    }

    /**
     * 开关设备板载 PDM 麦克风监听；不会停止 MCU 扬声器。
     */
    fun setMcuMicrophoneListening(serialNumber: String, enabled: Boolean) {
        if (enabled) {
            if (!requireOnlineDevice(serialNumber, "设备麦克风监听")) return
            mcuMicrophoneController.start(serialNumber)
        } else {
            stopMcuMicrophoneCapture()
            mcuMicrophoneController.stop()
        }
    }

    fun setMcuMicrophoneGain(gain: Float) {
        val normalized = gain.coerceIn(0f, 1f)
        _mcuMicrophoneGain.value = normalized
        mcuMicrophoneController.setPlaybackGain(normalized)
    }

    fun startMcuMicrophoneCapture() {
        _mcuMicrophoneCaptureActive.value =
            mcuMicrophoneController.startDebugCapture()
    }

    fun stopMcuMicrophoneCapture() {
        mcuMicrophoneController.stopDebugCapture()
        _mcuMicrophoneCaptureActive.value = false
    }

    fun setServoAngle(serialNumber: String, angle: Int, speedDps: Int = SPEAKER_SERVO_DEFAULT_SPEED_DPS) {
        deviceActions.setServoAngle(serialNumber, angle, speedDps)
    }

    fun testServo(serialNumber: String, speedDps: Int = SPEAKER_SERVO_DEFAULT_SPEED_DPS) {
        sweepServo(
            serialNumber = serialNumber,
            minAngle = SPEAKER_SERVO_MIN_ANGLE,
            maxAngle = SPEAKER_SERVO_MAX_ANGLE,
            speedDps = speedDps,
            cycles = 1,
            durationMs = SPEAKER_SERVO_DEFAULT_HOLD_MS
        )
    }

    fun sweepServo(
        serialNumber: String,
        minAngle: Int,
        maxAngle: Int,
        speedDps: Int,
        cycles: Int,
        durationMs: Int
    ) {
        deviceActions.sweepServo(
            serialNumber,
            minAngle,
            maxAngle,
            speedDps,
            cycles,
            durationMs
        )
    }

    fun stepServo(
        serialNumber: String,
        minAngle: Int,
        maxAngle: Int,
        stepAngle: Int,
        speedDps: Int,
        intervalMs: Int
    ) {
        deviceActions.stepServo(
            serialNumber,
            minAngle,
            maxAngle,
            stepAngle,
            speedDps,
            intervalMs
        )
    }

    fun getStatus(serialNumber: String) {
        deviceActions.getStatus(serialNumber)
    }

    fun refreshRecords(
        serialNumber: String,
        offset: Int = 0,
        limit: Int = RECORD_LIST_PAGE_SIZE,
        order: String = "desc"
    ) {
        deviceActions.refreshRecords(serialNumber, offset, limit, order)
    }

    fun refreshStorageStatus(serialNumber: String) {
        deviceActions.refreshStorageStatus(serialNumber)
    }

    fun playRecord(serialNumber: String, recordId: String, volumePercent: Int) {
        deviceActions.playRecord(serialNumber, recordId, volumePercent)
    }

    fun deleteRecord(serialNumber: String, recordId: String) {
        deviceActions.deleteRecord(serialNumber, recordId)
    }

    fun updateRecordName(serialNumber: String, recordId: String, name: String) {
        deviceActions.updateRecordName(serialNumber, recordId, name)
    }

    // TTS 合成、手机预览与文件播放
    fun speakText(serialNumber: String, text: String, volume: Int = DEFAULT_SPEAKER_VOLUME) {
        if (!requireOnlineDevice(serialNumber, "发送文字喊话")) return
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        stopAllAudioOperations()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts, progress = 0.10f)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在合成文字语音"
        )
        launchLatestAudioOperation {
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                val pcm = ttsCoordinator.synthesize(trimmed, _outputQuality.value.sampleRate)
                if (pcm.isEmpty()) error("文字语音合成音频为空")
                val quality = _outputQuality.value
                val processedPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = pcm,
                    durationMs = SpeakerAudioConfig.Timing.TTS_FILE_LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val routeTimestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("TTS", serialNumber, routeTimestamp)
                val storeTaskId = speakerTransferRouteId("STTS", serialNumber, routeTimestamp)
                val createdAt = isoNow()
                val recordName = "文字喊话 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = processedPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                SpeakerLogger.debug(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "tts temp file encoded recordId=$recordId engine=System " +
                        "fileSize=${opusFile.fileSize} codec=${opusFile.codec} uploadQuality=${quality.name} " +
                        "deviceOutputQuality=${_outputQuality.value.name} sampleRate=${opusFile.sampleRate} " +
                        "packets=${opusFile.packetCount} bitrate=${opusFile.bitrate} encodeMs=${System.currentTimeMillis() - startedAt}"
                )
                transferOpusAndAwaitResult(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = createdAt,
                    recordName = recordName,
                    recordType = "tts",
                    opusFile = opusFile,
                    label = "文字语音",
                    downloadMsgPrefix = "tts-download",
                    autoPlayVolume = volume.coerceIn(0, 100),
                    autoPlayLabel = "播放文字语音",
                    startedAt = startedAt,
                    temporary = true,
                    visible = false,
                    autoPlayInDownload = true,
                    fallbackPlayAfterSave = false
                )
            }.onFailure { throwable ->
                val message = throwable.toSpeakerUserVisibleMessage("文字语音失败")
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Failed,
                    text = message
                )
            }
        }
    }

    fun previewTtsOnPhone(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        stopAllAudioOperations()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成本机试听"
        )
        launchLatestAudioOperation {
            runCatchingPreservingCancellation {
                val quality = _outputQuality.value
                SpeakerLogger.debug(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "tts phone preview engine=System quality=${quality.name} sampleRate=${quality.sampleRate}"
                )
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在本机试听"
                )
                ttsCoordinator.preview(trimmed, quality.sampleRate)
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "本机试听完成"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                val message = throwable.toSpeakerUserVisibleMessage("本机试听失败")
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Failed,
                    text = message
                )
            }
        }
    }

    fun playToneTest(serialNumber: String) {
        if (!requireOnlineDevice(serialNumber, "播放蜂鸣")) return
        stopAllAudioOperations()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tone)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成蜂鸣"
        )
        launchLatestAudioOperation {
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                val quality = _outputQuality.value
                val tonePcm = SpeakerCoreAudioEngine.generateTonePcm16(
                    frequencyHz = SpeakerAudioConfig.Tone.FREQUENCY_HZ,
                    durationMs = SpeakerAudioConfig.Tone.DURATION_MS,
                    amplitude = SpeakerAudioConfig.Tone.AMPLITUDE * _outputGain.value,
                    sampleRate = quality.sampleRate,
                    minDurationMs = quality.packetMs
                )
                val playbackPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = tonePcm,
                    durationMs = SpeakerAudioConfig.Tone.LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val routeTimestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("TONE", serialNumber, routeTimestamp)
                val storeTaskId = speakerTransferRouteId("STONE", serialNumber, routeTimestamp)
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = playbackPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                transferOpusAndAwaitResult(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = isoNow(),
                    recordName = "蜂鸣测试",
                    recordType = "test",
                    opusFile = opusFile,
                    label = "蜂鸣",
                    downloadMsgPrefix = "tone-download",
                    autoPlayVolume = devices.value
                        .firstOrNull { it.serialNumber == serialNumber }
                        ?.volume ?: DEFAULT_SPEAKER_VOLUME,
                    autoPlayLabel = "播放蜂鸣",
                    startedAt = startedAt,
                    temporary = true,
                    visible = false,
                    autoPlayInDownload = true,
                    fallbackPlayAfterSave = false
                )
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
            }.onFailure { throwable ->
                val message = throwable.toSpeakerUserVisibleMessage("蜂鸣失败")
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Failed,
                    text = message
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    // 按住说话与录音保存
    fun startPushToTalkRecord(serialNumber: String) {
        if (!requireOnlineDevice(serialNumber, "开始按住喊话")) return
        if (pttRecordJob?.isActive == true || pttDeliveryJob?.isActive == true) return
        if (!pttTargetLock.claim(serialNumber)) return
        cancelPlaybackOperation()
        /*
         * 松手喊话固定使用真实 48 kHz；“低/中/高”仍用于 TTS 和保存录音，
         * 防止用户切换文件音质后意外破坏直接 UDP 协议。
         */
        val captureQuality = SpeakerAudioQuality.High
        if (!beginFeedbackPauseForCapture(serialNumber)) {
            pttTargetLock.clearIfOwnedBy(serialNumber)
            return
        }
        pttCaptureQuality = captureQuality
        pttSaveName = null
        pttBuffer = ByteArrayOutputStream()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Recording)
        pttRecordJob = viewModelScope.launch(Dispatchers.IO) {
            val failure = runCatchingPreservingCancellation {
                audioRelay.captureMicrophoneFrames(captureQuality.sampleRate) { frame ->
                    appendPttFrame(frame)
                }
            }.exceptionOrNull()
            if (failure !is CancellationException) {
                finishInterruptedPttCapture(
                    serialNumber,
                    failure?.toSpeakerUserVisibleMessage("按住录音失败") ?: "录音采集已中断"
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startPushToTalkSaveRecord(serialNumber: String, defaultName: String? = null) {
        if (!requireOnlineDevice(serialNumber, "开始保存录音")) return
        if (pttRecordJob?.isActive == true || pttDeliveryJob?.isActive == true) return
        if (!pttTargetLock.claim(serialNumber)) return
        cancelPlaybackOperation()
        val captureQuality = _outputQuality.value
        if (!beginFeedbackPauseForCapture(serialNumber)) {
            pttTargetLock.clearIfOwnedBy(serialNumber)
            return
        }
        pttCaptureQuality = captureQuality
        pttSaveName = defaultName?.trim()?.takeIf { it.isNotBlank() } ?: defaultRecordName()
        pttBuffer = ByteArrayOutputStream()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.RecordingToStore)
        pttRecordJob = viewModelScope.launch(Dispatchers.IO) {
            val failure = runCatchingPreservingCancellation {
                audioRelay.captureMicrophoneFrames(captureQuality.sampleRate) { frame ->
                    appendPttFrame(frame)
                }
            }.exceptionOrNull()
            if (failure !is CancellationException) {
                finishInterruptedPttCapture(
                    serialNumber,
                    failure?.toSpeakerUserVisibleMessage("保存录音失败") ?: "录音采集已中断"
                )
            }
        }
    }

    fun finishPushToTalkRecord() {
        val job = pttRecordJob ?: return
        val serialNumber = pttTargetLock.release() ?: return cancelPushToTalkRecord()
        pttRecordJob = null
        pttDeliveryJob = viewModelScope.launch(Dispatchers.IO) {
            job.cancelAndJoin()
            val feedbackRestored = withContext(NonCancellable) {
                restoreFeedbackAfterCapture(serialNumber)
            }
            val pcm = pttBuffer?.toByteArray() ?: ByteArray(0)
            val quality = pttCaptureQuality ?: SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY
            pttBuffer = null
            pttCaptureQuality = null
            if (!feedbackRestored) {
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = "设备麦克风监听恢复失败，请关闭监听后重新开启"
                )
                return@launch
            }
            if (pcm.isEmpty()) {
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = "录音时间太短")
                return@launch
            }
            _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Sending)
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                check(SpeakerCoreAudioEngine.hasPushToTalkSpeech(pcm, quality.sampleRate)) {
                    "未检测到有效语音，请靠近手机麦克风后重试"
                }
                val processedPcm = SpeakerCoreAudioEngine.processPushToTalk(
                    pcm16le = pcm,
                    sampleRate = quality.sampleRate
                )
                val playbackPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = processedPcm,
                    durationMs = SpeakerAudioConfig.Timing.RECORDED_LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val routeTimestamp = System.currentTimeMillis()
                val sessionId = speakerTransferRouteId("PTT", serialNumber, routeTimestamp)
                val talkId = speakerTransferRouteId("TALK", serialNumber, routeTimestamp)
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = playbackPcm,
                    recordId = talkId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs,
                    bitrate = SpeakerAudioConfig.DirectPtt.BITRATE
                )
                val volume = devices.value
                    .firstOrNull { it.serialNumber == serialNumber }
                    ?.volume ?: DEFAULT_SPEAKER_VOLUME
                audioRelay.sendMedia(
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
                SpeakerLogger.debug(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "direct ptt sent session=$sessionId rate=${opusFile.sampleRate} " +
                        "bitrate=${opusFile.bitrate} packets=${opusFile.packetCount} " +
                        "bytes=${opusFile.fileSize} elapsedMs=${System.currentTimeMillis() - startedAt}"
                )
                _talkState.value = _talkState.value.copy(mode = SpeakerTalkMode.Idle)
            }.onFailure { throwable ->
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toSpeakerUserVisibleMessage("语音发送失败")
                )
            }
        }
    }

    fun finishPushToTalkSaveRecord() {
        val job = pttRecordJob ?: return
        val serialNumber = pttTargetLock.release() ?: return cancelPushToTalkRecord()
        pttRecordJob = null
        pttDeliveryJob = viewModelScope.launch(Dispatchers.IO) {
            job.cancelAndJoin()
            val feedbackRestored = withContext(NonCancellable) {
                restoreFeedbackAfterCapture(serialNumber)
            }
            val pcm = pttBuffer?.toByteArray() ?: ByteArray(0)
            val recordName = pttSaveName ?: defaultRecordName()
            val quality = pttCaptureQuality ?: SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY
            pttBuffer = null
            pttSaveName = null
            pttCaptureQuality = null
            if (!feedbackRestored) {
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = "设备麦克风监听恢复失败，请关闭监听后重新开启"
                )
                return@launch
            }
            if (pcm.isEmpty()) {
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = "录音时间太短")
                return@launch
            }
            _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.SavingRecord, progress = 0.10f)
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                _talkState.value = _talkState.value.copy(progress = 0.25f)
                val routeTimestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("REC", serialNumber, routeTimestamp)
                val storeTaskId = speakerTransferRouteId("STORE", serialNumber, routeTimestamp)
                val createdAt = isoNow()
                check(SpeakerCoreAudioEngine.hasPushToTalkSpeech(pcm, quality.sampleRate)) {
                    "未检测到有效语音，请靠近手机麦克风后重试"
                }
                val processedPcm = SpeakerCoreAudioEngine.processPushToTalk(
                    pcm16le = pcm,
                    sampleRate = quality.sampleRate
                )
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = processedPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                SpeakerLogger.debug(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save encoded recordId=$recordId fileSize=${opusFile.fileSize} " +
                        "quality=${quality.name} sampleRate=${opusFile.sampleRate} " +
                        "packets=${opusFile.packetCount} bitrate=${opusFile.bitrate} " +
                        "encodeMs=${System.currentTimeMillis() - startedAt}"
                )
                transferOpusAndAwaitResult(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = createdAt,
                    recordName = recordName,
                    opusFile = opusFile,
                    label = "录音",
                    downloadMsgPrefix = "record-download",
                    autoPlayVolume = null,
                    autoPlayLabel = "播放录音",
                    startedAt = startedAt
                )
            }.onFailure { throwable ->
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toSpeakerUserVisibleMessage("保存录音失败")
                )
            }
        }
    }

    fun cancelPushToTalkRecord(serialNumber: String? = null) {
        val target = pttTargetLock.clearAndGetIfOwnedBy(serialNumber) ?: return
        pttRecordJob?.cancel()
        pttRecordJob = null
        pttBuffer = null
        pttSaveName = null
        pttCaptureQuality = null
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
        if (pttFeedbackWasPaused || pttFeedbackPauseJob != null) {
            scheduleFeedbackRestore(target)
        }
    }

    private fun appendPttFrame(frame: ByteArray) {
        val buffer = pttBuffer ?: return
        val sampleRate = pttCaptureQuality?.sampleRate ?: return
        val maxPcmBytes = sampleRate * Short.SIZE_BYTES * MAX_PTT_DURATION_SECONDS
        val remaining = maxPcmBytes - buffer.size()
        if (remaining <= 0) return
        buffer.write(frame, 0, minOf(frame.size, remaining))
    }

    private fun finishInterruptedPttCapture(serialNumber: String, message: String) {
        if (!pttTargetLock.clearIfOwnedBy(serialNumber)) return
        pttRecordJob = null
        pttBuffer = null
        pttSaveName = null
        pttCaptureQuality = null
        _talkState.value = SpeakerTalkState(
            mode = SpeakerTalkMode.Idle,
            error = message
        )
        if (pttFeedbackWasPaused || pttFeedbackPauseJob != null) {
            scheduleFeedbackRestore(serialNumber)
        }
    }

    /** 手机端先立即停止回传播放，再并行等待 MCU cmd116 OFF，录音无需丢开头。 */
    private fun beginFeedbackPauseForCapture(serialNumber: String): Boolean {
        if (pttFeedbackPauseJob != null || pttFeedbackWasPaused) {
            _talkState.value = SpeakerTalkState(
                mode = SpeakerTalkMode.Idle,
                error = "设备麦克风监听正在切换，请稍后再按住喊话"
            )
            return false
        }
        when (mcuMicrophoneController.beginPushToTalkPause(serialNumber)) {
            SpeakerPushToTalkPauseResult.NotListening -> {
                pttFeedbackWasPaused = false
                return true
            }
            SpeakerPushToTalkPauseResult.Busy -> {
                pttFeedbackWasPaused = false
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = "设备麦克风监听正在切换，请稍后再按住喊话"
                )
                return false
            }
            SpeakerPushToTalkPauseResult.Paused -> pttFeedbackWasPaused = true
        }
        pttFeedbackPauseJob = viewModelScope.launch(Dispatchers.IO) {
            val confirmed = mcuMicrophoneController.confirmPushToTalkPause(serialNumber)
            if (!confirmed) {
                SpeakerLogger.warn(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "MCU 未确认暂停回传；手机端仍保持暂停以保护录音"
                )
            }
        }
        return true
    }

    /** 等待暂停命令收尾，再恢复同一会话和 320 ms jitter 预缓冲。 */
    private suspend fun restoreFeedbackAfterCapture(serialNumber: String): Boolean {
        val pauseJob = pttFeedbackPauseJob
        val shouldResume = pttFeedbackWasPaused
        pttFeedbackPauseJob = null
        pttFeedbackWasPaused = false
        pauseJob?.join()
        return !shouldResume || mcuMicrophoneController.resumeAfterPushToTalk(serialNumber)
    }

    private fun scheduleFeedbackRestore(serialNumber: String) {
        lateinit var cleanup: Job
        cleanup = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    restoreFeedbackAfterCapture(serialNumber)
                }
            } finally {
                if (pttDeliveryJob === cleanup) pttDeliveryJob = null
            }
        }
        pttDeliveryJob = cleanup
    }

    private fun stopAllAudioOperations() {
        cancelPlaybackOperation()
        cancelPushToTalkOperations()
    }

    private fun cancelPlaybackOperation() {
        latestAudioOperation.cancel()
        ttsCoordinator.stopPreview()
        abandonPendingRecordSave()
    }

    private fun cancelPushToTalkOperations() {
        pttDeliveryJob?.cancel()
        pttDeliveryJob = null
        cancelPushToTalkRecord()
    }

    private fun abandonPendingRecordSave() {
        recordSaveCoordinator.clear()
    }

    private fun launchLatestAudioOperation(block: suspend () -> Unit) {
        lateinit var job: Job
        job = viewModelScope.launch(
            context = Dispatchers.IO,
            start = CoroutineStart.LAZY
        ) {
            try {
                block()
            } finally {
                latestAudioOperation.clearIfCurrent(job)
            }
        }
        latestAudioOperation.replaceWith(job)
        job.start()
    }

    // 统一 Ogg UDP 传输与 MCU 业务事件回执
    private suspend fun transferOpusAndAwaitResult(
        serialNumber: String,
        recordId: String,
        storeTaskId: String,
        createdAt: String,
        recordName: String,
        recordType: String = "record",
        opusFile: SpeakerOpusFile,
        label: String,
        downloadMsgPrefix: String,
        autoPlayVolume: Int?,
        autoPlayLabel: String,
        startedAt: Long,
        temporary: Boolean = false,
        visible: Boolean = true,
        autoPlayInDownload: Boolean = false,
        fallbackPlayAfterSave: Boolean = true
    ) {
        recordSaveCoordinator.transferAndAwaitResult(
            SpeakerRecordTransferRequest(
                serialNumber = serialNumber,
                recordId = recordId,
                storeTaskId = storeTaskId,
                createdAt = createdAt,
                recordName = recordName,
                recordType = recordType,
                opusFile = opusFile,
                label = label,
                downloadMsgPrefix = downloadMsgPrefix,
                autoPlayVolume = autoPlayVolume,
                autoPlayLabel = autoPlayLabel,
                startedAt = startedAt,
                temporary = temporary,
                visible = visible,
                autoPlayInDownload = autoPlayInDownload,
                fallbackPlayAfterSave = fallbackPlayAfterSave
            )
        )
    }

    private fun send(
        serialNumber: String,
        command: SpeakerCommand,
        label: String,
        awaitAck: Boolean = true
    ) = commandCoordinator.send(serialNumber, command, label, awaitAck)

    private fun requireOnlineDevice(serialNumber: String, action: String): Boolean {
        if (devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline == true) {
            return true
        }
        val message = "设备离线，无法$action"
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
        _talkState.value = SpeakerTalkState(
            mode = SpeakerTalkMode.Idle,
            error = message
        )
        clearFeedbackAfter(null)
        return false
    }

    private fun clearFeedbackAfter(msgId: String?) {
        val expectedFeedback = _feedback.value
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            // Local operations have no MQTT msgId. Object identity also prevents an
            // older command's timer from clearing feedback produced by newer work.
            if (_feedback.value === expectedFeedback && expectedFeedback.msgId == msgId) {
                _feedback.value = SpeakerCommandFeedback()
            }
        }
    }

    private fun newMsgId(action: String): String = DeviceCommandId.next("speaker", action)

    private fun defaultRecordName(): String =
        "录音 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA).format(Date())

    override fun onCleared() {
        mcuMicrophoneController.stop()
        stopAllAudioOperations()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_500L
        const val RECORD_LIST_PAGE_SIZE = 4
        const val MAX_PTT_DURATION_SECONDS = 60
        fun percentToOutputGain(volume: Int): Float =
            (volume.coerceIn(0, 100) / 100f) * SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN
    }
}
