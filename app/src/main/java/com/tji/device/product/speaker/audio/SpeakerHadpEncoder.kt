package com.tji.device.product.speaker.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets
import java.util.Locale
import java.util.zip.CRC32

object SpeakerHadpEncoder {
    fun encode(pcm: ByteArray, recordId: String): SpeakerHadpFile {
        val alignedPcm = pcm.copyOf(pcm.size - (pcm.size % BYTES_PER_PCM16_SAMPLE))
        require(alignedPcm.isNotEmpty()) { "录音音频为空" }

        val packetizer = SpeakerAdpcmPacketizer()
        val payloads = ArrayList<ByteArray>()
        var offset = 0
        while (offset < alignedPcm.size) {
            val end = minOf(offset + SpeakerAdpcmPacketizer.PCM_FRAME_BYTES, alignedPcm.size)
            val frame = alignedPcm.copyOfRange(offset, end).padPcmFrame()
            val packet = packetizer.packetize(frame) ?: error("ADPCM 编码失败")
            payloads += packet.copyOfRange(LEGACY_HEADER_BYTES, packet.size)
            offset = end
        }

        val audioSize = payloads.sumOf { it.size }
        val audioData = ByteArray(audioSize)
        var writeOffset = 0
        payloads.forEach { payload ->
            payload.copyInto(audioData, destinationOffset = writeOffset)
            writeOffset += payload.size
        }

        val frameCount = payloads.size
        val durationMs = frameCount * SpeakerAdpcmPacketizer.PACKET_MS
        val audioCrc32 = crc32(audioData)
        val header = buildHeader(
            recordId = recordId,
            frameCount = frameCount,
            audioBytes = audioData.size,
            durationMs = durationMs,
            audioCrc32 = audioCrc32
        )
        val data = header + audioData
        val fileCrc32 = crc32(data)
        return SpeakerHadpFile(
            data = data,
            fileSize = data.size,
            crc32 = formatCrc32(fileCrc32),
            durationMs = durationMs,
            frameCount = frameCount,
            audioBytes = audioData.size,
            audioCrc32 = formatCrc32(audioCrc32)
        )
    }

    private fun buildHeader(
        recordId: String,
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
            .putShort(CODEC_IMA_ADPCM.toShort())
            .putShort(0)
            .putInt(SpeakerAdpcmPacketizer.SAMPLE_RATE)
            .putShort(SpeakerAdpcmPacketizer.CHANNELS.toShort())
            .putShort(SpeakerAdpcmPacketizer.PACKET_MS.toShort())
            .putShort(ADPCM_FRAME_BYTES.toShort())
            .putShort(SAMPLES_PER_FRAME.toShort())
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

    private const val BYTES_PER_PCM16_SAMPLE = 2
    private const val LEGACY_HEADER_BYTES = 20
    private const val HADP_HEADER_BYTES = 128
    private const val HADP_VERSION = 1
    private const val CODEC_IMA_ADPCM = 1
    private const val ADPCM_FRAME_BYTES = 164
    private const val SAMPLES_PER_FRAME = 320
    private const val RECORD_ID_BYTES = 64
    private const val HEADER_PREFIX_AND_RECORD_ID_BYTES = 44 + RECORD_ID_BYTES
    private val HADP_MAGIC = byteArrayOf('H'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte())
}

data class SpeakerHadpFile(
    val data: ByteArray,
    val fileSize: Int,
    val crc32: String,
    val durationMs: Int,
    val frameCount: Int,
    val audioBytes: Int,
    val audioCrc32: String
)
