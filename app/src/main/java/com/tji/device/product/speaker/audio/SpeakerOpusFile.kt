package com.tji.device.product.speaker.audio

import java.util.Locale
import java.util.zip.CRC32

/**
 * App 上传给喊话器的标准 Ogg Opus 文件及其传输元数据。
 *
 * 音频压缩只由 native speaker-core/libopus 完成；Kotlin 不保留第二套编码器。
 */
data class SpeakerOpusFile(
    val data: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val packetMs: Int,
    val bitrate: Int,
    val fileSize: Int,
    val crc32: String,
    val durationMs: Int,
    val packetCount: Int
) {
    val codec: String = CODEC
    val container: String = CONTAINER

    companion object {
        const val CODEC = "opus"
        const val CONTAINER = "ogg"
        const val DEFAULT_PACKET_MS = 20
        const val DEFAULT_BITRATE = 24_000

        fun fromEncoded(
            data: ByteArray,
            pcmBytes: Int,
            sampleRate: Int,
            channels: Int,
            packetMs: Int,
            bitrate: Int
        ): SpeakerOpusFile {
            require(
                data.size >= 27 &&
                    data.copyOfRange(0, 4).contentEquals("OggS".encodeToByteArray())
            ) {
                "speaker-core 未返回有效 Ogg 文件"
            }
            val inputSamples = pcmBytes / (Short.SIZE_BYTES * channels)
            val samplesPerPacket = sampleRate * packetMs / 1_000
            return SpeakerOpusFile(
                data = data,
                sampleRate = sampleRate,
                channels = channels,
                packetMs = packetMs,
                bitrate = bitrate,
                fileSize = data.size,
                crc32 = "0x%08X".format(
                    Locale.US,
                    CRC32().apply { update(data) }.value
                ),
                durationMs = inputSamples * 1_000 / sampleRate,
                packetCount = (inputSamples + samplesPerPacket - 1) / samplesPerPacket
            )
        }
    }
}
