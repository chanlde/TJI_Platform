package com.tji.device.product.speaker.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 完整按住说话录音的离线语音前端。
 *
 * 只在松手后运行，因此噪声底可以从整段录音的安静窗口估计；处理顺序为
 * 松手尾部保护、去直流、高通、软噪声门、语音区 AGC、压缩限幅、低通和
 * 尾部淡出。它不改变 Opus 协议，只改善送入编码器的 PCM。
 */
object SpeakerPttProcessor {
    data class Analysis(
        val hasSpeech: Boolean,
        val noiseRms: Float,
        val activeRms: Float,
        val activeWindows: Int
    )

    fun analyze(pcm16le: ByteArray, sampleRate: Int): Analysis {
        val samples = guardedSamples(pcm16le, sampleRate)
        if (samples.isEmpty()) return Analysis(false, 0f, 0f, 0)
        removeDc(samples)
        highPass(samples, sampleRate)
        return analyzeSamples(samples, sampleRate)
    }

    fun process(pcm16le: ByteArray, sampleRate: Int): ByteArray {
        val samples = guardedSamples(pcm16le, sampleRate)
        if (samples.isEmpty()) return ByteArray(0)
        removeDc(samples)
        highPass(samples, sampleRate)
        val analysis = analyzeSamples(samples, sampleRate)
        if (!analysis.hasSpeech) return ByteArray(0)

        applyNoiseGate(samples, sampleRate, analysis.noiseRms)
        applyActiveAgc(samples, sampleRate, analysis.noiseRms)
        compressAndLimit(samples)
        lowPass(samples, sampleRate)
        fadeEdges(samples, sampleRate)
        return samples.toPcm16leWithTail(sampleRate)
    }

    private fun guardedSamples(pcm16le: ByteArray, sampleRate: Int): FloatArray {
        require(sampleRate in 8_000..48_000) { "按住喊话采样率无效: $sampleRate" }
        val alignedBytes = pcm16le.size and -2
        val totalSamples = alignedBytes / 2
        val releaseGuard = sampleRate * RELEASE_GUARD_MS / 1_000
        val keptSamples = (totalSamples - releaseGuard).coerceAtLeast(0)
        return FloatArray(keptSamples) { index ->
            val offset = index * 2
            val value = (pcm16le[offset].toInt() and 0xff) or
                (pcm16le[offset + 1].toInt() shl 8)
            value.toShort() / 32768f
        }
    }

    private fun removeDc(samples: FloatArray) {
        val mean = samples.sum() / samples.size
        samples.indices.forEach { samples[it] -= mean }
    }

