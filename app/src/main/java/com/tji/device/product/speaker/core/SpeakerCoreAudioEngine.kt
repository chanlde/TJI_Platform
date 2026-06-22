package com.tji.device.product.speaker.core

import android.util.Log
import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerHadpEncoder
import com.tji.device.product.speaker.audio.SpeakerHadpFile
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerVoiceProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
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
    /**
     * 将 PCM16 语音编码成 HADP 文件。
     *
     * @param pcm 单声道小端 PCM16 字节。
     * @param recordId 写入 HADP 文件头/元数据的稳定 id。
     * @param codec HADP 音频负载编码；文件播放/存储通常使用 PCM16。
     * @param sampleRate PCM 采样率，单位 Hz。
     * @param channels 声道数；当前喊话器链路期望单声道。
     * @param packetMs 写入 HADP 元数据的逻辑帧时长。
     */
    fun encodeHadp(
        pcm: ByteArray,
        recordId: String,
        codec: SpeakerHadpCodec = SpeakerAudioConfig.Codec.DEFAULT_HADP_CODEC,
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE,
        channels: Int = SpeakerAdpcmPacketizer.CHANNELS,
        packetMs: Int = SpeakerAdpcmPacketizer.PACKET_MS
    ): SpeakerHadpFile {
        SpeakerCoreNative.encodeHadpOrNull(
            pcm16le = pcm,
            recordId = recordId,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs
        )?.let { data ->
            logNative("hadp", "native recordId=$recordId codec=${codec.wireName} bytes=${data.size}")
            return data.toHadpFile(codec)
        }
        logFallback("hadp", "recordId=$recordId codec=${codec.wireName}")
        return SpeakerHadpEncoder.encode(
            pcm = pcm,
            recordId = recordId,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs
        )
    }

    /**
     * 在 HADP 编码前处理完整的按住说话录音。
     *
     * 会应用面向整段录音的清理：松手尾部保护、可选噪声门、清晰度增强/均衡、
     * 自动增益/压缩、限幅、尾部淡出和尾部静音。
     *
     * @param pcm16le 从 AudioRecord 录到的单声道小端 PCM16。
     * @param toneSettings 用户低频/高频 EQ 设置。
     */
    fun processPushToTalk(
        pcm16le: ByteArray,
        toneSettings: SpeakerToneSettings = SpeakerToneSettings()
    ): ByteArray {
        SpeakerCoreNative.processPushToTalkOrNull(pcm16le, toneSettings)?.let { processed ->
            logNative("voice-ptt", "in=${pcm16le.size} out=${processed.size}")
            return processed
        }
        logFallback("voice-ptt", "bytes=${pcm16le.size}")
        return SpeakerVoiceProcessor.processPushToTalk(pcm16le, toneSettings)
    }

    /**
     * 在编码或发送前处理合成音/文件播放 PCM。
     *
     * 播放路径会跳过麦克风清理，但会应用用户 EQ、TTS 低通、响度归一、
     * 限幅、尾部淡出和尾部静音。
     *
     * @param pcm16le 单声道小端 PCM16 播放音频。
     * @param toneSettings 用户低频/高频 EQ 设置。
     * @param sampleRate PCM 采样率，单位 Hz。
     */
    fun applyPlaybackTone(
        pcm16le: ByteArray,
        toneSettings: SpeakerToneSettings = SpeakerToneSettings(),
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE
    ): ByteArray {
        SpeakerCoreNative.processPlaybackOrNull(pcm16le, toneSettings, sampleRate)?.let { processed ->
            logNative("voice-playback", "sampleRate=$sampleRate in=${pcm16le.size} out=${processed.size}")
            return processed
        }
        logFallback("voice-playback", "sampleRate=$sampleRate bytes=${pcm16le.size}")
        return SpeakerVoiceProcessor.applyPlaybackTone(pcm16le, toneSettings, sampleRate)
    }

    /**
     * 创建用于连续 40 ms 麦克风帧的有状态实时喊话处理器。
     *
     * 返回对象会保留高通、清晰度增强和自动增益历史，避免实时语音在 UDP 包之间
     * 忽大忽小或产生咔嗒声。
     */
    fun createLiveVoiceProcessor(): LiveVoiceProcessor =
        LiveVoiceProcessor(
            nativeProcessor = SpeakerCoreNative.createVoiceProcessorOrNull(),
            kotlinProcessor = SpeakerVoiceProcessor()
        )

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
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE,
        minDurationMs: Int = SpeakerAdpcmPacketizer.PACKET_MS,
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
     * 用静音补齐 PCM16，直到字节数成为 [frameBytes] 的整数倍。
     *
     * 这样可以保持 UDP/HADP 分帧对齐，避免最后出现半帧 ADPCM。
     */
    fun padPcm16ToFrame(
        pcm16le: ByteArray,
        frameBytes: Int
    ): ByteArray {
        SpeakerCoreNative.padPcm16ToFrameOrNull(pcm16le, frameBytes)?.let { padded ->
            logNative("pad-frame", "frameBytes=$frameBytes in=${pcm16le.size} out=${padded.size}")
            return padded
        }
        logFallback("pad-frame", "frameBytes=$frameBytes bytes=${pcm16le.size}")
        val safeFrameBytes = frameBytes.coerceAtLeast(BYTES_PER_PCM16_SAMPLE)
        val alignedSize = pcm16le.size - (pcm16le.size % BYTES_PER_PCM16_SAMPLE)
        val remainder = alignedSize % safeFrameBytes
        if (remainder == 0 && alignedSize == pcm16le.size) return pcm16le
        val outputSize = alignedSize + if (remainder == 0) 0 else safeFrameBytes - remainder
        return pcm16le.copyOf(outputSize)
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

    /**
     * 将 Kokoro/sherpa 的浮点采样转成单声道 PCM16。
     *
     * @param samples 归一化浮点 PCM，期望范围 -1.0..1.0。
     * @param sourceSampleRate 模型原始采样率。
     * @param targetSampleRate 喊话器播放输出采样率。
     */
    fun float32ToPcm16(
        samples: FloatArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray {
        SpeakerCoreNative.float32ToPcm16OrNull(samples, sourceSampleRate, targetSampleRate)?.let { pcm ->
            logNative("float32-pcm16", "$sourceSampleRate->$targetSampleRate samples=${samples.size} out=${pcm.size}")
            return pcm
        }
        logFallback("float32-pcm16", "$sourceSampleRate->$targetSampleRate samples=${samples.size}")
        return samples.float32ToPcm16Fallback(sourceSampleRate, targetSampleRate)
    }

    private fun ByteArray.toHadpFile(defaultCodec: SpeakerHadpCodec): SpeakerHadpFile {
        require(size >= HADP_HEADER_BYTES) { "HADP 文件头不完整" }
        require(copyOfRange(0, 4).contentEquals(HADP_MAGIC)) { "HADP magic 无效" }
        val header = ByteBuffer.wrap(this, 0, HADP_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.position(8)
        val codecId = header.short.toInt() and 0xFFFF
        header.position(12)
        val sampleRate = header.int
        val channels = header.short.toInt() and 0xFFFF
        val packetMs = header.short.toInt() and 0xFFFF
        val frameBytes = header.short.toInt() and 0xFFFF
        val samplesPerFrame = header.short.toInt() and 0xFFFF
        val frameCount = header.int
        val audioBytes = header.int
        val durationMs = header.int
        val audioCrc32 = header.int.toLong() and UINT32_MASK
        val codec = SpeakerHadpCodec.entries.firstOrNull { it.id == codecId } ?: defaultCodec
        return SpeakerHadpFile(
            data = this,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            packetMs = packetMs,
            frameBytes = frameBytes,
            samplesPerFrame = samplesPerFrame,
            fileSize = size,
            crc32 = formatCrc32(crc32()),
            durationMs = durationMs,
            frameCount = frameCount,
            audioBytes = audioBytes,
            audioCrc32 = formatCrc32(audioCrc32)
        )
    }

    private fun ByteArray.crc32(): Long =
        CRC32().apply { update(this@crc32) }.value

    private fun formatCrc32(value: Long): String =
        "0x%08X".format(Locale.US, value)

    private fun logNative(path: String, detail: String) {
        Log.d(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "speakerCoreNative status=native path=$path $detail")
    }

    private fun logFallback(path: String, detail: String) {
        Log.d(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "speakerCoreNative status=fallback path=$path $detail")
    }

    /**
     * 有状态实时处理器包装。
     *
     * 原生处理器存在时优先使用；Kotlin 处理器保留为回退实现，
     * 确保 JNI 不可用时麦克风流仍然能工作。
     */
    class LiveVoiceProcessor internal constructor(
        private val nativeProcessor: SpeakerCoreNative.VoiceProcessor?,
        private val kotlinProcessor: SpeakerVoiceProcessor
    ) {
        fun processFrame(
            pcm16le: ByteArray,
            toneSettings: SpeakerToneSettings = SpeakerToneSettings()
        ): ByteArray {
            nativeProcessor?.processFrameOrNull(pcm16le, toneSettings)?.let { processed ->
                return processed
            }
            return kotlinProcessor.processFrame(pcm16le, toneSettings)
        }

        fun close() {
            nativeProcessor?.close()
        }
    }

    private const val HADP_HEADER_BYTES = 128
    private const val BYTES_PER_PCM16_SAMPLE = 2
    private const val MILLIS_PER_SECOND = 1_000
    private const val TWO_PI = 2.0 * PI
    private const val UINT32_MASK = 0xFFFF_FFFFL
    private val HADP_MAGIC = byteArrayOf('H'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 'P'.code.toByte())

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
        var sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
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

    private fun FloatArray.float32ToPcm16Fallback(sourceSampleRate: Int, targetSampleRate: Int): ByteArray {
        require(sourceSampleRate > 0 && targetSampleRate > 0) { "采样率无效: $sourceSampleRate -> $targetSampleRate" }
        if (isEmpty()) return ByteArray(0)
        val outputSamples = if (sourceSampleRate == targetSampleRate) {
            this
        } else {
            resampleLinear(sourceSampleRate, targetSampleRate)
        }
        val pcm = ByteArray(outputSamples.size * BYTES_PER_PCM16_SAMPLE)
        for (i in outputSamples.indices) {
            val clamped = outputSamples[i].coerceIn(-1f, 1f)
            val value = if (clamped < 0f) {
                (clamped * PCM_I16_NEGATIVE_SCALE).roundToInt()
            } else {
                (clamped * PCM_I16_POSITIVE_SCALE).roundToInt()
            }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val offset = i * BYTES_PER_PCM16_SAMPLE
            pcm[offset] = (value and 0xFF).toByte()
            pcm[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun FloatArray.resampleLinear(sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate) return this
        val outSize = (size.toLong() * targetRate / sourceRate).toInt().coerceAtLeast(1)
        val output = FloatArray(outSize)
        for (i in output.indices) {
            val sourcePos = i.toDouble() * sourceRate.toDouble() / targetRate.toDouble()
            val base = sourcePos.toInt().coerceIn(0, lastIndex)
            val next = (base + 1).coerceAtMost(lastIndex)
            val fraction = sourcePos - base
            output[i] = (this[base] + (this[next] - this[base]) * fraction).toFloat()
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

    private const val PCM_I16_NEGATIVE_SCALE = 32768f
    private const val PCM_I16_POSITIVE_SCALE = 32767f
}
