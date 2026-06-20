package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerUdpStreamType

object SpeakerCoreNative {
    private const val LIBRARY_NAME = "tji_speaker_core_jni"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loaded = false

    fun isAvailable(): Boolean {
        ensureLoaded()
        return loaded
    }

    fun encodeHadpOrNull(
        pcm16le: ByteArray,
        recordId: String,
        codec: SpeakerHadpCodec,
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE,
        channels: Int = SpeakerAdpcmPacketizer.CHANNELS,
        packetMs: Int = SpeakerAdpcmPacketizer.PACKET_MS
    ): ByteArray? =
        runNative {
            nativeEncodeHadp(
                pcm16le,
                recordId,
                codec.id,
                sampleRate,
                channels,
                packetMs
            )
        }

    fun packetizeLegacyOrNull(
        pcm16le: ByteArray,
        sequence: Int,
        timestampSamples: Int
    ): ByteArray? =
        runNative {
            nativePacketizeLegacy(pcm16le, sequence, timestampSamples)
        }

    fun packetizeV2OrNull(
        pcm16le: ByteArray,
        sequence: Int,
        timestampMs: Int,
        deviceId: String,
        taskId: String,
        talkId: String,
        streamType: SpeakerUdpStreamType,
        isLastPacket: Boolean
    ): ByteArray? =
        runNative {
            nativePacketizeV2(
                pcm16le,
                sequence,
                timestampMs,
                deviceId,
                taskId,
                talkId,
                streamType.code,
                isLastPacket
            )
        }

    private inline fun runNative(block: () -> ByteArray): ByteArray? {
        if (!isAvailable()) return null
        return runCatching(block).getOrNull()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loadAttempted) return
        loadAttempted = true
        loaded = runCatching {
            System.loadLibrary(LIBRARY_NAME)
        }.isSuccess
    }

    private external fun nativeEncodeHadp(
        pcm16le: ByteArray,
        recordId: String,
        codecId: Int,
        sampleRate: Int,
        channels: Int,
        packetMs: Int
    ): ByteArray

    private external fun nativePacketizeLegacy(
        pcm16le: ByteArray,
        sequence: Int,
        timestampSamples: Int
    ): ByteArray

    private external fun nativePacketizeV2(
        pcm16le: ByteArray,
        sequence: Int,
        timestampMs: Int,
        deviceId: String,
        taskId: String,
        talkId: String,
        streamType: Int,
        isLastPacket: Boolean
    ): ByteArray
}
