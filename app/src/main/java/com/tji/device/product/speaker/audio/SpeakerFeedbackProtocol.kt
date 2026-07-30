package com.tji.device.product.speaker.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.TreeMap

/**
 * MCU 板载 PDM 麦克风回传包。
 *
 * 这不是手机麦克风数据：MCU 固定输出 16 kHz/单声道/20 ms，App 只负责
 * 校验、抖动缓冲、Opus 解码和本机监听。
 */
data class SpeakerFeedbackPacket(
    val sequence: Int,
    val timestampSamples: Long,
    val codec: Int,
    val sampleRate: Int,
    val channels: Int,
    val packetMs: Int,
    val sampleCount: Int,
    val deviceId: String,
    val sessionId: String,
    val talkId: String,
    val payload: ByteArray
)

object SpeakerFeedbackProtocol {
    fun parse(packet: ByteArray, length: Int = packet.size): SpeakerFeedbackPacket? {
        if (length !in FIXED_HEADER_BYTES..packet.size) return null
        val header = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        if ((header.short.toInt() and 0xFFFF) != MAGIC) return null
        if ((header.get().toInt() and 0xFF) != VERSION) return null

        val codec = header.get().toInt() and 0xFF
        val headerBytes = header.short.toInt() and 0xFFFF
        val flags = header.short.toInt() and 0xFFFF
        val sequence = header.int
        val timestamp = header.int.toLong() and 0xFFFF_FFFFL
        val sampleRate = header.short.toInt() and 0xFFFF
        val channels = header.get().toInt() and 0xFF
        val packetMs = header.get().toInt() and 0xFF
        val payloadBytes = header.short.toInt() and 0xFFFF
        val sampleCount = header.short.toInt() and 0xFFFF
        val deviceLength = header.get().toInt() and 0xFF
        val sessionLength = header.get().toInt() and 0xFF
        val talkLength = header.get().toInt() and 0xFF
        header.get() // reserved

        if ((flags and FLAG_FEEDBACK) == 0 ||
            codec != CODEC_OPUS ||
            sampleRate != SAMPLE_RATE ||
            channels != CHANNELS ||
            packetMs != PACKET_MS ||
            sampleCount != SAMPLES_PER_PACKET
        ) {
            return null
        }
        if (deviceLength !in 1..MAX_ID_BYTES ||
            sessionLength !in 1..MAX_ID_BYTES ||
            talkLength !in 1..MAX_ID_BYTES
        ) {
            return null
        }
        val expectedHeader = FIXED_HEADER_BYTES + deviceLength + sessionLength + talkLength
        if (headerBytes != expectedHeader || headerBytes + payloadBytes != length) return null
        if (payloadBytes !in 1..MAX_OPUS_PACKET_BYTES) return null

        return runCatching {
            val deviceStart = FIXED_HEADER_BYTES
            val sessionStart = deviceStart + deviceLength
            val talkStart = sessionStart + sessionLength
            val payloadStart = talkStart + talkLength
            SpeakerFeedbackPacket(
                sequence = sequence,
                timestampSamples = timestamp,
                codec = codec,
                sampleRate = sampleRate,
                channels = channels,
                packetMs = packetMs,
                sampleCount = sampleCount,
                deviceId = decodeUtf8Strict(packet, deviceStart, sessionStart),
                sessionId = decodeUtf8Strict(packet, sessionStart, talkStart),
                talkId = decodeUtf8Strict(packet, talkStart, payloadStart),
                payload = packet.copyOfRange(payloadStart, length)
            )
        }.getOrNull()
    }

    private fun decodeUtf8Strict(bytes: ByteArray, start: Int, end: Int): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, end - start))
            .toString()

    const val CODEC_OPUS = 2
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val PACKET_MS = 20
    const val SAMPLES_PER_PACKET = 320
    const val PCM_BYTES_PER_PACKET = SAMPLES_PER_PACKET * 2
    const val MAX_OPUS_PACKET_BYTES = 1_275
    const val MAX_DATAGRAM_BYTES = 1_600
    private const val MAX_ID_BYTES = 32
    private const val MAGIC = 0xA55A
    private const val VERSION = 2
    private const val FIXED_HEADER_BYTES = 28
    private const val FLAG_FEEDBACK = 0x0008
}

/**
 * 对少量 UDP 乱序进行重排；超过窗口的缺包输出静音帧，避免 AudioTrack 时间轴跳变。
 */
class SpeakerFeedbackJitterBuffer(
    private val prebufferPackets: Int = 4,
    private val maxReorderPackets: Int = 6
) {
    private val pending = TreeMap<Int, SpeakerFeedbackPacket>()
    private var expectedSequence: Int? = null
    private var started = false

    var duplicatePackets: Long = 0
        private set
    var concealedPackets: Long = 0
        private set

    init {
        require(prebufferPackets > 0) { "预缓冲包数必须大于0" }
        require(maxReorderPackets > 0) { "乱序窗口必须大于0" }
    }

    fun offer(packet: SpeakerFeedbackPacket): List<SpeakerFeedbackPacket?> {
        val expected = expectedSequence
        if (started && expected != null && packet.sequence < expected) {
            duplicatePackets += 1
            return emptyList()
        }
        if (pending.putIfAbsent(packet.sequence, packet) != null) {
            duplicatePackets += 1
            return emptyList()
        }
        if (!started) {
            expectedSequence = pending.firstKey()
            if (pending.size < prebufferPackets) return emptyList()
            started = true
        }

        val ready = mutableListOf<SpeakerFeedbackPacket?>()
        while (pending.isNotEmpty()) {
            val nextSequence = expectedSequence ?: break
            val exact = pending.remove(nextSequence)
            if (exact != null) {
                ready += exact
                expectedSequence = nextSequence + 1
                continue
            }
            if (pending.size < maxReorderPackets) break
            ready += null
            concealedPackets += 1
            val firstPending = pending.firstKey()
            expectedSequence = if (firstPending.toLong() - nextSequence.toLong() > maxReorderPackets) {
                firstPending
            } else {
                nextSequence + 1
            }
        }
        return ready
    }
}
