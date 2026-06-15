package com.tji.device.product.speaker.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets
import java.util.Locale
import java.util.zip.CRC32

object SpeakerHadpEncoder {
    fun encode(
        pcm: ByteArray,
        recordId: String,
        codec: SpeakerHadpCodec = SpeakerAudioConfig.Codec.DEFAULT_HADP_CODEC,
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE,
        channels: Int = SpeakerAdpcmPacketizer.CHANNELS,
        packetMs: Int = SpeakerAdpcmPacketizer.PACKET_MS
    ): SpeakerHadpFile {
        val alignedPcm = pcm.copyOf(pcm.size - (pcm.size % BYTES_PER_PCM16_SAMPLE))
        require(alignedPcm.isNotEmpty()) { "录音音频为空" }
        require(channels == 1) { "HADP 当前只支持 mono: channels=$channels" }
        require(packetMs > 0) { "packetMs 必须大于 0" }
        val pcmFrameBytes = sampleRate * packetMs / MILLIS_PER_SECOND * BYTES_PER_PCM16_SAMPLE
        val samplesPerFrame = pcmFrameBytes / BYTES_PER_PCM16_SAMPLE
        val frameBytes = when (codec) {
            SpeakerHadpCodec.ImaAdpcm -> {
                require(sampleRate == SpeakerAdpcmPacketizer.SAMPLE_RATE && packetMs == SpeakerAdpcmPacketizer.PACKET_MS) {
                    "ADPCM HADP 当前只支持 8k/40ms"
                }
                codec.frameBytes
            }
            SpeakerHadpCodec.Pcm16 -> pcmFrameBytes
        }

        val payloads = when (codec) {
            SpeakerHadpCodec.ImaAdpcm -> encodeAdpcmFrames(alignedPcm)
            SpeakerHadpCodec.Pcm16 -> encodePcm16Frames(alignedPcm, pcmFrameBytes)
        }

        val audioSize = payloads.sumOf { it.size }
        val audioData = ByteArray(audioSize)
        var writeOffset = 0
        payloads.forEach { payload ->
            payload.copyInto(audioData, destinationOffset = writeOffset)
            writeOffset += payload.size
        }

        val frameCount = payloads.size
        val durationMs = frameCount * packetMs
        val audioCrc32 = crc32(audioData)
        val header = buildHeader(
            recordId = recordId,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs,
            frameBytes = frameBytes,
            samplesPerFrame = samplesPerFrame,
            frameCount = frameCount,
            audioBytes = audioData.size,
            durationMs = durationMs,
            audioCrc32 = audioCrc32
        )
        val data = header + audioData
        val fileCrc32 = crc32(data)
        return SpeakerHadpFile(
            data = data,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs,
            frameBytes = frameBytes,
            samplesPerFrame = samplesPerFrame,
            fileSize = data.size,
            crc32 = formatCrc32(fileCrc32),
            durationMs = durationMs,
            frameCount = frameCount,
            audioBytes = audioData.size,
            audioCrc32 = formatCrc32(audioCrc32)
        )
    }

    private fun encodeAdpcmFrames(pcm: ByteArray): List<ByteArray> {
        val packetizer = SpeakerAdpcmPacketizer()
        val payloads = ArrayList<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + SpeakerAdpcmPacketizer.PCM_FRAME_BYTES, pcm.size)
            val frame = pcm.copyOfRange(offset, end).padPcmFrame()
            val packet = packetizer.packetize(frame) ?: error("ADPCM 编码失败")
            payloads += packet.copyOfRange(LEGACY_HEADER_BYTES, packet.size)
            offset = end
        }
        return payloads
    }

    private fun encodePcm16Frames(pcm: ByteArray, pcmFrameBytes: Int): List<ByteArray> {
        val payloads = ArrayList<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + pcmFrameBytes, pcm.size)
            payloads += pcm.copyOfRange(offset, end).padFrame(pcmFrameBytes)
            offset = end
        }
        return payloads
    }

    private fun buildHeader(
        recordId: String,
        codec: SpeakerHadpCodec,
        sampleRate: Int,
        channels: Int,
        packetMs: Int,
        frameBytes: Int,
        samplesPerFrame: Int,
        frameCount: Int,
        audioBytes: Int,
        durationMs: Int,
        audioCrc32: Long
    ): ByteArray {
        val recordIdBytes = recordId.toByteArray(Charsets.UTF_8).take(RECORD_ID_BYTES).toByteArray()
        return ByteBuffer.allocate(HADP_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(HADP_MAGIC)
            .putShort(HADP_VERSION.toShort())
            .putShort(HADP_HEADER_BYTES.toShort())
            .putShort(codec.id.toShort())
            .putShort(0)
            .putInt(sampleRate)
            .putShort(channels.toShort())
            .putShort(packetMs.toShort())
            .putShort(frameBytes.toShort())
            .putShort(samplesPerFrame.toShort())
            .putInt(frameCount)
            .putInt(audioBytes)
            .putInt(durationMs)
            .putInt(audioCrc32.toInt())
            .putInt(0)
            .put(recordIdBytes)
            .put(ByteArray(RECORD_ID_BYTES - recordIdBytes.size))
            .put(ByteArray(HADP_HEADER_BYTES - HEADER_PREFIX_AND_RECORD_ID_BYTES))
            .array()
    }

    private fun crc32(data: ByteArray): Long =
        CRC32().apply { update(data) }.value

    private fun formatCrc32(value: Long): String =
        "0x%08X".format(Locale.US, value)

    private fun ByteArray.padPcmFrame(): ByteArray =
        if (size == SpeakerAdpcmPacketizer.PCM_FRAME_BYTES) {
            this
        } else {
            copyOf(SpeakerAdpcmPacketizer.PCM_FRAME_BYTES)
        }

    private fun ByteArray.padFrame(frameBytes: Int): ByteArray =
        if (size == frameBytes) this else copyOf(frameBytes)

    private const val BYTES_PER_PCM16_SAMPLE = 2
    private const val MILLIS_PER_SECOND = 1_000
    private const val LEGACY_HEADER_BYTES = 20
    private const val HADP_HEADER_BYTES = 128
    private const val HADP_VERSION = 1
    private const val RECORD_ID_BYTES = 64
    private const val HEADER_PREFIX_AND_RECORD_ID_BYTES = 44 + RECORD_ID_BYTES
    private val HADP_MAGIC = byteArrayOf('H'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte())
}

enum class SpeakerHadpCodec(
    val id: Int,
    val wireName: String,
    val frameBytes: Int,
    val samplesPerFrame: Int
) {
    ImaAdpcm(
        id = 1,
        wireName = "ima_adpcm",
        frameBytes = 164,
        samplesPerFrame = 320
    ),
    Pcm16(
        id = 2,
        wireName = "pcm16",
        frameBytes = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES,
        samplesPerFrame = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / 2
    )
}

data class SpeakerHadpFile(
    val data: ByteArray,
    val codec: SpeakerHadpCodec,
    val sampleRate: Int,
    val channels: Int,
    val packetMs: Int,
    val frameBytes: Int,
    val samplesPerFrame: Int,
    val fileSize: Int,
    val crc32: String,
    val durationMs: Int,
    val frameCount: Int,
    val audioBytes: Int,
    val audioCrc32: String
)
