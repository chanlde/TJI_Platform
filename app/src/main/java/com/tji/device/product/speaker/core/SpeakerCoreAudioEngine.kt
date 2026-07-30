package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerFeedbackProtocol
import com.tji.device.product.speaker.audio.SpeakerOpusFile
import com.tji.device.product.speaker.audio.SpeakerPttProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Android 侧的 speaker-core 音频统一入口。
 *
 * 所有公开方法都是原生优先：JNI 可用时先调用 [SpeakerCoreNative]，
 * 并输出 `speakerCoreNative status=native` 日志；如果原生库加载或执行失败，
 * 自动回退到原 Kotlin 实现。
 */
object SpeakerCoreAudioEngine {
    /** 松手后对完整手机麦克风录音执行噪声估计、软门限和防爆音处理。 */
    fun processPushToTalk(pcm16le: ByteArray, sampleRate: Int): ByteArray {
        val processed = SpeakerPttProcessor.process(pcm16le, sampleRate)
        SpeakerLogger.debug(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "pttProcessor sampleRate=$sampleRate in=${pcm16le.size} out=${processed.size}"
        )
        return processed
    }

    fun hasPushToTalkSpeech(pcm16le: ByteArray, sampleRate: Int): Boolean =
        SpeakerPttProcessor.analyze(pcm16le, sampleRate).hasSpeech

    /**
     * 将 PCM16 语音编码成标准 Ogg Opus 文件。
     *
     * @param pcm 单声道小端 PCM16 字节。
     * @param recordId 写入 OpusTags 的稳定 id。
     * @param sampleRate PCM 采样率，单位 Hz。
     * @param channels 声道数；当前喊话器链路期望单声道。
     * @param packetMs Opus 包时长，正式链路固定为 20 ms。
     */
    fun encodeOggOpus(
        pcm: ByteArray,
        recordId: String,
        sampleRate: Int,
        channels: Int = 1,
        packetMs: Int = SpeakerOpusFile.DEFAULT_PACKET_MS,
        bitrate: Int = SpeakerOpusFile.DEFAULT_BITRATE
    ): SpeakerOpusFile {
        val data = SpeakerCoreNative.encodeOggOpusOrNull(
            pcm16le = pcm,
            recordId = recordId,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs,
            bitrate = bitrate
        ) ?: error("当前版本必须加载 speaker-core/libopus，无法生成 Ogg Opus")
        logNative(
            "ogg-opus",
            "recordId=$recordId sampleRate=$sampleRate bitrate=$bitrate bytes=${data.size}"
        )
        return SpeakerOpusFile.fromEncoded(
            data = data,
            pcmBytes = pcm.size - pcm.size % 2,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs,
            bitrate = bitrate
        )
    }

