package com.tji.device.product.speaker.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets

class SpeakerAdpcmPacketizer(
    private val streamContext: SpeakerUdpStreamContext? = null
) {
    private var sequence: Int = 0
    private var legacyTimestampSamples: Int = 0
    private var timestampMs: Int = 0
    private var stepIndex: Int = 0

    fun reset() {
        sequence = 0
        legacyTimestampSamples = 0
        timestampMs = 0
        stepIndex = 0
    }

    fun packetize(pcm16le: ByteArray, isLastPacket: Boolean = false): ByteArray? {
        val aligned = pcm16le.size - (pcm16le.size % 2)
        if (aligned < 2) return null
        val encoded = encodeImaAdpcmBlock(pcm16le, aligned, stepIndex)
        stepIndex = encoded.nextStepIndex
        val header = streamContext?.let { context ->
            buildV2Header(context, encoded, isLastPacket)
        } ?: buildLegacyHeader(encoded)
        sequence += 1
        legacyTimestampSamples += encoded.sampleCount
        timestampMs += PACKET_MS
        return header + encoded.payload
    }

    private fun buildLegacyHeader(encoded: EncodedAdpcm): ByteArray =
        ByteBuffer.allocate(LEGACY_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(MAGIC.toShort())
            .put(LEGACY_VERSION.toByte())
            .put(CODEC_IMA_ADPCM.toByte())
            .putInt(sequence)
            .putInt(legacyTimestampSamples)
            .putShort(SAMPLE_RATE.toShort())
            .put(CHANNELS.toByte())
            .put(0)
            .putShort(encoded.payload.size.toShort())
            .putShort(encoded.sampleCount.toShort())
            .array()

    private fun buildV2Header(
        context: SpeakerUdpStreamContext,
        encoded: EncodedAdpcm,
        isLastPacket: Boolean
    ): ByteArray {
        val deviceBytes = context.deviceId.toByteArray(Charsets.UTF_8).take(MAX_ID_BYTES).toByteArray()
        val taskBytes = context.taskId.toByteArray(Charsets.UTF_8).take(MAX_ID_BYTES).toByteArray()
        val talkBytes = context.talkId.toByteArray(Charsets.UTF_8).take(MAX_ID_BYTES).toByteArray()
        val headerLen = V2_FIXED_HEADER_BYTES + deviceBytes.size + taskBytes.size + talkBytes.size
        val flags = buildFlags(context.type, isLastPacket)
        return ByteBuffer.allocate(headerLen)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(MAGIC.toShort())
            .put(V2_VERSION.toByte())
            .put(CODEC_IMA_ADPCM.toByte())
            .putShort(headerLen.toShort())
            .putShort(flags.toShort())
            .putInt(sequence)
            .putInt(timestampMs)
            .putShort(SAMPLE_RATE.toShort())
            .put(CHANNELS.toByte())
            .put(PACKET_MS.toByte())
            .putShort(encoded.payload.size.toShort())
            .putShort(encoded.sampleCount.toShort())
            .put(deviceBytes.size.toByte())
            .put(taskBytes.size.toByte())
            .put(talkBytes.size.toByte())
            .put(0)
            .put(deviceBytes)
            .put(taskBytes)
            .put(talkBytes)
            .array()
    }

    private fun buildFlags(type: SpeakerUdpStreamType, isLastPacket: Boolean): Int {
        var flags = if (isLastPacket) FLAG_LAST_PACKET else 0
        flags = flags or when (type) {
            SpeakerUdpStreamType.RecordStore -> FLAG_STORE_TO_SD
            SpeakerUdpStreamType.PlaybackFeedback -> FLAG_PLAYBACK or FLAG_FEEDBACK
        }
        return flags
    }

    private fun encodeImaAdpcmBlock(pcm: ByteArray, length: Int, initialStepIndex: Int): EncodedAdpcm {
        val sampleCount = length / 2
        var predictor = readLeI16(pcm, 0)
        var index = initialStepIndex.coerceIn(0, 88)
        val out = ArrayList<Byte>(4 + sampleCount / 2)
        out.add((predictor and 0xFF).toByte())
        out.add(((predictor shr 8) and 0xFF).toByte())
        out.add(index.toByte())
        out.add(0)
        var pending: Int? = null

        for (sampleIndex in 1 until sampleCount) {
            val sample = readLeI16(pcm, sampleIndex * 2)
            val step = IMA_STEP_TABLE[index]
            var diff = sample - predictor
            var nibble = 0
            if (diff < 0) {
                nibble = 8
                diff = -diff
            }

            var delta = step shr 3
            if (diff >= step) {
                nibble = nibble or 4
                diff -= step
                delta += step
            }
            if (diff >= (step shr 1)) {
                nibble = nibble or 2
                diff -= step shr 1
                delta += step shr 1
            }
            if (diff >= (step shr 2)) {
                nibble = nibble or 1
                delta += step shr 2
            }

            predictor = if ((nibble and 8) != 0) predictor - delta else predictor + delta
            predictor = predictor.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            index = (index + IMA_INDEX_TABLE[nibble]).coerceIn(0, 88)

            if (pending == null) {
                pending = nibble and 0x0F
            } else {
                out.add((pending or ((nibble and 0x0F) shl 4)).toByte())
                pending = null
            }
        }
        if (pending != null) out.add(pending.toByte())
        return EncodedAdpcm(out.toByteArray(), sampleCount, index)
    }

    private fun readLeI16(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)
        return value.toShort().toInt()
    }

    private data class EncodedAdpcm(
        val payload: ByteArray,
        val sampleCount: Int,
        val nextStepIndex: Int
    )

    companion object {
        const val SAMPLE_RATE = 8_000
        const val CHANNELS = 1
        const val PACKET_MS = 40
        const val PCM_FRAME_BYTES = SAMPLE_RATE * PACKET_MS / 1_000 * 2
        private const val LEGACY_HEADER_BYTES = 20
        private const val V2_FIXED_HEADER_BYTES = 28
        private const val MAGIC = 0xA55A
        private const val LEGACY_VERSION = 1
        private const val V2_VERSION = 2
        private const val CODEC_IMA_ADPCM = 1
        private const val FLAG_LAST_PACKET = 0x01
        private const val FLAG_STORE_TO_SD = 0x02
        private const val FLAG_PLAYBACK = 0x04
        private const val FLAG_FEEDBACK = 0x08
        private const val MAX_ID_BYTES = 255

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
}

data class SpeakerUdpStreamContext(
    val deviceId: String,
    val taskId: String,
    val type: SpeakerUdpStreamType,
    val talkId: String = ""
)

enum class SpeakerUdpStreamType(val code: Int) {
    RecordStore(1),
    PlaybackFeedback(2)
}