    private fun highPass(samples: FloatArray, sampleRate: Int) {
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * HIGH_PASS_HZ)
        val alpha = rc / (rc + dt)
        var previousInput = 0f
        var previousOutput = 0f
        for (index in samples.indices) {
            val input = samples[index]
            previousOutput = alpha * (previousOutput + input - previousInput)
            previousInput = input
            samples[index] = previousOutput
        }
    }

    private fun analyzeSamples(samples: FloatArray, sampleRate: Int): Analysis {
        val window = max(1, sampleRate * WINDOW_MS / 1_000)
        val rmsValues = ArrayList<Float>((samples.size + window - 1) / window)
        var offset = 0
        while (offset < samples.size) {
            val end = min(offset + window, samples.size)
            rmsValues += rms(samples, offset, end)
            offset = end
        }
        if (rmsValues.isEmpty()) return Analysis(false, 0f, 0f, 0)
        val sorted = rmsValues.sorted()
        val lowCount = max(1, (sorted.size * NOISE_PERCENT).toInt())
        val noiseRms = max(NOISE_FLOOR_RMS, sorted.take(lowCount).average().toFloat())
        val openRms = max(MIN_SPEECH_RMS, noiseRms * GATE_OPEN_MULTIPLIER)
        val active = rmsValues.filter { it >= openRms }
        return Analysis(
            hasSpeech = active.size >= MIN_ACTIVE_WINDOWS,
            noiseRms = noiseRms,
            activeRms = if (active.isEmpty()) 0f else active.average().toFloat(),
            activeWindows = active.size
        )
    }

    private fun applyNoiseGate(samples: FloatArray, sampleRate: Int, noiseRms: Float) {
        val window = max(1, sampleRate * WINDOW_MS / 1_000)
        val closeRms = max(NOISE_FLOOR_RMS, noiseRms * GATE_CLOSE_MULTIPLIER)
        val openRms = max(MIN_SPEECH_RMS, noiseRms * GATE_OPEN_MULTIPLIER)
        var gain = CLOSED_GAIN
        var offset = 0
        while (offset < samples.size) {
            val end = min(offset + window, samples.size)
            val level = rms(samples, offset, end)
            val target = when {
                level <= closeRms -> CLOSED_GAIN
                level >= openRms -> 1f
                else -> CLOSED_GAIN +
                    (level - closeRms) / (openRms - closeRms) * (1f - CLOSED_GAIN)
            }
            gain += (target - gain) * GATE_SMOOTHING
            for (index in offset until end) samples[index] *= gain
            offset = end
        }
    }

    private fun applyActiveAgc(samples: FloatArray, sampleRate: Int, noiseRms: Float) {
        val window = max(1, sampleRate * WINDOW_MS / 1_000)
        val openRms = max(MIN_SPEECH_RMS, noiseRms * GATE_OPEN_MULTIPLIER)
        var sumSquares = 0.0
        var activeSamples = 0
        var offset = 0
        while (offset < samples.size) {
            val end = min(offset + window, samples.size)
            if (rms(samples, offset, end) >= openRms * CLOSED_GAIN) {
                for (index in offset until end) {
                    sumSquares += samples[index] * samples[index]
                    activeSamples++
                }
            }
            offset = end
        }
        if (activeSamples == 0) return
        val activeRms = sqrt(sumSquares / activeSamples).toFloat()
        val gain = (TARGET_RMS / max(activeRms, 0.0001f)).coerceIn(1f, MAX_GAIN)
        samples.indices.forEach { samples[it] *= gain }
    }

    private fun compressAndLimit(samples: FloatArray) {
        for (index in samples.indices) {
            val sign = if (samples[index] < 0f) -1f else 1f
            var magnitude = abs(samples[index])
            if (magnitude > COMPRESS_THRESHOLD) {
                magnitude = COMPRESS_THRESHOLD +
                    (magnitude - COMPRESS_THRESHOLD) / COMPRESS_RATIO
            }
            samples[index] = sign * min(magnitude, LIMITER_CEILING)
        }
    }

    private fun lowPass(samples: FloatArray, sampleRate: Int) {
        val cutoff = min(6_800f, sampleRate * 0.42f)
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoff)
        val alpha = dt / (rc + dt)
        var previous = samples.first()
        for (index in samples.indices) {
            previous += alpha * (samples[index] - previous)
            samples[index] = previous
        }
    }

    private fun fadeEdges(samples: FloatArray, sampleRate: Int) {
        val fadeIn = min(samples.size, sampleRate * FADE_IN_MS / 1_000)
        val fadeOut = min(samples.size, sampleRate * FADE_OUT_MS / 1_000)
        for (index in 0 until fadeIn)
            samples[index] *= index.toFloat() / max(1, fadeIn)
        for (index in 0 until fadeOut)
            samples[samples.lastIndex - index] *= index.toFloat() / max(1, fadeOut)
    }

    private fun rms(samples: FloatArray, start: Int, end: Int): Float {
        if (end <= start) return 0f
        var sum = 0.0
        for (index in start until end) sum += samples[index] * samples[index]
        return sqrt(sum / (end - start)).toFloat()
    }

    private fun FloatArray.toPcm16leWithTail(sampleRate: Int): ByteArray {
        val tailSamples = sampleRate * TAIL_SILENCE_MS / 1_000
        val output = ByteArray((size + tailSamples) * 2)
        for (index in indices) {
            val sample = (this[index].coerceIn(-1f, 0.999969f) * 32768f).toInt().toShort()
            output[index * 2] = sample.toInt().toByte()
            output[index * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return output
    }

    private const val RELEASE_GUARD_MS = 120
    private const val WINDOW_MS = 20
    private const val NOISE_PERCENT = 0.20f
    private const val NOISE_FLOOR_RMS = 0.0015f
    private const val MIN_SPEECH_RMS = 0.007f
    private const val MIN_ACTIVE_WINDOWS = 2
    private const val GATE_CLOSE_MULTIPLIER = 1.6f
    private const val GATE_OPEN_MULTIPLIER = 2.8f
    private const val CLOSED_GAIN = 0.035f
    private const val GATE_SMOOTHING = 0.60f
    private const val HIGH_PASS_HZ = 110f
    private const val TARGET_RMS = 0.18f
    private const val MAX_GAIN = 6f
    private const val COMPRESS_THRESHOLD = 0.62f
    private const val COMPRESS_RATIO = 3.2f
    private const val LIMITER_CEILING = 0.95f
    private const val FADE_IN_MS = 8
    private const val FADE_OUT_MS = 35
    private const val TAIL_SILENCE_MS = 20
}
