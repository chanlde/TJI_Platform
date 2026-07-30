package com.tji.device.product.speaker.audio

import android.Manifest
import android.media.audiofx.AcousticEchoCanceler
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.max

data class SpeakerRelayConfig(
    val host: String = SpeakerAudioConfig.Relay.HOST,
    val port: Int = SpeakerAudioConfig.Relay.PORT
)

/**
 * 手机麦克风 PCM 采集入口。
 *
 * 正式喊话在松开按键后编码成 48 kHz Opus 并通过可靠 UDP 直传。采集使用
 * 通信音源并绑定系统 AEC，因此 MCU 麦克风监听可以在按住录音期间继续播放。
 */
class SpeakerAudioRelay {
    private val directPttClient = SpeakerDirectPttClient()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun captureMicrophoneFrames(
        sampleRate: Int,
        onFrame: suspend (ByteArray) -> Unit
    ) {
        require(sampleRate in SpeakerMicrophoneFormat.supportedSampleRates) {
            "不支持的麦克风采样率: $sampleRate"
        }
        val frameBytes = SpeakerMicrophoneFormat.frameBytes(sampleRate)
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "手机不支持 ${sampleRate / 1_000}kHz 单声道 PCM16 录音" }
        val bufferSize = max(minBuffer, frameBytes * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "初始化 ${sampleRate / 1_000}kHz 麦克风失败"
        }
        val aecAvailable = AcousticEchoCanceler.isAvailable()
        val echoCanceler = if (aecAvailable) {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                enabled = true
            }
        } else {
            null
        }
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "phone AEC available=$aecAvailable " +
                "created=${echoCanceler != null} enabled=${echoCanceler?.enabled == true} " +
                "session=${recorder.audioSessionId}"
        )
        val frame = ByteArray(frameBytes)
        var filled = 0
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, filled, frameBytes - filled)
                check(read >= 0) { "麦克风读取失败: $read" }
                if (read == 0) continue
                filled += read - (read % SpeakerMicrophoneFormat.PCM16_BYTES_PER_SAMPLE)
                if (filled >= frameBytes) {
                    onFrame(frame.copyOf())
                    filled = 0
                }
            }
        } finally {
            runCatching { recorder.stop() }
            echoCanceler?.release()
            recorder.release()
        }
    }

    /** @brief 将松手后生成的 48 kHz Opus 可靠直传给 MCU。 */
    suspend fun sendPushToTalk(
        deviceId: String,
        sessionId: String,
        talkId: String,
        opusFile: SpeakerOpusFile,
        volume: Int
    ) {
        directPttClient.send(
            deviceId = deviceId,
            sessionId = sessionId,
            talkId = talkId,
            opusFile = opusFile,
            volume = volume
        )
    }
}
