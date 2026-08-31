package com.tji.device.product.speaker.core

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

    fun encodeOggOpusOrNull(
        pcm16le: ByteArray,
        recordId: String,
        sampleRate: Int,
        channels: Int,
        packetMs: Int,
        bitrate: Int
    ): ByteArray? =
        runNative {
            nativeEncodeOggOpus(
                pcm16le,
                recordId,
                sampleRate,
                channels,
                packetMs,
                bitrate
            )
        }

    fun createRealtimeOpusDecoderOrNull(
        sampleRate: Int,
        packetMs: Int
    ): RealtimeOpusDecoder? {
        val handle = runNativeValue {
            nativeCreateRealtimeOpusDecoder(sampleRate, packetMs)
        } ?: return null
        return handle.takeIf { it != 0L }?.let(::RealtimeOpusDecoder)
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

    fun decodeWavPcm16MonoOrNull(
        wav: ByteArray,
        targetSampleRate: Int
    ): ByteArray? =
        runNative {
            nativeDecodeWavPcm16Mono(wav, targetSampleRate)
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

    class RealtimeOpusDecoder internal constructor(
        private var handle: Long
    ) {
        fun decodeOrNull(payload: ByteArray): ByteArray? =
            currentHandle()?.let { current ->
                runNative { nativeDecodeRealtimeOpus(current, payload, false) }
            }

        fun concealOrNull(): ByteArray? =
            currentHandle()?.let { current ->
                runNative { nativeDecodeRealtimeOpus(current, ByteArray(0), true) }
            }

        fun close() {
            currentHandle()?.let { current ->
                runCatching { nativeFreeRealtimeOpusDecoder(current) }
            }
            handle = 0L
        }

        private fun currentHandle(): Long? = handle.takeIf { it != 0L }
    }

    private external fun nativeEncodeOggOpus(
        pcm16le: ByteArray,
        recordId: String,
        sampleRate: Int,
        channels: Int,
        packetMs: Int,
        bitrate: Int
    ): ByteArray

    private external fun nativeCreateRealtimeOpusDecoder(sampleRate: Int, packetMs: Int): Long

    private external fun nativeFreeRealtimeOpusDecoder(handle: Long)

    private external fun nativeDecodeRealtimeOpus(
        handle: Long,
        payload: ByteArray,
        packetLost: Boolean
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

    private external fun nativeDecodeWavPcm16Mono(
        wav: ByteArray,
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

}