    /**
     * 重采样单声道小端 PCM16。
     *
     * 当前实现使用轻量线性插值，适合语音/控制链路，不是录音棚级重采样器。
     */
    fun resamplePcm16(
        pcm16le: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray {
        if (sourceSampleRate == targetSampleRate) return pcm16le
        SpeakerCoreNative.resamplePcm16OrNull(pcm16le, sourceSampleRate, targetSampleRate)?.let { resampled ->
            logNative("resample", "$sourceSampleRate->$targetSampleRate in=${pcm16le.size} out=${resampled.size}")
            return resampled
        }
        logFallback("resample", "$sourceSampleRate->$targetSampleRate bytes=${pcm16le.size}")
        return pcm16le.resamplePcm16Fallback(sourceSampleRate, targetSampleRate)
    }

    /**
     * 生成本地测试音，格式为单声道小端 PCM16。
     *
     * @param frequencyHz 测试音频率，单位 Hz。
     * @param durationMs 请求时长；小于 [minDurationMs] 时会抬到最小值。
     * @param amplitude 线性振幅，范围 0.0..1.0。
     * @param sampleRate 输出采样率，单位 Hz。
     * @param minDurationMs 最小时长，用于保持分包对齐稳定。
     * @param fadeMs 短淡入/淡出，避免测试音边缘产生咔嗒声。
     */
    fun generateTonePcm16(
        frequencyHz: Int,
        durationMs: Int,
        amplitude: Float,
        sampleRate: Int = SpeakerFeedbackProtocol.SAMPLE_RATE,
        minDurationMs: Int = SpeakerFeedbackProtocol.PACKET_MS,
        fadeMs: Int = SpeakerAudioConfig.Tone.FADE_MS
    ): ByteArray {
        SpeakerCoreNative.generateTonePcm16OrNull(
            frequencyHz = frequencyHz,
            durationMs = durationMs,
            sampleRate = sampleRate,
            minDurationMs = minDurationMs,
            fadeMs = fadeMs,
            amplitude = amplitude
        )?.let { tone ->
            logNative("tone", "frequencyHz=$frequencyHz durationMs=$durationMs bytes=${tone.size}")
            return tone
        }
        logFallback("tone", "frequencyHz=$frequencyHz durationMs=$durationMs")
        return generateTonePcm16Fallback(frequencyHz, durationMs, amplitude, sampleRate, minDurationMs, fadeMs)
    }

    /**
     * 给 PCM16 前面添加静音。
     *
     * 用于 TTS、测试音和录音播放前，让单片机缓冲和功放在第一个可听样本前完成稳定。
     */
    fun prependSilencePcm16(
        pcm16le: ByteArray,
        durationMs: Int,
        sampleRate: Int
    ): ByteArray {
        SpeakerCoreNative.prependSilencePcm16OrNull(pcm16le, durationMs, sampleRate)?.let { padded ->
            logNative("prepend-silence", "durationMs=$durationMs sampleRate=$sampleRate in=${pcm16le.size} out=${padded.size}")
            return padded
        }
        logFallback("prepend-silence", "durationMs=$durationMs sampleRate=$sampleRate bytes=${pcm16le.size}")
        val silenceBytes = sampleRate.coerceAtLeast(1) *
            durationMs.coerceAtLeast(0) /
            MILLIS_PER_SECOND *
            BYTES_PER_PCM16_SAMPLE
        val alignedSize = pcm16le.size - (pcm16le.size % BYTES_PER_PCM16_SAMPLE)
        val alignedInput = if (alignedSize == pcm16le.size) pcm16le else pcm16le.copyOf(alignedSize)
        return if (silenceBytes <= 0) alignedInput else ByteArray(silenceBytes) + alignedInput
    }

    /**
     * 将 Android 系统 TTS 输出的 WAV 解码成单声道 PCM16。
     *
     * 支持 16 位 PCM WAV。多声道输入会先混成单声道，再重采样到 [targetSampleRate]。
     */
    fun decodeWavPcm16Mono(
        wav: ByteArray,
        targetSampleRate: Int
    ): ByteArray {
        SpeakerCoreNative.decodeWavPcm16MonoOrNull(wav, targetSampleRate)?.let { pcm ->
            logNative("wav-pcm16-mono", "targetSampleRate=$targetSampleRate in=${wav.size} out=${pcm.size}")
            return pcm
        }
        logFallback("wav-pcm16-mono", "targetSampleRate=$targetSampleRate bytes=${wav.size}")
        return wav.decodeWavPcm16MonoFallback(targetSampleRate)
    }

    private fun logNative(path: String, detail: String) {
        SpeakerLogger.debug(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "speakerCoreNative status=native path=$path $detail")
    }

    private fun logFallback(path: String, detail: String) {
        SpeakerLogger.debug(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "speakerCoreNative status=fallback path=$path $detail")
    }

    private const val BYTES_PER_PCM16_SAMPLE = 2
    private const val MILLIS_PER_SECOND = 1_000
    private const val TWO_PI = 2.0 * PI

    private fun ByteArray.resamplePcm16Fallback(sourceSampleRate: Int, targetSampleRate: Int): ByteArray {
        require(sourceSampleRate > 0 && targetSampleRate > 0) { "录音重采样参数无效" }
        val sourceSamples = size / BYTES_PER_PCM16_SAMPLE
        if (sourceSamples <= 0) return ByteArray(0)
        val targetSamples = (sourceSamples.toLong() * targetSampleRate / sourceSampleRate)
            .toInt()
            .coerceAtLeast(1)
        val output = ByteArray(targetSamples * BYTES_PER_PCM16_SAMPLE)
        for (index in 0 until targetSamples) {
            val sourcePosition = index.toFloat() * sourceSampleRate.toFloat() / targetSampleRate.toFloat()
            val left = sourcePosition.toInt().coerceIn(0, sourceSamples - 1)
            val right = (left + 1).coerceAtMost(sourceSamples - 1)
            val fraction = sourcePosition - left
            val leftSample = readPcm16Sample(left)
            val rightSample = readPcm16Sample(right)
            val sample = (leftSample + (rightSample - leftSample) * fraction)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val offset = index * BYTES_PER_PCM16_SAMPLE
            output[offset] = (sample and 0xFF).toByte()
            output[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun ByteArray.readPcm16Sample(index: Int): Int {
        val offset = index * BYTES_PER_PCM16_SAMPLE
        return ((this[offset].toInt() and 0xFF) or (this[offset + 1].toInt() shl 8)).toShort().toInt()
    }

    private fun generateTonePcm16Fallback(
        frequencyHz: Int,
        durationMs: Int,
        amplitude: Float,
        sampleRate: Int,
        minDurationMs: Int,
        fadeMs: Int
    ): ByteArray {
        require(frequencyHz > 0 && sampleRate > 0) { "测试音频率或采样率无效" }
        val sampleCount = sampleRate * durationMs.coerceAtLeast(minDurationMs) / MILLIS_PER_SECOND
        val fadeSamples = sampleRate * fadeMs.coerceAtLeast(0) / MILLIS_PER_SECOND
        val safeAmplitude = amplitude.coerceIn(0f, 1f)
        val pcm = ByteArray(sampleCount * BYTES_PER_PCM16_SAMPLE)
        for (index in 0 until sampleCount) {
            val fadeIn = if (fadeSamples > 0) (index.toFloat() / fadeSamples).coerceIn(0f, 1f) else 1f
            val fadeOut = if (fadeSamples > 0) ((sampleCount - index - 1).toFloat() / fadeSamples).coerceIn(0f, 1f) else 1f
            val envelope = minOf(fadeIn, fadeOut)
            val phase = TWO_PI * frequencyHz.toDouble() * index.toDouble() / sampleRate.toDouble()
            val sample = (sin(phase) * Short.MAX_VALUE * safeAmplitude * envelope).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val offset = index * BYTES_PER_PCM16_SAMPLE
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun ByteArray.decodeWavPcm16MonoFallback(targetRate: Int): ByteArray {
        require(targetRate > 0) { "TTS 目标采样率无效: $targetRate" }
        require(size >= 44) { "TTS 音频为空" }
        require(String(this, 0, 4, Charsets.US_ASCII) == "RIFF") { "TTS 音频格式不是 WAV" }
        require(String(this, 8, 4, Charsets.US_ASCII) == "WAVE") { "TTS 音频格式不是 WAVE" }

        var channels = 1
        var sampleRate = SpeakerFeedbackProtocol.SAMPLE_RATE
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= size) {
            val chunkId = String(this, offset, 4, Charsets.US_ASCII)
            val chunkSize = readLe32(offset + 4)
            val chunkData = offset + 8
            if (chunkData + chunkSize > size) break
            when (chunkId) {
                "fmt " -> {
                    val audioFormat = readLe16(chunkData)
                    require(audioFormat == 1) { "TTS WAV 不是 PCM 格式" }
                    channels = readLe16(chunkData + 2).coerceAtLeast(1)
                    sampleRate = readLe32(chunkData + 4).coerceAtLeast(1)
                    bitsPerSample = readLe16(chunkData + 14)
                }
                "data" -> {
                    dataOffset = chunkData
                    dataSize = chunkSize
                    break
                }
            }
            offset = chunkData + chunkSize + (chunkSize and 1)
        }
        require(dataOffset >= 0 && dataSize > 0) { "TTS WAV 没有音频数据" }
        require(bitsPerSample == 16) { "TTS WAV 不是 16bit PCM" }

        val inputFrames = dataSize / (channels * BYTES_PER_PCM16_SAMPLE)
        if (inputFrames <= 0) return ByteArray(0)
        val samples = ShortArray(inputFrames)
        var cursor = dataOffset
        for (frame in 0 until inputFrames) {
            var mixed = 0
            repeat(channels) {
                mixed += readLeI16(cursor)
                cursor += BYTES_PER_PCM16_SAMPLE
            }
            samples[frame] = (mixed / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val outFrames = (inputFrames.toLong() * targetRate / sampleRate)
            .toInt()
            .coerceAtLeast(1)
        val output = ByteArray(outFrames * BYTES_PER_PCM16_SAMPLE)
        if (sampleRate == targetRate) {
            for (i in 0 until outFrames) {
                output.writeLeI16(i * BYTES_PER_PCM16_SAMPLE, samples[i.coerceAtMost(samples.lastIndex)].toInt())
            }
        } else if (sampleRate > targetRate) {
            val ratio = sampleRate.toFloat() / targetRate.toFloat()
            var sourceIndex = 0
            for (i in 0 until outFrames) {
                val lowerBound = (sourceIndex + 1).coerceAtMost(samples.size)
                val nextIndex = ((i + 1) * ratio).roundToInt().coerceIn(lowerBound, samples.size)
                var sum = 0L
                var count = 0
                while (sourceIndex < nextIndex) {
                    sum += samples[sourceIndex].toLong()
                    sourceIndex += 1
                    count += 1
                }
                val value = if (count > 0) {
                    (sum / count).toInt()
                } else {
                    samples[sourceIndex.coerceIn(0, samples.lastIndex)].toInt()
                }
                output.writeLeI16(i * BYTES_PER_PCM16_SAMPLE, value)
            }
        } else {
            for (i in 0 until outFrames) {
                val sourcePos = i.toFloat() * sampleRate.toFloat() / targetRate.toFloat()
                val base = sourcePos.toInt().coerceIn(0, samples.lastIndex)
                val next = (base + 1).coerceAtMost(samples.lastIndex)
                val frac = sourcePos - base
                val value = (samples[base] + (samples[next] - samples[base]) * frac)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.writeLeI16(i * BYTES_PER_PCM16_SAMPLE, value)
            }
        }
        return output
    }

    private fun ByteArray.writeLeI16(offset: Int, value: Int) {
        val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        this[offset] = (clamped and 0xFF).toByte()
        this[offset + 1] = ((clamped shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.readLe16(offset: Int): Int =
        ByteBuffer.wrap(this, offset, BYTES_PER_PCM16_SAMPLE).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun ByteArray.readLeI16(offset: Int): Int =
        ByteBuffer.wrap(this, offset, BYTES_PER_PCM16_SAMPLE).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    private fun ByteArray.readLe32(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

}
