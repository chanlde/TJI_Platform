package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerToneSettings
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

    fun decodeHadpPcm16OrNull(hadp: ByteArray): ByteArray? =
        runNative {
            nativeDecodeHadpPcm16(hadp)
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

    fun processPushToTalkOrNull(
        pcm16le: ByteArray,
        toneSettings: SpeakerToneSettings
    ): ByteArray? =
        processVoiceOrNull(
            pcm16le = pcm16le,
            profile = VOICE_PROFILE_PUSH_TO_TALK,
            sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE,
            toneSettings = toneSettings
        )

    fun processPlaybackOrNull(
        pcm16le: ByteArray,
        toneSettings: SpeakerToneSettings,
        sampleRate: Int
    ): ByteArray? =
        processVoiceOrNull(
            pcm16le = pcm16le,
            profile = VOICE_PROFILE_PLAYBACK,
            sampleRate = sampleRate,
            toneSettings = toneSettings
        )

    fun createVoiceProcessorOrNull(): VoiceProcessor? {
        val handle = runNativeValue { nativeCreateVoiceProcessor() } ?: return null
        if (handle == 0L) return null
        return VoiceProcessor(handle)
    }

    fun buildStandardCommandJsonOrNull(
        deviceId: String,
        msgId: String,
        commandCode: Int,
        commandName: String,
        timestampMs: Long,
        paramsJson: String,
        extraJson: String
    ): String? =
        runNativeValue {
            nativeBuildStandardCommandJson(
                deviceId,
                msgId,
                commandCode,
                commandName,
                timestampMs,
                paramsJson,
                extraJson
            ).toString(Charsets.UTF_8)
        }

    fun buildRecordDownloadCommandJsonOrNull(
        deviceId: String,
        msgId: String,
        recordId: String,
        storeTaskId: String,
        createdAt: String,
        name: String,
        downloadUrl: String,
        fileSize: Long,
        crc32: String,
        durationMs: Int,
        codec: String,
        sampleRate: Int,
        channels: Int,
        packetMs: Int,
        frameBytes: Int,
        samplesPerFrame: Int,
        verifyOnly: Boolean,
        verifyKind: String,
        expectedAudioCrc32: String,
        expectedFirstSamplesJson: String,
        temporary: Boolean,
        visible: Boolean,
        autoPlay: Boolean,
        playbackVolume: Int,
        hasPlaybackVolume: Boolean
    ): String? =
        runNativeValue {
            nativeBuildRecordDownloadCommandJson(
                deviceId,
                msgId,
                recordId,
                storeTaskId,
                createdAt,
                name,
                downloadUrl,
                fileSize,
                crc32,
                durationMs,
                codec,
                sampleRate,
                channels,
                packetMs,
                frameBytes,
                samplesPerFrame,
                verifyOnly,
                verifyKind,
                expectedAudioCrc32,
                expectedFirstSamplesJson,
                temporary,
                visible,
                autoPlay,
                playbackVolume,
                hasPlaybackVolume
            ).toString(Charsets.UTF_8)
        }

    fun resamplePcm16OrNull(
        pcm16le: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray? =
        runNative {
            nativeResamplePcm16(
                pcm16le,
                sourceSampleRate,
                targetSampleRate
            )
        }

    fun generateTonePcm16OrNull(
        frequencyHz: Int,
        durationMs: Int,
        sampleRate: Int,
        minDurationMs: Int,
        fadeMs: Int,
        amplitude: Float
    ): ByteArray? =
        runNative {
            nativeGenerateTonePcm16(
                frequencyHz,
                durationMs,
                sampleRate,
                minDurationMs,
                fadeMs,
                amplitude
            )
        }

    fun prependSilencePcm16OrNull(
        pcm16le: ByteArray,
        durationMs: Int,
        sampleRate: Int
    ): ByteArray? =
        runNative {
            nativePrependSilencePcm16(pcm16le, durationMs, sampleRate)
        }

    fun padPcm16ToFrameOrNull(
        pcm16le: ByteArray,
        frameBytes: Int
    ): ByteArray? =
        runNative {
            nativePadPcm16ToFrame(pcm16le, frameBytes)
        }

    fun decodeWavPcm16MonoOrNull(
        wav: ByteArray,
        targetSampleRate: Int
    ): ByteArray? =
        runNative {
            nativeDecodeWavPcm16Mono(wav, targetSampleRate)
        }

    fun float32ToPcm16OrNull(
        samples: FloatArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray? =
        runNative {
            nativeFloat32ToPcm16(samples, sourceSampleRate, targetSampleRate)
        }

    fun parseMqttStateJsonOrNull(
        serialNumber: String,
        payloadJson: String,
        allowOnline: Boolean
    ): String? =
        runNativeValue {
            nativeParseMqttStateJson(serialNumber, payloadJson, allowOnline).toString(Charsets.UTF_8)
        }

    fun parseMqttAckJsonOrNull(payloadJson: String): String? =
        runNativeValue {
            nativeParseMqttAckJson(payloadJson).toString(Charsets.UTF_8)
        }

    fun parseMqttRecordListJsonOrNull(payloadJson: String): String? =
        runNativeValue {
            nativeParseMqttRecordListJson(payloadJson).toString(Charsets.UTF_8)
        }

    fun parseMqttStorageStatusJsonOrNull(payloadJson: String): String? =
        runNativeValue {
            nativeParseMqttStorageStatusJson(payloadJson).toString(Charsets.UTF_8)
        }

    fun parseMqttRecordEventJsonOrNull(eventType: String, payloadJson: String): String? =
        runNativeValue {
            nativeParseMqttRecordEventJson(eventType, payloadJson).toString(Charsets.UTF_8)
        }

    private fun processVoiceOrNull(
        pcm16le: ByteArray,
        profile: Int,
        sampleRate: Int,
        toneSettings: SpeakerToneSettings
    ): ByteArray? {
        val normalized = toneSettings.normalized()
        return runNative {
            nativeProcessVoice(
                pcm16le,
                profile,
                sampleRate,
                normalized.bassDb,
                normalized.trebleDb
            )
        }
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

    class VoiceProcessor internal constructor(
        private var handle: Long
    ) {
        fun reset() {
            val current = currentHandle() ?: return
            runCatching { nativeResetVoiceProcessor(current) }
        }

        fun processFrameOrNull(
            pcm16le: ByteArray,
            toneSettings: SpeakerToneSettings
        ): ByteArray? {
            val current = currentHandle() ?: return null
            val normalized = toneSettings.normalized()
            return runNative {
                nativeVoiceProcessorProcessFrame(
                    current,
                    pcm16le,
                    normalized.bassDb,
                    normalized.trebleDb
                )
            }
        }

        fun close() {
            val current = currentHandle() ?: return
            runCatching { nativeFreeVoiceProcessor(current) }
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

    private external fun nativeDecodeHadpPcm16(hadp: ByteArray): ByteArray

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

    private external fun nativeProcessVoice(
        pcm16le: ByteArray,
        profile: Int,
        sampleRate: Int,
        bassDb: Float,
        trebleDb: Float
    ): ByteArray

    private external fun nativeCreateVoiceProcessor(): Long

    private external fun nativeFreeVoiceProcessor(handle: Long)

    private external fun nativeResetVoiceProcessor(handle: Long)

    private external fun nativeVoiceProcessorProcessFrame(
        handle: Long,
        pcm16le: ByteArray,
        bassDb: Float,
        trebleDb: Float
    ): ByteArray

    private external fun nativeBuildStandardCommandJson(
        deviceId: String,
        msgId: String,
        commandCode: Int,
        commandName: String,
        timestampMs: Long,
        paramsJson: String,
        extraJson: String
    ): ByteArray

    private external fun nativeBuildRecordDownloadCommandJson(
        deviceId: String,
        msgId: String,
        recordId: String,
        storeTaskId: String,
        createdAt: String,
        name: String,
        downloadUrl: String,
        fileSize: Long,
        crc32: String,
        durationMs: Int,
        codec: String,
        sampleRate: Int,
        channels: Int,
        packetMs: Int,
        frameBytes: Int,
        samplesPerFrame: Int,
        verifyOnly: Boolean,
        verifyKind: String,
        expectedAudioCrc32: String,
        expectedFirstSamplesJson: String,
        temporary: Boolean,
        visible: Boolean,
        autoPlay: Boolean,
        playbackVolume: Int,
        hasPlaybackVolume: Boolean
    ): ByteArray

    private external fun nativeResamplePcm16(
        pcm16le: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray

    private external fun nativeGenerateTonePcm16(
        frequencyHz: Int,
        durationMs: Int,
        sampleRate: Int,
        minDurationMs: Int,
        fadeMs: Int,
        amplitude: Float
    ): ByteArray

    private external fun nativePrependSilencePcm16(
        pcm16le: ByteArray,
        durationMs: Int,
        sampleRate: Int
    ): ByteArray

    private external fun nativePadPcm16ToFrame(
        pcm16le: ByteArray,
        frameBytes: Int
    ): ByteArray

    private external fun nativeDecodeWavPcm16Mono(
        wav: ByteArray,
        targetSampleRate: Int
    ): ByteArray

    private external fun nativeFloat32ToPcm16(
        samples: FloatArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray

    private external fun nativeParseMqttStateJson(
        serialNumber: String,
        payloadJson: String,
        allowOnline: Boolean
    ): ByteArray

    private external fun nativeParseMqttAckJson(payloadJson: String): ByteArray

    private external fun nativeParseMqttRecordListJson(payloadJson: String): ByteArray

    private external fun nativeParseMqttStorageStatusJson(payloadJson: String): ByteArray

    private external fun nativeParseMqttRecordEventJson(
        eventType: String,
        payloadJson: String
    ): ByteArray

    private const val VOICE_PROFILE_LIVE = 0
    private const val VOICE_PROFILE_PUSH_TO_TALK = 1
    private const val VOICE_PROFILE_PLAYBACK = 2
}
