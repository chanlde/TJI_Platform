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

    fun createAdpcmPacketizerOrNull(): AdpcmPacketizer? {
        val handle = runNativeValue { nativeCreatePacketizer() } ?: return null
        if (handle == 0L) return null
        return AdpcmPacketizer(handle)
    }

    private inline fun runNative(block: () -> ByteArray): ByteArray? {
        if (!isAvailable()) return null
        return runCatching(block).getOrNull()
    }

    private inline fun <T> runNativeValue(block: () -> T): T? {
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

    class AdpcmPacketizer internal constructor(
        private var handle: Long
    ) {
        fun reset() {
            val current = currentHandle() ?: return
            runCatching { nativeResetPacketizer(current) }
        }

        fun packetizeLegacyOrNull(
            pcm16le: ByteArray,
            sequence: Int,
            timestampSamples: Int
        ): ByteArray? {
            val current = currentHandle() ?: return null
            return runNative {
                nativePacketizerPacketizeLegacy(current, pcm16le, sequence, timestampSamples)
            }
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
        ): ByteArray? {
            val current = currentHandle() ?: return null
            return runNative {
                nativePacketizerPacketizeV2(
                    current,
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
        }

        fun close() {
            val current = currentHandle() ?: return
            runCatching { nativeFreePacketizer(current) }
            handle = 0
        }

        private fun currentHandle(): Long? =
            handle.takeIf { it != 0L }
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

    private external fun nativeCreatePacketizer(): Long

    private external fun nativeFreePacketizer(handle: Long)

    private external fun nativeResetPacketizer(handle: Long)

    private external fun nativePacketizerPacketizeLegacy(
        handle: Long,
        pcm16le: ByteArray,
        sequence: Int,
        timestampSamples: Int
    ): ByteArray

    private external fun nativePacketizerPacketizeV2(
        handle: Long,
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
