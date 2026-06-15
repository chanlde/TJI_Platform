package com.tji.device.product.speaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class SpeakerLocalAudioPlayer {
    suspend fun playPcm16le(
        pcm: ByteArray,
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE
    ) = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) return@withContext
        require(sampleRate > 0) { "本机播放采样率无效: $sampleRate" }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
            .setBufferSizeInBytes(max(minBuffer, SpeakerAdpcmPacketizer.PCM_FRAME_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) break
                offset += written
            }
            track.stop()
        } finally {
            track.release()
        }
    }
}
