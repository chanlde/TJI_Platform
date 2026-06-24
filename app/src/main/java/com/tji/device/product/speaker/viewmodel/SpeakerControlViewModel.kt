package com.tji.device.product.speaker.viewmodel

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerHadpEncoder
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerHadpFile
import com.tji.device.product.speaker.audio.SpeakerKokoroTtsSettings
import com.tji.device.product.speaker.audio.SpeakerKokoroVoice
import com.tji.device.product.speaker.audio.SpeakerLocalAudioPlayer
import com.tji.device.product.speaker.audio.SpeakerLocalKokoroTtsClient
import com.tji.device.product.speaker.audio.SpeakerRecordUploadClient
import com.tji.device.product.speaker.audio.SpeakerRecordUploadResult
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.audio.SpeakerTtsEngine
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.audio.SpeakerUdpStreamContext
import com.tji.device.product.speaker.audio.SpeakerUdpStreamType
import com.tji.device.product.speaker.audio.SpeakerVoiceProcessor
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import com.tji.device.product.speaker.core.SpeakerCoreShadowVerifier
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository
import com.tji.device.util.toUserVisibleMessage
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
    private val localKokoroTtsClient: SpeakerLocalKokoroTtsClient,
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

    private val _outputQuality = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY)
    val outputQuality: StateFlow<SpeakerAudioQuality> = _outputQuality.asStateFlow()

    private val _kokoroTtsSettings = MutableStateFlow(SpeakerKokoroTtsSettings())
    val kokoroTtsSettings: StateFlow<SpeakerKokoroTtsSettings> = _kokoroTtsSettings.asStateFlow()

    private val pendingCommands = mutableMapOf<String, String>()
    private val localAudioPlayer = SpeakerLocalAudioPlayer()
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
    private var pendingRecordProgressConfirmJob: Job? = null
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
            }
        }
        viewModelScope.launch {
            stateRepository.recordEvents.collect { envelope ->
                handleRecordSaveEvent(envelope.serialNumber, envelope.event)
                handleRecordMutationEvent(envelope.serialNumber, envelope.event)
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
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "speaker volume commit serialNumber=$serialNumber volumePercent=$normalizedVolume"
        )
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
        _ttsEngine.value = if (engine in SpeakerAudioConfig.Tts.AVAILABLE_ENGINES) {
            engine
        } else {
            SpeakerAudioConfig.Tts.DEFAULT_ENGINE
        }
    }

    fun setOutputQuality(serialNumber: String, quality: SpeakerAudioQuality) {
        _outputQuality.value = quality
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.SetAudioQuality(
                msgId = newMsgId("audio-quality"),
                quality = quality.wireName,
                sampleRate = quality.sampleRate,
                packetMs = quality.packetMs,
                frameBytes = quality.frameBytes,
                samplesPerFrame = quality.samplesPerFrame
            ),
            label = "音质设置"
        )
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

    fun refreshRecords(
        serialNumber: String,
        offset: Int = 0,
        limit: Int = RECORD_LIST_PAGE_SIZE,
        order: String = "desc"
    ) {
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.ListRecords(
                msgId = newMsgId("record-list"),
                offset = offset.coerceAtLeast(0),
                limit = limit.coerceIn(1, RECORD_LIST_PAGE_SIZE),
                order = order.ifBlank { "desc" }
            ),
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
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.SavingRecord, progress = 0.10f)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在合成文字语音"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val startedAt = System.currentTimeMillis()
                val pcm = getOrSynthesizeTtsPcm(trimmed)
                if (pcm.isEmpty()) error("文字语音合成音频为空")
                val quality = _outputQuality.value
                val processedPcm = SpeakerCoreAudioEngine
                    .applyPlaybackTone(pcm, _toneSettings.value, quality.sampleRate)
                    .let {
                        SpeakerCoreAudioEngine.resamplePcm16(
                            pcm16le = it,
                            sourceSampleRate = quality.sampleRate,
                            targetSampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
                        )
                    }
                    .let {
                        SpeakerCoreAudioEngine.prependSilencePcm16(
                            pcm16le = it,
                            durationMs = SpeakerAudioConfig.Voice.TTS_FILE_LEADING_SILENCE_MS,
                            sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
                        )
                    }
                val suffix = System.currentTimeMillis()
                val cleanDeviceId = serialNumber.filter { it.isLetterOrDigit() }.ifBlank { "DEVICE" }
                val recordId = "TTS_PLAY_${cleanDeviceId}_$suffix"
                val storeTaskId = "STORE_TTS_PLAY_${cleanDeviceId}_$suffix"
                val createdAt = isoNow()
                val recordName = "文字喊话 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"
                val hadp = SpeakerCoreAudioEngine.encodeHadp(
                    pcm = processedPcm,
                    recordId = recordId,
                    codec = SpeakerHadpCodec.ImaAdpcm,
                    sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE,
                    packetMs = SpeakerAdpcmPacketizer.PACKET_MS
                )
                logHadpShadowResult(
                    label = "tts-temp-file",
                    hadp = hadp,
                    pcm16le = processedPcm,
                    recordId = recordId
                )
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "tts temp file encoded recordId=$recordId engine=${_ttsEngine.value.name} " +
                        "fileSize=${hadp.fileSize} codec=${hadp.codec.wireName} uploadQuality=${quality.name} " +
                        "deviceOutputQuality=${_outputQuality.value.name} sampleRate=${hadp.sampleRate} " +
                        "audioBytes=${hadp.audioBytes} frames=${hadp.frameCount} encodeMs=${System.currentTimeMillis() - startedAt}"
                )
                uploadHadpAndRequestPlayback(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = createdAt,
                    recordName = recordName,
                    hadp = hadp,
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
                pendingRecordSave = null
                if (throwable !is CancellationException) {
                    val message = throwable.toUserVisibleMessage("文字语音失败")
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = message
                    )
                }
            }
        }
    }

    fun previewTtsOnPhone(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成本机试听"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pcm = getOrSynthesizeTtsPcm(trimmed)
                if (pcm.isEmpty()) error("文字语音合成音频为空")
                val quality = _outputQuality.value
                val processedPcm = SpeakerCoreAudioEngine.applyPlaybackTone(pcm, _toneSettings.value, quality.sampleRate)
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "tts phone preview engine=${_ttsEngine.value.name} bytes=${processedPcm.size} " +
                        "uploadQuality=${quality.name} deviceOutputQuality=${_outputQuality.value.name} sampleRate=${quality.sampleRate}"
                )
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在本机试听"
                )
                localAudioPlayer.playPcm16le(processedPcm, quality.sampleRate)
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "本机试听完成"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    val message = throwable.toUserVisibleMessage("本机试听失败")
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = message
                    )
                }
            }
        }
    }

    fun previewLocalKokoroOriginalOnPhone(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        if (_ttsEngine.value != SpeakerTtsEngine.LocalKokoro) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请先选择本地语音")
            return
        }
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成本地原声"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val localAudio = localKokoroTtsClient.synthesizePcm16Mono(
                    text = trimmed,
                    settings = _kokoroTtsSettings.value,
                    targetSampleRate = null
                )
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "local kokoro original preview bytes=${localAudio.pcm16.size} " +
                        "sampleRate=${localAudio.sampleRate} sourceRate=${localAudio.sourceSampleRate}"
                )
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在播放本地原声 ${localAudio.sampleRate}Hz"
                )
                localAudioPlayer.playPcm16le(localAudio.pcm16, localAudio.sampleRate)
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "本地原声试听完成"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    val message = throwable.toUserVisibleMessage("本地原声试听失败")
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = message
                    )
                }
            }
        }
    }

    private suspend fun getOrSynthesizeTtsPcm(text: String): ByteArray {
        val engine = _ttsEngine.value.takeIf { it in SpeakerAudioConfig.Tts.AVAILABLE_ENGINES }
            ?: SpeakerAudioConfig.Tts.DEFAULT_ENGINE.also { _ttsEngine.value = it }
        val quality = _outputQuality.value
        val cacheKey = buildTtsCacheKey(text, engine)
        synchronized(ttsPcmCache) {
            ttsPcmCache[cacheKey]?.let { return it }
        }
        val pcm = when (engine) {
            SpeakerTtsEngine.LocalKokoro -> localKokoroTtsClient
                .synthesizePcm16Mono(text, _kokoroTtsSettings.value, quality.sampleRate)
                .pcm16
            SpeakerTtsEngine.System -> ttsSynthesizer.synthesizeToPcm(
                text = text,
                voicePreset = _ttsVoicePreset.value,
                targetSampleRate = quality.sampleRate
            )
        }
        synchronized(ttsPcmCache) {
            ttsPcmCache[cacheKey] = pcm
        }
        return pcm
    }

    private fun buildTtsCacheKey(text: String, engine: SpeakerTtsEngine): String =
        if (engine == SpeakerTtsEngine.LocalKokoro) {
            val settings = _kokoroTtsSettings.value.normalized()
            "${engine.name}|${settings.voice.serverName}|${"%.3f".format(settings.speed)}|${_outputQuality.value.sampleRate}|$text"
        } else {
            "${engine.name}|${_ttsVoicePreset.value.name}|${_outputQuality.value.sampleRate}|$text"
        }

    fun playLocalKokoroTtsFileTest(serialNumber: String, text: String, volumePercent: Int) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _feedback.value = SpeakerCommandFeedback(status = SpeakerCommandFeedbackStatus.Failed, text = "请输入喊话文本")
            return
        }
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.SavingRecord, progress = 0.10f)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成本地语音"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val startedAt = System.currentTimeMillis()
                val suffix = System.currentTimeMillis()
                val cleanDeviceId = serialNumber.filter { it.isLetterOrDigit() }.ifBlank { "DEVICE" }
                val recordId = "TTS_LOCAL_${cleanDeviceId}_$suffix"
                val storeTaskId = "STORE_TTS_LOCAL_${cleanDeviceId}_$suffix"
                val createdAt = isoNow()
                val recordName = "本地语音 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"
                val quality = _outputQuality.value
                val localAudio = localKokoroTtsClient.synthesizePcm16Mono(trimmed, _kokoroTtsSettings.value, quality.sampleRate)
                val hadp = SpeakerCoreAudioEngine.encodeHadp(
                    pcm = localAudio.pcm16,
                    recordId = recordId,
                    sampleRate = localAudio.sampleRate,
                    packetMs = quality.packetMs
                )
                logHadpShadowResult(
                    label = "local-kokoro-tts-file",
                    hadp = hadp,
                    pcm16le = localAudio.pcm16,
                    recordId = recordId
                )
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "local kokoro tts file encoded recordId=$recordId sourceRate=${localAudio.sourceSampleRate} " +
                        "sampleRate=${hadp.sampleRate} fileSize=${hadp.fileSize} audioBytes=${hadp.audioBytes} " +
                        "frames=${hadp.frameCount} encodeMs=${System.currentTimeMillis() - startedAt}"
                )
                uploadHadpAndRequestPlayback(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = createdAt,
                    recordName = recordName,
                    hadp = hadp,
                    label = "本地语音文件",
                    downloadMsgPrefix = "local-tts-download",
                    autoPlayVolume = volumePercent.coerceIn(0, 100),
                    autoPlayLabel = "播放本地语音",
                    startedAt = startedAt,
                    temporary = true,
                    visible = false,
                    autoPlayInDownload = true
                )
            }.onFailure { throwable ->
                pendingRecordSave = null
                val message = throwable.toUserVisibleMessage("本地语音失败")
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Failed,
                    text = message
                )
            }
        }
    }

    fun playToneTest(serialNumber: String) {
        stopLocalAudio()
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tone)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在发送蜂鸣"
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val tonePcm = SpeakerCoreAudioEngine.generateTonePcm16(
                    frequencyHz = SpeakerAudioConfig.Tone.FREQUENCY_HZ,
                    durationMs = SpeakerAudioConfig.Tone.DURATION_MS,
                    amplitude = SpeakerAudioConfig.Tone.AMPLITUDE * _outputGain.value
                )
                audioRelay.sendRecordedPcm(
                    pcm = tonePcm,
                    outputGain = _outputGain.value,
                    prebufferPackets = SpeakerAudioConfig.Tone.PREBUFFER_PACKETS,
                    leadingSilenceMs = SpeakerAudioConfig.Tone.LEADING_SILENCE_MS,
                    streamContext = playbackStreamContext(serialNumber, "tone")
                ) {
                    incrementPacketCount(SpeakerTalkMode.Tone)
                }
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "蜂鸣已发送"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                val message = throwable.toUserVisibleMessage("蜂鸣失败")
                _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Failed,
                    text = message
                )
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
                    _talkState.value = SpeakerTalkState(
                        mode = SpeakerTalkMode.Idle,
                        error = throwable.toUserVisibleMessage("实时喊话失败")
                    )
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
                    _talkState.value = SpeakerTalkState(
                        mode = SpeakerTalkMode.Idle,
                        error = throwable.toUserVisibleMessage("按住录音失败")
                    )
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
                    _talkState.value = SpeakerTalkState(
                        mode = SpeakerTalkMode.Idle,
                        error = throwable.toUserVisibleMessage("保存录音失败")
                    )
                }
            }
        }
    }

    fun finishPushToTalkRecord(serialNumber: String) {
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
                val startedAt = System.currentTimeMillis()
                if (!SpeakerVoiceProcessor.hasPushToTalkSpeech(pcm)) {
                    _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Failed,
                        text = "未检测到语音"
                    )
                    clearFeedbackAfter(null)
                    return@runCatching
                }
                val processedPcm = SpeakerVoiceProcessor.processPushToTalk(pcm, _toneSettings.value)
                logPttAudioStats(pcm, processedPcm)
                val playbackPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = processedPcm,
                    durationMs = SpeakerAudioConfig.Timing.RECORDED_LEADING_SILENCE_MS,
                    sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
                )
                val suffix = System.currentTimeMillis()
                val cleanDeviceId = serialNumber.filter { it.isLetterOrDigit() }.ifBlank { "DEVICE" }
                val recordId = "PTT_PLAY_${cleanDeviceId}_$suffix"
                val storeTaskId = "STORE_PTT_PLAY_${cleanDeviceId}_$suffix"
                val createdAt = isoNow()
                val recordName = "按住喊话 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"
                val hadp = SpeakerCoreAudioEngine.encodeHadp(
                    pcm = playbackPcm,
                    recordId = recordId,
                    codec = SpeakerHadpCodec.ImaAdpcm,
                    sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE,
                    packetMs = SpeakerAdpcmPacketizer.PACKET_MS
                )
                logHadpShadowResult(
                    label = "ptt-temp-file",
                    hadp = hadp,
                    pcm16le = playbackPcm,
                    recordId = recordId
                )
                uploadHadpAndRequestPlayback(
                    serialNumber = serialNumber,
                    recordId = recordId,
                    storeTaskId = storeTaskId,
                    createdAt = createdAt,
                    recordName = recordName,
                    hadp = hadp,
                    label = "按住喊话",
                    downloadMsgPrefix = "ptt-download",
                    autoPlayVolume = devices.value.firstOrNull { it.serialNumber == serialNumber }?.volume
                        ?: DEFAULT_SPEAKER_VOLUME,
                    autoPlayLabel = "播放按住喊话",
                    startedAt = startedAt,
                    temporary = true,
                    visible = false,
                    autoPlayInDownload = true,
                    fallbackPlayAfterSave = false
                )
                _talkState.value = _talkState.value.copy(mode = SpeakerTalkMode.Idle, recordedPcm = ByteArray(0))
            }.onFailure { throwable ->
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toUserVisibleMessage("语音发送失败")
                )
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
                val quality = _outputQuality.value
                val pttPcm = SpeakerCoreAudioEngine.processPushToTalk(pcm, _toneSettings.value)
                val processedPcm = SpeakerCoreAudioEngine.resamplePcm16(
                    pcm16le = pttPcm,
                    sourceSampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE,
                    targetSampleRate = quality.sampleRate
                )
                _talkState.value = _talkState.value.copy(progress = 0.25f)
                val suffix = System.currentTimeMillis()
                val cleanDeviceId = serialNumber.filter { it.isLetterOrDigit() }.ifBlank { "DEVICE" }
                val recordId = "REC_${cleanDeviceId}_$suffix"
                val storeTaskId = "STORE_${cleanDeviceId}_$suffix"
                val createdAt = isoNow()
                val hadp = SpeakerCoreAudioEngine.encodeHadp(
                    pcm = processedPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                logHadpShadowResult(
                    label = "record-save",
                    hadp = hadp,
                    pcm16le = processedPcm,
                    recordId = recordId
                )
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save encoded recordId=$recordId fileSize=${hadp.fileSize} " +
                        "quality=${quality.name} sampleRate=${hadp.sampleRate} audioBytes=${hadp.audioBytes} " +
                        "frames=${hadp.frameCount} encodeMs=${System.currentTimeMillis() - startedAt}"
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
                val downloadMsgId = newMsgId("record-download")
                pendingRecordSave = PendingRecordSave(
                    serialNumber = serialNumber,
                    recordId = upload.recordId,
                    commandMsgId = downloadMsgId,
                    record = upload.toSpeakerRecord(recordName, createdAt),
                    startedAt = startedAt
                )
                send(
                    serialNumber = serialNumber,
                    command = SpeakerCommand.RecordDownload(
                        msgId = downloadMsgId,
                        recordId = upload.recordId,
                        storeTaskId = storeTaskId,
                        createdAt = createdAt,
                        name = recordName,
                        downloadUrl = upload.downloadUrl,
                        fileSize = upload.fileSize,
                        crc32 = upload.crc32,
                        durationMs = upload.durationMs,
                        codec = upload.codec,
                        sampleRate = upload.sampleRate,
                        channels = upload.channels,
                        packetMs = upload.packetMs,
                        frameBytes = upload.frameBytes,
                        samplesPerFrame = upload.samplesPerFrame
                    ),
                    label = "保存录音",
                    awaitAck = false
                )
                _talkState.value = _talkState.value.copy(progress = 0.92f)
                _feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "等待设备保存完成"
                )
                waitForRecordSaveEvent(serialNumber, upload.recordId, startedAt)
            }.onFailure { throwable ->
                pendingRecordSave = null
                _talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = throwable.toUserVisibleMessage("保存录音失败")
                )
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

    private fun playbackStreamContext(serialNumber: String, prefix: String): SpeakerUdpStreamContext {
        val id = "PLAY_${prefix.uppercase(Locale.US)}_${System.currentTimeMillis()}"
        return SpeakerUdpStreamContext(
            deviceId = serialNumber,
            taskId = id,
            type = SpeakerUdpStreamType.Playback,
            talkId = id
        )
    }

    private suspend fun uploadHadpAndRequestPlayback(
        serialNumber: String,
        recordId: String,
        storeTaskId: String,
        createdAt: String,
        recordName: String,
        hadp: SpeakerHadpFile,
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
        _talkState.value = _talkState.value.copy(progress = 0.40f)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在上传$label"
        )
        val upload = recordUploadClient.uploadTempRecord(
            deviceId = serialNumber,
            recordId = recordId,
            name = recordName,
            hadp = hadp
        )
        _talkState.value = _talkState.value.copy(progress = 0.80f)
        val downloadMsgId = newMsgId(downloadMsgPrefix)
        pendingRecordSave = PendingRecordSave(
            serialNumber = serialNumber,
            recordId = upload.recordId,
            commandMsgId = downloadMsgId,
            record = upload.toSpeakerRecord(recordName, createdAt),
            startedAt = startedAt,
            autoPlayVolume = if (fallbackPlayAfterSave) autoPlayVolume?.coerceIn(0, 100) else null,
            autoPlayLabel = autoPlayLabel,
            waitForPlayback = autoPlayInDownload,
            cacheRecord = !temporary,
            timeoutMs = if (autoPlayInDownload) {
                RECORD_SAVE_EVENT_TIMEOUT_MS + upload.durationMs.toLong() + RECORD_PLAYBACK_FEEDBACK_MARGIN_MS
            } else {
                RECORD_SAVE_EVENT_TIMEOUT_MS
            }
        )
        send(
            serialNumber = serialNumber,
            command = SpeakerCommand.RecordDownload(
                msgId = downloadMsgId,
                recordId = upload.recordId,
                storeTaskId = storeTaskId,
                createdAt = createdAt,
                name = recordName,
                downloadUrl = upload.downloadUrl,
                fileSize = upload.fileSize,
                crc32 = upload.crc32,
                durationMs = upload.durationMs,
                codec = upload.codec,
                sampleRate = upload.sampleRate,
                channels = upload.channels,
                packetMs = upload.packetMs,
                frameBytes = upload.frameBytes,
                samplesPerFrame = upload.samplesPerFrame,
                temporary = temporary,
                visible = visible,
                autoPlay = autoPlayInDownload,
                playbackVolume = autoPlayVolume
            ),
            label = label,
            awaitAck = false
        )
        _talkState.value = _talkState.value.copy(progress = 0.92f)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "等待设备保存$label"
        )
        waitForRecordSaveEvent(serialNumber, upload.recordId, startedAt)
    }

    private fun send(
        serialNumber: String,
        command: SpeakerCommand,
        label: String,
        awaitAck: Boolean = true
    ) {
        if (awaitAck) {
            pendingCommands[command.msgId] = label
        }
        _feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "${label}已发送"
        )
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
            if (!awaitAck) return@launch
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
            "record_playback" -> {
                if (event.ok) {
                    completeRecordSave(serialNumber, pending.recordId, successText = "临时音频播放完成")
                } else {
                    failRecordSave(event.message.ifBlank { "临时音频播放失败" })
                }
            }
            "record_saved" -> {
                if (pending.waitForPlayback) {
                    _talkState.value = _talkState.value.copy(progress = 0.98f)
                    _feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Pending,
                        text = "设备已下载，正在播放"
                    )
                } else {
                    completeRecordSave(serialNumber, pending.recordId)
                }
            }
            "record_failed" -> failRecordSave(event.message.ifBlank { "设备保存录音失败" })
            "record_progress" -> {
                _talkState.value = _talkState.value.copy(progress = (event.progress / 100f).coerceIn(0.92f, 0.98f))
                if (event.progress >= 100 && !pending.waitForPlayback) {
                    confirmRecordSaveFromListAfterProgress(serialNumber, pending)
                }
            }
        }
    }

    private fun handleRecordMutationEvent(serialNumber: String, event: SpeakerRecordEvent) {
        val deleteAlreadyGone = event.type == "record_deleted" &&
            !event.recordId.isNullOrBlank() &&
            (event.code == 404 || event.message.contains("not found", ignoreCase = true))
        if (!event.ok && !deleteAlreadyGone) return
        val shouldRefresh = when (event.type) {
            "record_deleted" -> true
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

    private fun completeRecordSave(serialNumber: String, recordId: String, successText: String? = null) {
        val completed = pendingRecordSave
        pendingRecordProgressConfirmJob?.cancel()
        pendingRecordProgressConfirmJob = null
        completed?.commandMsgId?.let { pendingCommands.remove(it) }
        pendingRecordSave = null
        viewModelScope.launch(Dispatchers.IO) {
            _talkState.value = _talkState.value.copy(progress = 1f)
            _feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Success,
                text = successText ?: if (completed?.autoPlayVolume != null) "文件保存完成，正在播放" else "录音保存完成"
            )
            if (completed?.cacheRecord == true) {
                stateRepository.upsertRecord(serialNumber, completed.record)
            }
            refreshRecords(serialNumber)
            refreshStorageStatus(serialNumber)
            completed?.autoPlayVolume?.let { volume ->
                send(
                    serialNumber = serialNumber,
                    command = SpeakerCommand.PlayRecord(
                        msgId = newMsgId("tone-file-play"),
                        recordId = recordId,
                        volume = volume
                    ),
                    label = completed.autoPlayLabel
                )
            }
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
        pendingRecordProgressConfirmJob?.cancel()
        pendingRecordProgressConfirmJob = null
        pendingRecordSave?.commandMsgId?.let { pendingCommands.remove(it) }
        pendingRecordSave = null
        _talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }

    private fun waitForRecordSaveEvent(serialNumber: String, recordId: String, startedAt: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(pendingRecordSave?.timeoutMs ?: RECORD_SAVE_EVENT_TIMEOUT_MS)
            val pending = pendingRecordSave ?: return@launch
            if (pending.serialNumber != serialNumber || pending.recordId != recordId || pending.startedAt != startedAt) {
                return@launch
            }
            if (!pending.waitForPlayback && confirmRecordSaveFromPagedList(serialNumber, recordId)) {
                completeRecordSave(serialNumber, recordId)
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

    private fun confirmRecordSaveFromListAfterProgress(serialNumber: String, pending: PendingRecordSave) {
        pendingRecordProgressConfirmJob?.cancel()
        pendingRecordProgressConfirmJob = viewModelScope.launch(Dispatchers.IO) {
            delay(RECORD_PROGRESS_DONE_CONFIRM_DELAY_MS)
            val current = pendingRecordSave ?: return@launch
            if (
                current.serialNumber != pending.serialNumber ||
                current.recordId != pending.recordId ||
                current.startedAt != pending.startedAt
            ) {
                return@launch
            }
            if (confirmRecordSaveFromPagedList(serialNumber, pending.recordId)) {
                completeRecordSave(serialNumber, pending.recordId)
            }
        }
    }

    private suspend fun confirmRecordSaveFromPagedList(serialNumber: String, recordId: String): Boolean {
        var offset = 0
        repeat(RECORD_CONFIRM_MAX_PAGES) {
            controlRepository.sendCommand(
                serialNumber,
                SpeakerCommand.ListRecords(
                    msgId = newMsgId("record-confirm-list"),
                    offset = offset,
                    limit = RECORD_LIST_PAGE_SIZE,
                    order = "desc"
                )
            )
            delay(RECORD_CONFIRM_PAGE_WAIT_MS)
            val state = devices.value.firstOrNull { it.serialNumber == serialNumber }
            if (state?.records.orEmpty().any { it.recordId == recordId }) {
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save confirmed by list recordId=$recordId offset=$offset"
                )
                return true
            }
            val total = state?.recordTotal?.takeIf { it > 0 } ?: RECORD_CONFIRM_MAX_RECORDS
            offset += RECORD_LIST_PAGE_SIZE
            if (offset >= minOf(total, RECORD_CONFIRM_MAX_RECORDS)) return false
        }
        return false
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

    private fun logHadpShadowResult(
        label: String,
        hadp: SpeakerHadpFile,
        pcm16le: ByteArray,
        recordId: String
    ) {
        val kotlinHadp = runCatching {
            SpeakerHadpEncoder.encode(
                pcm = pcm16le,
                recordId = recordId,
                codec = hadp.codec,
                sampleRate = hadp.sampleRate,
                channels = hadp.channels,
                packetMs = hadp.packetMs
            )
        }.getOrNull()
        if (kotlinHadp == null) {
            Log.d(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "speakerCoreShadow status=kotlinUnavailable path=$label recordId=$recordId"
            )
            return
        }
        val result = SpeakerCoreShadowVerifier.compareHadp(
            kotlinHadp = kotlinHadp,
            pcm16le = pcm16le,
            recordId = recordId
        )
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "${result.toLogLine()} path=$label recordId=$recordId " +
                "codec=${hadp.codec.wireName} sampleRate=${hadp.sampleRate} packetMs=${hadp.packetMs} " +
                "frames=${hadp.frameCount} fileSize=${hadp.fileSize}"
        )
    }

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
        const val RECORD_PLAYBACK_FEEDBACK_MARGIN_MS = 5_000L
        const val RECORD_LIST_PAGE_SIZE = 4
        const val RECORD_PROGRESS_DONE_CONFIRM_DELAY_MS = 1_500L
        const val RECORD_CONFIRM_PAGE_WAIT_MS = 700L
        const val RECORD_CONFIRM_MAX_RECORDS = 32
        const val RECORD_CONFIRM_MAX_PAGES = RECORD_CONFIRM_MAX_RECORDS / RECORD_LIST_PAGE_SIZE
        fun percentToOutputGain(volume: Int): Float =
            (volume.coerceIn(0, 100) / 100f) * SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN
    }
}

private data class PendingRecordSave(
    val serialNumber: String,
    val recordId: String,
    val commandMsgId: String,
    val record: SpeakerRecord,
    val startedAt: Long,
    val autoPlayVolume: Int? = null,
    val waitForPlayback: Boolean = false,
    val cacheRecord: Boolean = true,
    val timeoutMs: Long = 20_000L,
    val autoPlayLabel: String = "播放文件"
)

private fun SpeakerRecordUploadResult.toSpeakerRecord(name: String, createdAt: String): SpeakerRecord =
    SpeakerRecord(
        recordId = recordId,
        name = name,
        fileSize = fileSize,
        durationMs = durationMs.toLong(),
        codec = codec,
        sampleRate = sampleRate,
        channels = channels,
        packetMs = packetMs,
        crc32 = crc32,
        createdAt = createdAt
    )

class SpeakerControlViewModelFactory(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer,
    private val localKokoroTtsClient: SpeakerLocalKokoroTtsClient,
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
                localKokoroTtsClient,
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
