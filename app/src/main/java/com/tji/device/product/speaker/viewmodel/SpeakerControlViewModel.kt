package com.tji.device.product.speaker.viewmodel

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerHadpEncoder
import com.tji.device.product.speaker.audio.SpeakerKokoroTtsSettings
import com.tji.device.product.speaker.audio.SpeakerKokoroVoice
import com.tji.device.product.speaker.audio.SpeakerRecordUploadClient
import com.tji.device.product.speaker.audio.SpeakerRemoteTtsClient
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.audio.SpeakerTtsEngine
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.audio.SpeakerUdpStreamContext
import com.tji.device.product.speaker.audio.SpeakerUdpStreamType
import com.tji.device.product.speaker.audio.SpeakerVoiceProcessor
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecordEvent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

class SpeakerControlViewModel(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer,
    private val remoteTtsClient: SpeakerRemoteTtsClient,
    private val recordUploadClient: SpeakerRecordUploadClient
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

    private val _ttsEngine = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_ENGINE)
    val ttsEngine: StateFlow<SpeakerTtsEngine> = _ttsEngine.asStateFlow()

    private val _kokoroTtsSettings = MutableStateFlow(SpeakerKokoroTtsSettings())
    val kokoroTtsSettings: StateFlow<SpeakerKokoroTtsSettings> = _kokoroTtsSettings.asStateFlow()

    private val pendingCommands = mutableMapOf<String, String>()
    private val ttsPcmCache = object : LinkedHashMap<String, ByteArray>(
        SpeakerAudioConfig.Tts.PCM_CACHE_MAX_ITEMS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > SpeakerAudioConfig.Tts.PCM_CACHE_MAX_ITEMS
    }
    private var liveJob: Job? = null
    private var pttRecordJob: Job? = null
    private var pttBuffer: ByteArrayOutputStream? = null
    private var pttSaveName: String? = null
    private var pendingRecordSave: PendingRecordSave? = null
    private var lastHandledRecordEventKey: String? = null
    private var lastHandledRecordMutationEventKey: String? = null

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
                        val text = if (ack.ok) "${label}已确认" else "${label}失败"
                        _feedback.value = SpeakerCommandFeedback(
                            msgId = ack.msgId,
                            status = if (ack.ok) SpeakerCommandFeedbackStatus.Success else SpeakerCommandFeedbackStatus.Failed,
                            text = text
                        )
                        clearFeedbackAfter(ack.msgId)
                    }
                states.forEach { state ->
                    state.lastRecordEvent?.let { event ->
                        handleRecordSaveEvent(state.serialNumber, event)
                        handleRecordMutationEvent(state.serialNumber, event)
                    }
                }
            }
        }
    }

    fun stop(serialNumber: String) {
        stopLocalAudio()
        send(serialNumber, SpeakerCommand.Stop(newMsgId("stop")), "停止")
    }

    fun setVolume(serialNumber: String, volume: Int) {
        val normalizedVolume = volume.coerceIn(0, 100)
        _outputGain.value = percentToOutputGain(normalizedVolume)
        send(serialNumber, SpeakerCommand.SetVolume(newMsgId("volume"), normalizedVolume), "音量设置")
    }

    fun setOutputGain(outputGain: Float) {
        _outputGain.value = outputGain.coerceIn(0f, SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN)
    }

    fun setToneSettings(toneSettings: SpeakerToneSettings) {
        _toneSettings.value = toneSettings.normalized()
    }

    fun setTtsVoicePreset(preset: SpeakerTtsVoicePreset) {
        if (preset !in _availableTtsVoicePresets.value) return
        _ttsVoicePreset.value = preset
    }

    fun setTtsEngine(engine: SpeakerTtsEngine) {
        _ttsEngine.value = engine
    }

    fun setKokoroVoice(voice: SpeakerKokoroVoice) {
        _kokoroTtsSettings.value = _kokoroTtsSettings.value.copy(voice = voice).normalized()
    }

    fun setKokoroSpeed(speed: Float) {
        _kokoroTtsSettings.value = _kokoroTtsSettings.value.copy(speed = speed).normalized()
    }

    fun getStatus(serialNumber: String) {
        send(serialNumber, SpeakerCommand.GetStatus(newMsgId("status")), "状态查询")
    }

    fun refreshRecords(serialNumber: String, offset: Int = 0, limit: Int = 8) {
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.ListRecords(newMsgId("record-list"), offset = offset, limit = limit),
            label = "录音列表"
        )
    }

    fun refreshStorageStatus(serialNumber: String) {
        send(serialNumber, SpeakerCommand.GetStorageStatus(newMsgId("storage")), "容量查询")
    }

    fun playRecord(serialNumber: String, recordId: String, volumePercent: Int) {
        if (recordId.isBlank()) return
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.PlayRecord(
                msgId = newMsgId("record-play"),
                recordId = recordId,
                volume = volumePercent
            ),
            label = "播放录音"
        )
    }

    fun deleteRecord(serialNumber: String, recordId: String) {
        if (recordId.isBlank()) return
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.DeleteRecord(newMsgId("record-delete"), recordId),
            label = "删除录音"
        )
    }

    fun updateRecordName(serialNumber: String, recordId: String, name: String) {
        val trimmed = name.trim()
        if (recordId.isBlank() || trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Failed,
                text = "录音名称不能为空"
            )
            return
        }
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.UpdateRecord(
                msgId = newMsgId("record-update"),
                recordId = recordId,
                name = trimmed
            ),
            label = "录音改名"
        )
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
                val pcm = getOrSynthesizeTtsPcm(trimmed)
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

    private suspend fun getOrSynthesizeTtsPcm(text: String): ByteArray {
        val engine = _ttsEngine.value
        val cacheKey = buildTtsCacheKey(text, engine)
        synchronized(ttsPcmCache) {
            ttsPcmCache[cacheKey]?.let { return it }
        }
        val pcm = if (engine == SpeakerTtsEngine.KokoroOffline) {
            remoteTtsClient.synthesizeKokoroPcm8k(text, _kokoroTtsSettings.value)
        } else {
            ttsSynthesizer.synthesizeToPcm8k(text, _ttsVoicePreset.value)
        }
        synchronized(ttsPcmCache) {
            ttsPcmCache[cacheKey] = pcm
        }
        return pcm
    }

    private fun buildTtsCacheKey(text: String, engine: SpeakerTtsEngine): String =
        if (engine == SpeakerTtsEngine.KokoroOffline) {
            val settings = _kokoroTtsSettings.value.normalized()
            "${engine.name}|${settings.voice.serverName}|${"%.3f".format(settings.speed)}|$text"
        } else {
            "${engine.name}|${_ttsVoicePreset.value.name}|$text"
        }

    fun playToneTest() {
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = "当前设备暂不支持蜂鸣测试"
        )
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
        pttSaveName = null
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startPushToTalkSaveRecord(defaultName: String? = null) {
        if (pttRecordJob?.isActive == true) return
        stopLiveTalk()
        pttSaveName = defaultName?.trim()?.takeIf { it.isNotBlank() } ?: defaultRecordName()
        pttBuffer = ByteArrayOutputStream()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.RecordingToStore)
        pttRecordJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                audioRelay.captureMicrophoneFrames { frame ->
                    pttBuffer?.write(frame)
                }
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "保存录音失败")
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

    fun finishPushToTalkSaveRecord(serialNumber: String) {
        val job = pttRecordJob ?: return
        job.cancel()
        pttRecordJob = null
        viewModelScope.launch(Dispatchers.IO) {
            delay(80)
            val pcm = pttBuffer?.toByteArray() ?: ByteArray(0)
            val recordName = pttSaveName ?: defaultRecordName()
            pttBuffer = null
            pttSaveName = null
            if (pcm.isEmpty()) {
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = "录音时间太短")
                return@launch
            }
            _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.SavingRecord, recordedPcm = pcm, progress = 0.10f)
            runCatching {
                val startedAt = System.currentTimeMillis()
                val processedPcm = SpeakerVoiceProcessor.processPushToTalk(pcm, _toneSettings.value)
                _talkState.value = _talkState.value.copy(progress = 0.25f)
                val suffix = System.currentTimeMillis()
                val cleanDeviceId = serialNumber.filter { it.isLetterOrDigit() }.ifBlank { "DEVICE" }
                val recordId = "REC_${cleanDeviceId}_$suffix"
                val hadp = SpeakerHadpEncoder.encode(processedPcm, recordId)
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save encoded recordId=$recordId fileSize=${hadp.fileSize} " +
                        "audioBytes=${hadp.audioBytes} frames=${hadp.frameCount} encodeMs=${System.currentTimeMillis() - startedAt}"
                )
                _talkState.value = _talkState.value.copy(progress = 0.40f)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在上传录音"
                )
                val uploadStartedAt = System.currentTimeMillis()
                val upload = recordUploadClient.uploadTempRecord(
                    deviceId = serialNumber,
                    recordId = recordId,
                    name = recordName,
                    hadp = hadp
                )
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save uploaded recordId=${upload.recordId} fileSize=${upload.fileSize} " +
                        "uploadMs=${System.currentTimeMillis() - uploadStartedAt} totalMs=${System.currentTimeMillis() - startedAt}"
                )
                _talkState.value = _talkState.value.copy(progress = 0.80f)
                pendingRecordSave = PendingRecordSave(
                    serialNumber = serialNumber,
                    recordId = upload.recordId,
                    startedAt = startedAt
                )
                send(
                    serialNumber = serialNumber,
                    command = SpeakerCommand.RecordDownload(
                        msgId = newMsgId("record-download"),
                        recordId = upload.recordId,
                        name = recordName,
                        downloadUrl = upload.downloadUrl,
                        fileSize = upload.fileSize,
                        crc32 = upload.crc32,
                        durationMs = upload.durationMs
                    ),
                    label = "保存录音"
                )
                _talkState.value = _talkState.value.copy(progress = 0.92f)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "等待设备保存完成"
                )
                waitForRecordSaveEvent(serialNumber, upload.recordId, startedAt)
            }.onFailure { throwable ->
                pendingRecordSave = null
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = throwable.message ?: "保存录音失败")
            }
        }
    }

    fun cancelPushToTalkRecord() {
        pttRecordJob?.cancel()
        pttRecordJob = null
        pttBuffer = null
        pttSaveName = null
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

    private fun handleRecordSaveEvent(serialNumber: String, event: SpeakerRecordEvent) {
        val pending = pendingRecordSave ?: return
        if (pending.serialNumber != serialNumber || event.recordId != pending.recordId) return
        val eventKey = listOf(event.type, event.recordId, event.code, event.timestamp).joinToString("|")
        if (eventKey == lastHandledRecordEventKey) return
        lastHandledRecordEventKey = eventKey
        val elapsedMs = System.currentTimeMillis() - pending.startedAt
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "record save device event type=${event.type} ok=${event.ok} code=${event.code} " +
                "recordId=${event.recordId} elapsedMs=$elapsedMs msg=${event.message}"
        )
        when (event.type) {
            "record_saved" -> completeRecordSave(serialNumber, pending.recordId)
            "record_failed" -> failRecordSave(event.message.ifBlank { "设备保存录音失败" })
        }
    }

    private fun handleRecordMutationEvent(serialNumber: String, event: SpeakerRecordEvent) {
        if (!event.ok) return
        val shouldRefresh = when (event.type) {
            "record_deleted",
            "record_updated" -> true
            "record_saved" -> pendingRecordSave?.recordId != event.recordId
            else -> false
        }
        if (!shouldRefresh) return
        val eventKey = listOf("mutation", event.type, event.recordId, event.code, event.timestamp).joinToString("|")
        if (eventKey == lastHandledRecordMutationEventKey) return
        lastHandledRecordMutationEventKey = eventKey
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "record mutation refresh type=${event.type} recordId=${event.recordId} sn=$serialNumber"
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshRecords(serialNumber)
            refreshStorageStatus(serialNumber)
        }
    }

    private fun completeRecordSave(serialNumber: String, recordId: String) {
        pendingRecordSave = null
        viewModelScope.launch(Dispatchers.IO) {
            _talkState.value = _talkState.value.copy(progress = 1f)
            _feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Success,
                text = "录音保存完成"
            )
            refreshRecords(serialNumber)
            refreshStorageStatus(serialNumber)
            delay(SAVE_PROGRESS_DONE_VISIBLE_MS)
            if (pendingRecordSave?.recordId == recordId) return@launch
            if (_talkState.value.mode == SpeakerTalkMode.SavingRecord) {
                _talkState.value = _talkState.value.copy(
                    mode = SpeakerTalkMode.Idle,
                    recordedPcm = ByteArray(0),
                    progress = 0f
                )
            }
            clearFeedbackAfter(null)
        }
    }

    private fun failRecordSave(message: String) {
        pendingRecordSave = null
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }

    private fun waitForRecordSaveEvent(serialNumber: String, recordId: String, startedAt: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(RECORD_SAVE_EVENT_TIMEOUT_MS)
            val pending = pendingRecordSave ?: return@launch
            if (pending.serialNumber != serialNumber || pending.recordId != recordId || pending.startedAt != startedAt) {
                return@launch
            }
            Log.w(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "record save timeout recordId=$recordId elapsedMs=${System.currentTimeMillis() - startedAt}"
            )
            pendingRecordSave = null
            refreshRecords(serialNumber)
            refreshStorageStatus(serialNumber)
            _talkState.value = SpeakerTalkState(
                mode = SpeakerTalkMode.Idle,
                error = "等待设备保存反馈超时"
            )
            _feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Timeout,
                text = "等待设备保存反馈超时"
            )
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

    private fun defaultRecordName(): String =
        "录音 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA).format(Date())

    override fun onCleared() {
        stopLocalAudio()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_500L
        const val RECORD_SAVE_EVENT_TIMEOUT_MS = 20_000L
        const val SAVE_PROGRESS_DONE_VISIBLE_MS = 500L

        fun percentToOutputGain(volume: Int): Float =
            (volume.coerceIn(0, 100) / 100f) * SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN
    }
}

private data class PendingRecordSave(
    val serialNumber: String,
    val recordId: String,
    val startedAt: Long
)

class SpeakerControlViewModelFactory(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer,
    private val remoteTtsClient: SpeakerRemoteTtsClient,
    private val recordUploadClient: SpeakerRecordUploadClient
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeakerControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeakerControlViewModel(
                stateRepository,
                controlRepository,
                audioRelay,
                ttsSynthesizer,
                remoteTtsClient,
                recordUploadClient
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class SpeakerTalkState(
    val mode: SpeakerTalkMode = SpeakerTalkMode.Idle,
    val packetsSent: Int = 0,
    val recordedPcm: ByteArray = ByteArray(0),
    val progress: Float = 0f,
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeakerTalkState) return false
        return mode == other.mode &&
            packetsSent == other.packetsSent &&
            recordedPcm.contentEquals(other.recordedPcm) &&
            progress == other.progress &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + packetsSent
        result = 31 * result + recordedPcm.contentHashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

enum class SpeakerTalkMode {
    Idle,
    Live,
    Recording,
    Sending,
    RecordingToStore,
    SavingRecord,
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
