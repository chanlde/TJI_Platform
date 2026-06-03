package com.tji.device.product.speaker.viewmodel

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.audio.SpeakerVoiceProcessor
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class SpeakerControlViewModel(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer
) : ViewModel() {
    val devices: StateFlow<List<SpeakerDeviceState>> = stateRepository.devices

    private val _feedback = MutableStateFlow(SpeakerCommandFeedback())
    val feedback: StateFlow<SpeakerCommandFeedback> = _feedback.asStateFlow()

    private val _talkState = MutableStateFlow(SpeakerTalkState())
    val talkState: StateFlow<SpeakerTalkState> = _talkState.asStateFlow()

    private val _outputGain = MutableStateFlow(SpeakerAudioConfig.Gain.DEFAULT_OUTPUT_GAIN)
    val outputGain: StateFlow<Float> = _outputGain.asStateFlow()

    private val _toneSettings = MutableStateFlow(SpeakerToneSettings())
    val toneSettings: StateFlow<SpeakerToneSettings> = _toneSettings.asStateFlow()

    private val _ttsVoicePreset = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)
    val ttsVoicePreset: StateFlow<SpeakerTtsVoicePreset> = _ttsVoicePreset.asStateFlow()

    private val _availableTtsVoicePresets = MutableStateFlow(listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET))
    val availableTtsVoicePresets: StateFlow<List<SpeakerTtsVoicePreset>> = _availableTtsVoicePresets.asStateFlow()

    private val pendingCommands = mutableMapOf<String, String>()
    private var liveJob: Job? = null
    private var pttRecordJob: Job? = null
    private var pttBuffer: ByteArrayOutputStream? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ttsSynthesizer.inspectChineseVoices()
            }.onSuccess { inventory ->
                _availableTtsVoicePresets.value = inventory.availablePresets
                if (_ttsVoicePreset.value !in inventory.availablePresets) {
                    _ttsVoicePreset.value = SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET
                }
            }.onFailure { throwable ->
                Log.w(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "TTS voice inspect failed", throwable)
                _availableTtsVoicePresets.value = listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)
                _ttsVoicePreset.value = SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET
            }
        }
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach { ack ->
                        val label = pendingCommands.remove(ack.msgId) ?: return@forEach
                        val text = if (ack.ok) "${label}已确认" else "${label}失败：${ack.message}"
                        _feedback.value = SpeakerCommandFeedback(
                            msgId = ack.msgId,
                            status = if (ack.ok) SpeakerCommandFeedbackStatus.Success else SpeakerCommandFeedbackStatus.Failed,
                            text = text
                        )
                        clearFeedbackAfter(ack.msgId)
                    }
            }
        }
    }

    fun stop(serialNumber: String) {
        stopLocalAudio()
        viewModelScope.launch(Dispatchers.IO) {
            audioRelay.sendStreamReset()
        }
        send(serialNumber, SpeakerCommand.Stop(newMsgId("stop")), "停止")
    }

    fun setVolume(serialNumber: String, volume: Int) {
        setOutputGain(percentToOutputGain(volume))
    }

    fun setOutputGain(outputGain: Float) {
        val normalized = outputGain.coerceIn(0f, SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN)
        _outputGain.value = normalized
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "音量 ${"%.2f".format(normalized)}"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                audioRelay.sendOutputGain(normalized)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "音量已发送"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = throwable.message ?: "音量设置失败"
                    )
                }
            }
        }
    }

    fun setToneSettings(toneSettings: SpeakerToneSettings) {
        _toneSettings.value = toneSettings.normalized()
    }

    fun setTtsVoicePreset(preset: SpeakerTtsVoicePreset) {
        if (preset !in _availableTtsVoicePresets.value) return
        _ttsVoicePreset.value = preset
    }

    fun getStatus(serialNumber: String) {
        send(serialNumber, SpeakerCommand.GetStatus(newMsgId("status")), "状态查询")
    }

    fun speakText(serialNumber: String, text: String, volume: Int = DEFAULT_SPEAKER_VOLUME) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在合成文字语音"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pcm = ttsSynthesizer.synthesizeToPcm8k(trimmed, _ttsVoicePreset.value)
                if (pcm.isEmpty()) error("TTS 合成音频为空")
                val processedPcm = SpeakerVoiceProcessor.applyPlaybackTone(pcm, _toneSettings.value)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在发送文字语音"
                )
                audioRelay.sendRecordedPcm(
                    pcm = processedPcm,
                    outputGain = percentToOutputGain(volume),
                    prebufferPackets = SpeakerAudioConfig.Tts.PREBUFFER_PACKETS,
                    leadingSilenceMs = SpeakerAudioConfig.Tts.LEADING_SILENCE_MS
                ) {
                    incrementPacketCount(SpeakerTalkMode.Tts)
                }
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "文字语音已发送"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "文字语音失败")
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = throwable.message ?: "文字语音失败"
                    )
                }
            }
        }
    }

    fun playToneTest() {
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tone)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                audioRelay.sendTone(outputGain = _outputGain.value)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "蜂鸣指令已发送"
                )
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "蜂鸣测试失败")
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startLiveTalk() {
        if (liveJob?.isActive == true) return
        pttRecordJob?.cancel()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Live)
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                audioRelay.streamMicrophone(
                    outputGain = _outputGain.value,
                    toneSettings = _toneSettings.value
                ) {
                    incrementPacketCount(SpeakerTalkMode.Live)
                }
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "实时喊话失败")
                }
            }
        }
    }

    fun stopLiveTalk() {
        liveJob?.cancel()
        liveJob = null
        if (_talkState.value.mode == SpeakerTalkMode.Live) {
            _talkState.value = _talkState.value.copy(mode = SpeakerTalkMode.Idle)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startPushToTalkRecord() {
        if (pttRecordJob?.isActive == true) return
        stopLiveTalk()
        pttBuffer = ByteArrayOutputStream()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Recording)
        pttRecordJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                audioRelay.captureMicrophoneFrames { frame ->
                    pttBuffer?.write(frame)
                }
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "按住录音失败")
                }
            }
        }
    }

    fun finishPushToTalkRecord() {
        val job = pttRecordJob ?: return
        job.cancel()
        pttRecordJob = null
        viewModelScope.launch(Dispatchers.IO) {
            delay(80)
            val pcm = pttBuffer?.toByteArray() ?: ByteArray(0)
            pttBuffer = null
            if (pcm.isEmpty()) {
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = "录音时间太短")
                return@launch
            }
            _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Sending, recordedPcm = pcm)
            runCatching {
                val processedPcm = SpeakerVoiceProcessor.processPushToTalk(pcm, _toneSettings.value)
                logPttAudioStats(pcm, processedPcm)
                audioRelay.sendRecordedPcm(
                    pcm = processedPcm,
                    outputGain = _outputGain.value,
                    prebufferPackets = SpeakerAudioConfig.Timing.RECORDED_PREBUFFER_PACKETS,
                    leadingSilenceMs = SpeakerAudioConfig.Timing.RECORDED_LEADING_SILENCE_MS
                ) {
                    incrementPacketCount(SpeakerTalkMode.Sending)
                }
                _talkState.value = _talkState.value.copy(mode = SpeakerTalkMode.Idle, recordedPcm = ByteArray(0))
            }.onFailure { throwable ->
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "语音发送失败")
            }
        }
    }

    fun cancelPushToTalkRecord() {
        pttRecordJob?.cancel()
        pttRecordJob = null
        pttBuffer = null
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
    }

    private fun stopLocalAudio() {
        stopLiveTalk()
        cancelPushToTalkRecord()
    }

    private fun logPttAudioStats(raw: ByteArray, processed: ByteArray) {
        val rawStats = SpeakerVoiceProcessor.measurePcm(raw)
        val processedStats = SpeakerVoiceProcessor.measurePcm(processed)
        val ratio = if (rawStats.rms > 0f) processedStats.rms / rawStats.rms else 0f
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "ptt rawRms=${rawStats.rms} rawPeak=${rawStats.peak} " +
                "processedRms=${processedStats.rms} processedPeak=${processedStats.peak} " +
                "rmsRatio=$ratio samples=${processedStats.samples}"
        )
    }

    private fun send(serialNumber: String, command: SpeakerCommand, label: String) {
        pendingCommands[command.msgId] = label
        _feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "${label}已发送"
        )
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
            delay(COMMAND_ACK_TIMEOUT_MS)
            if (pendingCommands.remove(command.msgId) != null) {
                _feedback.value = SpeakerCommandFeedback(
                    msgId = command.msgId,
                    status = SpeakerCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    private fun incrementPacketCount(expectedMode: SpeakerTalkMode) {
        val current = _talkState.value
        if (current.mode == expectedMode) {
            _talkState.value = current.copy(packetsSent = current.packetsSent + 1)
        }
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_feedback.value.msgId == msgId) {
                _feedback.value = SpeakerCommandFeedback()
            }
        }
    }

    private fun newMsgId(prefix: String): String = "speaker-$prefix-${System.currentTimeMillis()}"

    override fun onCleared() {
        stopLocalAudio()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_500L

        fun percentToOutputGain(volume: Int): Float =
            (volume.coerceIn(0, 100) / 100f) * SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN
    }
}

class SpeakerControlViewModelFactory(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeakerControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeakerControlViewModel(stateRepository, controlRepository, audioRelay, ttsSynthesizer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class SpeakerTalkState(
    val mode: SpeakerTalkMode = SpeakerTalkMode.Idle,
    val packetsSent: Int = 0,
    val recordedPcm: ByteArray = ByteArray(0),
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeakerTalkState) return false
        return mode == other.mode &&
            packetsSent == other.packetsSent &&
            recordedPcm.contentEquals(other.recordedPcm) &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + packetsSent
        result = 31 * result + recordedPcm.contentHashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

enum class SpeakerTalkMode {
    Idle,
    Live,
    Recording,
    Sending,
    Tts,
    Tone
}

data class SpeakerCommandFeedback(
    val msgId: String? = null,
    val status: SpeakerCommandFeedbackStatus = SpeakerCommandFeedbackStatus.Idle,
    val text: String? = null
)

enum class SpeakerCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed,
    Timeout
}
