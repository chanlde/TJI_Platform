package com.tji.device.product.speaker.audio

import com.tji.device.product.speaker.core.SpeakerCoreNative
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object SpeakerHadpDecoder {
    fun decodePcm16le(hadp: SpeakerHadpFile): ByteArray {
        SpeakerCoreNative.decodeHadpPcm16OrNull(hadp.data)?.let { return it }
        require(hadp.data.size >= HADP_HEADER_BYTES) { "HADP 文件过短" }
        val header = ByteBuffer.wrap(hadp.data, 0, HADP_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(HADP_MAGIC_BYTES)
        header.get(magic)
        require(magic.contentEquals(HADP_MAGIC)) { "HADP 魔术头错误" }
        val version = header.short.toInt() and 0xFFFF
        val headerBytes = header.short.toInt() and 0xFFFF
        val codecId = header.short.toInt() and 0xFFFF
        header.short
        val sampleRate = header.int
        val channels = header.short.toInt() and 0xFFFF
        val packetMs = header.short.toInt() and 0xFFFF
        val frameBytes = header.short.toInt() and 0xFFFF
        val samplesPerFrame = header.short.toInt() and 0xFFFF
        val frameCount = header.int
        val audioBytes = header.int

        require(version == HADP_VERSION) { "HADP 版本不支持: $version" }
        require(headerBytes == HADP_HEADER_BYTES) { "HADP 头长度不支持: $headerBytes" }
        require(sampleRate == SpeakerAdpcmPacketizer.SAMPLE_RATE) { "采样率不支持: $sampleRate" }
        require(channels == SpeakerAdpcmPacketizer.CHANNELS) { "声道数不支持: $channels" }
        require(packetMs == SpeakerAdpcmPacketizer.PACKET_MS) { "包时长不支持: $packetMs" }
        require(samplesPerFrame == SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / BYTES_PER_PCM16_SAMPLE) {
            "帧采样数不支持: $samplesPerFrame"
        }
        require(audioBytes >= 0 && HADP_HEADER_BYTES + audioBytes <= hadp.data.size) { "HADP 音频长度错误" }

        return when (codecId) {
            SpeakerHadpCodec.Pcm16.id -> {
                require(frameBytes == SpeakerHadpCodec.Pcm16.frameBytes) { "PCM16 帧长度错误: $frameBytes" }
                hadp.data.copyOfRange(HADP_HEADER_BYTES, HADP_HEADER_BYTES + audioBytes)
            }
            SpeakerHadpCodec.ImaAdpcm.id -> {
                require(frameBytes == SpeakerHadpCodec.ImaAdpcm.frameBytes) { "ADPCM 帧长度错误: $frameBytes" }
                decodeAdpcmFrames(hadp.data, frameCount, samplesPerFrame)
            }
            else -> error("HADP 编码不支持: $codecId")
        }
    }

    private fun decodeAdpcmFrames(data: ByteArray, frameCount: Int, samplesPerFrame: Int): ByteArray {
        val frames = ArrayList<ByteArray>()
        var offset = HADP_HEADER_BYTES
        repeat(frameCount) {
            require(offset + SpeakerHadpCodec.ImaAdpcm.frameBytes <= data.size) { "ADPCM 帧数据不完整" }
            frames += SpeakerAdpcmDecoder.decodeBlock(data, offset, samplesPerFrame)
            offset += SpeakerHadpCodec.ImaAdpcm.frameBytes
        }
        return frames.flattenToByteArray()
    }

    private fun List<ByteArray>.flattenToByteArray(): ByteArray {
        val totalBytes = sumOf { it.size }
        val pcm = ByteArray(totalBytes)
        var writeOffset = 0
        forEach { frame ->
            frame.copyInto(pcm, destinationOffset = writeOffset)
            writeOffset += frame.size
        }
        return pcm
    }

    private const val HADP_MAGIC_BYTES = 4
    private const val HADP_HEADER_BYTES = 128
    private const val HADP_VERSION = 1
    private const val BYTES_PER_PCM16_SAMPLE = 2
    private val HADP_MAGIC = byteArrayOf('H'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte())
}

object SpeakerAdpcmDecoder {
    fun decodeHadp(hadp: SpeakerHadpFile): ByteArray {
        require(hadp.codec == SpeakerHadpCodec.ImaAdpcm) { "当前 HADP 不是 ADPCM: ${hadp.codec.wireName}" }
        return SpeakerHadpDecoder.decodePcm16le(hadp)
    }

    fun decodeBlock(block: ByteArray, offset: Int = 0, expectedSamples: Int): ByteArray {
        require(offset + ADPCM_HEADER_BYTES <= block.size) { "ADPCM block 过短" }
        var predictor = readLeI16(block, offset)
        var index = block[offset + 2].toInt() and 0xFF
        index = index.coerceIn(0, 88)
        val samples = IntArray(expectedSamples)
        samples[0] = predictor
        var sampleIndex = 1
        var payloadOffset = offset + ADPCM_HEADER_BYTES
        val payloadEnd = min(block.size, offset + ADPCM_FRAME_BYTES)
        while (payloadOffset < payloadEnd && sampleIndex < expectedSamples) {
            val packed = block[payloadOffset].toInt() and 0xFF
            val lowNibble = packed and 0x0F
            predictor = decodeNibble(lowNibble, predictor, index).also { decoded ->
                index = (index + IMA_INDEX_TABLE[lowNibble]).coerceIn(0, 88)
                samples[sampleIndex++] = decoded
            }
            if (sampleIndex >= expectedSamples) break
            val highNibble = (packed ushr 4) and 0x0F
            predictor = decodeNibble(highNibble, predictor, index).also { decoded ->
                index = (index + IMA_INDEX_TABLE[highNibble]).coerceIn(0, 88)
                samples[sampleIndex++] = decoded
            }
            payloadOffset += 1
        }
        return samples.toPcm16le()
    }

    private fun decodeNibble(nibble: Int, predictor: Int, index: Int): Int {
        val step = IMA_STEP_TABLE[index]
        var delta = step shr 3
        if ((nibble and 4) != 0) delta += step
        if ((nibble and 2) != 0) delta += step shr 1
        if ((nibble and 1) != 0) delta += step shr 2
        return if ((nibble and 8) != 0) {
            predictor - delta
        } else {
            predictor + delta
        }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    private fun readLeI16(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)
        return value.toShort().toInt()
    }

    private fun IntArray.toPcm16le(): ByteArray {
        val pcm = ByteArray(size * BYTES_PER_PCM16_SAMPLE)
        forEachIndexed { index, value ->
            val offset = index * BYTES_PER_PCM16_SAMPLE
            pcm[offset] = (value and 0xFF).toByte()
            pcm[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private const val HADP_HEADER_BYTES = 128
    private const val ADPCM_HEADER_BYTES = 4
    private const val ADPCM_FRAME_BYTES = 164
    private const val BYTES_PER_PCM16_SAMPLE = 2

    private val IMA_INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val IMA_STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )
}
