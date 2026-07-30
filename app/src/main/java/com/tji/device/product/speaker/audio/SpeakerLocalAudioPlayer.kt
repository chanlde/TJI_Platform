package com.tji.device.product.speaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class SpeakerLocalAudioPlayer {
    private val activeTrack = AtomicReference<AudioTrack?>()
    private val playbackMutex = Mutex()

    suspend fun playPcm16le(
        pcm: ByteArray,
        sampleRate: Int = SpeakerFeedbackProtocol.SAMPLE_RATE
    ) = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) return@withContext
        require(sampleRate > 0) { "本机播放采样率无效: $sampleRate" }
        playbackMutex.withLock {
            playBlocking(pcm, sampleRate)
        }
    }

    private suspend fun playBlocking(pcm: ByteArray, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "本机不支持 ${sampleRate}Hz 音频播放" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(
                max(
                    minBuffer,
                    SpeakerMicrophoneFormat.frameBytes(sampleRate) * AUDIO_BUFFER_FRAMES
                )
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        activeTrack.set(track)
        try {
            currentCoroutineContext().ensureActive()
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = track.write(pcm, offset, pcm.size - offset)
                check(written > 0) { "本机音频写入失败: code=$written" }
                offset += written
            }
        } finally {
            activeTrack.compareAndSet(track, null)
            stopSafely(track)
            track.release()
        }
    }

    fun stop() {
        activeTrack.get()?.let(::stopSafely)
    }

    private fun stopSafely(track: AudioTrack) {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
    }

    private companion object {
        const val AUDIO_BUFFER_FRAMES = 4
    }
}
