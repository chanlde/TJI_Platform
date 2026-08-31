package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.runCatchingPreservingCancellation
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns TTS, local preview and generated-tone playback as one replace-latest audio operation. */
internal class SpeakerGeneratedAudioCoordinator(
    private val scope: CoroutineScope,
    private val tts: SpeakerTtsCoordinator,
    private val recordTransfer: SpeakerRecordSaveCoordinator,
    private val devices: StateFlow<List<SpeakerDeviceState>>,
    private val outputQuality: StateFlow<SpeakerAudioQuality>,
    private val outputGain: StateFlow<Float>,
    private val talkState: MutableStateFlow<SpeakerTalkState>,
    private val feedback: MutableStateFlow<SpeakerCommandFeedback>,
    private val requireOnline: (String, String) -> Boolean,
    private val clearFeedbackAfter: (String?) -> Unit
) {
    private val latestOperation = LatestAudioOperation()

    fun speakText(serialNumber: String, text: String, volume: Int = DEFAULT_SPEAKER_VOLUME) {
        if (!requireOnline(serialNumber, "发送文字喊话")) return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return failBlankText()
        stop()
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts, progress = 0.10f)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在合成文字语音"
        )
        launchLatest {
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                val quality = outputQuality.value
                val pcm = tts.synthesize(trimmed, quality.sampleRate)
                check(pcm.isNotEmpty()) { "文字语音合成音频为空" }
                val processedPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = pcm,
                    durationMs = SpeakerAudioConfig.Timing.TTS_FILE_LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val timestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("TTS", serialNumber, timestamp)
                val storeTaskId = speakerTransferRouteId("STTS", serialNumber, timestamp)
                val createdAt = isoNow()
                val recordName = "文字喊话 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}"
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = processedPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                recordTransfer.transferAndAwaitResult(
                    SpeakerRecordTransferRequest(
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
                )
            }.onFailure { throwable ->
                failOperation(throwable.toSpeakerUserVisibleMessage("文字语音失败"))
            }
        }
    }

    fun previewOnPhone(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return failBlankText()
        stop()
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tts)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成本机试听"
        )
        launchLatest {
            runCatchingPreservingCancellation {
                feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Pending,
                    text = "正在本机试听"
                )
                tts.preview(trimmed, outputQuality.value.sampleRate)
                talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
                feedback.value = SpeakerCommandFeedback(
                    status = SpeakerCommandFeedbackStatus.Success,
                    text = "本机试听完成"
                )
                clearFeedbackAfter(null)
            }.onFailure { throwable ->
                failOperation(throwable.toSpeakerUserVisibleMessage("本机试听失败"))
            }
        }
    }

    fun playTone(serialNumber: String) {
        if (!requireOnline(serialNumber, "播放蜂鸣")) return
        stop()
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Tone)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在生成蜂鸣"
        )
        launchLatest {
            runCatchingPreservingCancellation {
                val startedAt = System.currentTimeMillis()
                val quality = outputQuality.value
                val tonePcm = SpeakerCoreAudioEngine.generateTonePcm16(
                    frequencyHz = SpeakerAudioConfig.Tone.FREQUENCY_HZ,
                    durationMs = SpeakerAudioConfig.Tone.DURATION_MS,
                    amplitude = SpeakerAudioConfig.Tone.AMPLITUDE * outputGain.value,
                    sampleRate = quality.sampleRate,
                    minDurationMs = quality.packetMs
                )
                val playbackPcm = SpeakerCoreAudioEngine.prependSilencePcm16(
                    pcm16le = tonePcm,
                    durationMs = SpeakerAudioConfig.Tone.LEADING_SILENCE_MS,
                    sampleRate = quality.sampleRate
                )
                val timestamp = System.currentTimeMillis()
                val recordId = speakerTransferRouteId("TONE", serialNumber, timestamp)
                val opusFile = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = playbackPcm,
                    recordId = recordId,
                    sampleRate = quality.sampleRate,
                    packetMs = quality.packetMs
                )
                recordTransfer.transferAndAwaitResult(
                    SpeakerRecordTransferRequest(
                        serialNumber = serialNumber,
                        recordId = recordId,
                        storeTaskId = speakerTransferRouteId("STONE", serialNumber, timestamp),
                        createdAt = isoNow(),
                        recordName = "蜂鸣测试",
                        recordType = "test",
                        opusFile = opusFile,
                        label = "蜂鸣",
                        downloadMsgPrefix = "tone-download",
                        autoPlayVolume = devices.value.firstOrNull { it.serialNumber == serialNumber }
                            ?.volume ?: DEFAULT_SPEAKER_VOLUME,
                        autoPlayLabel = "播放蜂鸣",
                        startedAt = startedAt,
                        temporary = true,
                        visible = false,
                        autoPlayInDownload = true,
                        fallbackPlayAfterSave = false
                    )
                )
                talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle)
            }.onFailure { throwable ->
                failOperation(throwable.toSpeakerUserVisibleMessage("蜂鸣失败"))
            }
        }
    }

    fun stop() {
        latestOperation.cancel()
        tts.stopPreview()
        recordTransfer.clear()
    }

    private fun launchLatest(block: suspend () -> Unit) {
        lateinit var job: Job
        job = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                latestOperation.clearIfCurrent(job)
            }
        }
        latestOperation.replaceWith(job)
        job.start()
    }

    private fun failBlankText() {
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = "请输入喊话文本"
        )
    }

    private fun failOperation(message: String) {
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.CHINA).format(Date())
}
