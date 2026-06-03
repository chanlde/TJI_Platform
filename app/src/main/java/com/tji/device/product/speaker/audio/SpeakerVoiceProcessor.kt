package com.tji.device.product.speaker.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SpeakerVoiceProcessor {
    private var previousInput = 0f
    private var previousHighPass = 0f
    private var previousPresence = 0f
    private var previousAgcGain = 1f
    private val equalizer = SpeakerToneEqualizer()

    // Stateful processor for live talk: filter history is kept between 40 ms frames.
    fun processFrame(
        pcm16le: ByteArray,
        toneSettings: SpeakerToneSettings = SpeakerToneSettings()
    ): ByteArray =
        processPcm(pcm16le, stateful = true, profile = VoiceProfile.Live, toneSettings = toneSettings)

    // Stateless processor for recorded push-to-talk clips.
    fun processPcm(
        pcm16le: ByteArray,
        stateful: Boolean = false,
        toneSettings: SpeakerToneSettings = SpeakerToneSettings()
    ): ByteArray {
        return processPcm(pcm16le, stateful, VoiceProfile.PushToTalk, toneSettings)
    }

    private fun processPcm(
        pcm16le: ByteArray,
        stateful: Boolean,
        profile: VoiceProfile,
        toneSettings: SpeakerToneSettings
    ): ByteArray {
        val samples = pcm16le.toFloatSamples()
        if (samples.isEmpty()) return pcm16le

        val inputStats = samples.stats()

        // The speech chain is intentionally lightweight:
        // DC removal -> high-pass -> noise gate -> presence -> user EQ -> AGC/limiter -> live gate.
        removeDc(samples)
        highPass(samples, stateful)
        val pttNoiseGate = if (profile == VoiceProfile.PushToTalk) createPushToTalkNoiseGate(samples) else null
        pttNoiseGate?.applyTo(samples)
        addPresence(samples, stateful)
        applyToneEqualizer(samples, toneSettings, stateful)
        normalizeAndCompress(samples, stateful, profile)
        pttNoiseGate?.applyTo(samples)
        if (profile == VoiceProfile.Live) {
            applyLiveGate(samples, inputStats)
        }

        return samples.toPcm16le()
    }

    private fun removeDc(samples: FloatArray) {
        var sum = 0f
        for (sample in samples) sum += sample
        val mean = sum / samples.size
        for (i in samples.indices) {
            samples[i] -= mean
        }
    }

    private data class PushToTalkNoiseGate(
        val noiseRms: Float,
        val closeRms: Float,
        val openRms: Float,
        val windowSamples: Int
    ) {
        fun applyTo(samples: FloatArray) {
            var gain = SpeakerAudioConfig.Voice.PTT_NOISE_GATE_CLOSED_SCALE
            var offset = 0

            // Soft gate: reduce steady background noise without hard-cutting word starts.
            while (offset < samples.size) {
                val end = min(offset + windowSamples, samples.size)
                val rms = samples.windowRms(offset, end)
                val targetGain = when {
                    rms <= closeRms -> SpeakerAudioConfig.Voice.PTT_NOISE_GATE_CLOSED_SCALE
                    rms >= openRms -> 1f
                    else -> {
                        val range = (openRms - closeRms).coerceAtLeast(Float.MIN_VALUE)
                        val position = ((rms - closeRms) / range).coerceIn(0f, 1f)
                        SpeakerAudioConfig.Voice.PTT_NOISE_GATE_CLOSED_SCALE +
                            position * (1f - SpeakerAudioConfig.Voice.PTT_NOISE_GATE_CLOSED_SCALE)
                    }
                }
                gain += (targetGain - gain) * SpeakerAudioConfig.Voice.PTT_NOISE_GATE_SMOOTHING
                for (i in offset until end) {
                    samples[i] *= gain
                }
                offset = end
            }
        }

    }

    private fun createPushToTalkNoiseGate(samples: FloatArray): PushToTalkNoiseGate? {
        val windowSamples = msToSamples(SpeakerAudioConfig.Voice.PTT_NOISE_GATE_WINDOW_MS)
            .coerceAtLeast(1)
        val noiseRms = estimatePushToTalkNoiseRms(samples)
        val openRms = noiseRms * SpeakerAudioConfig.Voice.PTT_NOISE_GATE_OPEN_MULTIPLIER
        if (samples.maxWindowRms(windowSamples) < openRms) return null
        return PushToTalkNoiseGate(
            noiseRms = noiseRms,
            closeRms = noiseRms * SpeakerAudioConfig.Voice.PTT_NOISE_GATE_CLOSE_MULTIPLIER,
            openRms = openRms,
            windowSamples = windowSamples
        )
    }

    private fun FloatArray.maxWindowRms(windowSamples: Int): Float {
        var maxRms = 0f
        var offset = 0
        while (offset < size) {
            val end = min(offset + windowSamples, size)
            maxRms = maxOf(maxRms, windowRms(offset, end))
            offset = end
        }
        return maxRms
    }

    private fun estimatePushToTalkNoiseRms(samples: FloatArray): Float {
        val windowSamples = msToSamples(SpeakerAudioConfig.Voice.PTT_NOISE_GATE_WINDOW_MS)
            .coerceAtLeast(1)
        val windowRmsValues = ArrayList<Float>()
        var offset = 0
        while (offset < samples.size) {
            val end = min(offset + windowSamples, samples.size)
            windowRmsValues.add(samples.windowRms(offset, end))
            offset = end
        }
        if (windowRmsValues.isEmpty()) return SpeakerAudioConfig.Voice.PTT_NOISE_FLOOR_RMS

        windowRmsValues.sort()
        val lowWindowCount = (windowRmsValues.size * SpeakerAudioConfig.Voice.PTT_NOISE_LOW_PERCENT)
            .toInt()
            .coerceAtLeast(1)
        var sum = 0f
        for (i in 0 until lowWindowCount) {
            sum += windowRmsValues[i]
        }
        val measured = sum / lowWindowCount
        return maxOf(measured, SpeakerAudioConfig.Voice.PTT_NOISE_FLOOR_RMS)
    }

    private fun highPass(samples: FloatArray, stateful: Boolean) {
        val rc = 1f / (TWO_PI * SpeakerAudioConfig.Voice.HIGH_PASS_CUTOFF_HZ)
        val dt = 1f / SpeakerAdpcmPacketizer.SAMPLE_RATE
        val alpha = rc / (rc + dt)
        var lastInput = if (stateful) previousInput else 0f
        var lastOutput = if (stateful) previousHighPass else 0f
        for (i in samples.indices) {
            val input = samples[i]
            val output = alpha * (lastOutput + input - lastInput)
            samples[i] = output
            lastInput = input
            lastOutput = output
        }
        if (stateful) {
            previousInput = lastInput
            previousHighPass = lastOutput
        }
    }

    private fun addPresence(samples: FloatArray, stateful: Boolean) {
        var previous = if (stateful) previousPresence else 0f
        for (i in samples.indices) {
            val current = samples[i]
            val edge = current - previous
            samples[i] = (current + edge * SpeakerAudioConfig.Voice.PRESENCE_EDGE_GAIN)
                .coerceIn(SpeakerAudioConfig.Pcm.PCM_FLOAT_MIN, SpeakerAudioConfig.Pcm.PCM_FLOAT_MAX)
            previous = current
        }
        if (stateful) {
            previousPresence = previous
        }
    }

    private fun applyToneEqualizer(samples: FloatArray, toneSettings: SpeakerToneSettings, stateful: Boolean) {
        val settings = toneSettings.normalized()
        if (settings.bassDb == 0f && settings.trebleDb == 0f) return
        if (stateful) {
            equalizer.applyTo(samples, settings)
        } else {
            SpeakerToneEqualizer.applyStateless(samples, settings)
        }
    }

    private fun normalizeAndCompress(samples: FloatArray, stateful: Boolean, profile: VoiceProfile) {
        var peak = 0f
        var sumSq = 0f
        for (sample in samples) {
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            sumSq += sample * sample
        }
        val rms = sqrt(sumSq / samples.size)
        val params = profile.params
        if (rms < params.minActiveRms || peak < params.minActivePeak) return

        val targetGain = min(params.maxGain, params.targetRms / rms)
        val gain = if (stateful && profile == VoiceProfile.Live) {
            smoothLiveAgcGain(targetGain)
        } else {
            targetGain
        }
        for (i in samples.indices) {
            var x = samples[i] * gain
            val sign = if (x < 0f) -1f else 1f
            var magnitude = abs(x)
            if (magnitude > SpeakerAudioConfig.Voice.COMPRESS_THRESHOLD) {
                magnitude = SpeakerAudioConfig.Voice.COMPRESS_THRESHOLD +
                    (magnitude - SpeakerAudioConfig.Voice.COMPRESS_THRESHOLD) /
                    SpeakerAudioConfig.Voice.COMPRESS_RATIO
            }
            magnitude = min(magnitude, SpeakerAudioConfig.Voice.LIMITER_CEILING)
            samples[i] = sign * magnitude
        }
    }

    private fun smoothLiveAgcGain(targetGain: Float): Float {
        val smoothing = if (targetGain > previousAgcGain) {
            SpeakerAudioConfig.Voice.LIVE_AGC_GAIN_UP_SMOOTHING
        } else {
            SpeakerAudioConfig.Voice.LIVE_AGC_GAIN_DOWN_SMOOTHING
        }
        previousAgcGain += (targetGain - previousAgcGain) * smoothing
        return previousAgcGain.coerceIn(0f, SpeakerAudioConfig.Voice.LIVE_MAX_GAIN)
    }

    private fun applyLiveGate(samples: FloatArray, inputStats: SpeakerPcmStats) {
        val scale = when {
            inputStats.rms < SpeakerAudioConfig.Voice.LIVE_GATE_MUTE_RMS &&
                inputStats.peak < SpeakerAudioConfig.Voice.LIVE_GATE_MUTE_PEAK -> 0f

            inputStats.rms < SpeakerAudioConfig.Voice.LIVE_GATE_OPEN_RMS &&
                inputStats.peak < SpeakerAudioConfig.Voice.LIVE_GATE_OPEN_PEAK -> {
                val rmsScale = (
                    (inputStats.rms - SpeakerAudioConfig.Voice.LIVE_GATE_MUTE_RMS) /
                        (SpeakerAudioConfig.Voice.LIVE_GATE_OPEN_RMS - SpeakerAudioConfig.Voice.LIVE_GATE_MUTE_RMS)
                    )
                    .coerceIn(0f, 1f)
                (SpeakerAudioConfig.Voice.LIVE_GATE_LOW_ACTIVITY_SCALE +
                    rmsScale * (1f - SpeakerAudioConfig.Voice.LIVE_GATE_LOW_ACTIVITY_SCALE))
                    .coerceIn(0f, 1f)
            }

            else -> 1f
        }
        if (scale >= 1f) return
        for (i in samples.indices) {
            samples[i] *= scale
        }
    }

    private fun FloatArray.stats(): SpeakerPcmStats {
        var peak = 0f
        var sumSq = 0f
        for (sample in this) {
            peak = maxOf(peak, abs(sample))
            sumSq += sample * sample
        }
        return SpeakerPcmStats(
            rms = if (isEmpty()) 0f else sqrt(sumSq / size),
            peak = peak,
            samples = size
        )
    }

    private fun msToSamples(milliseconds: Int): Int =
        SpeakerAdpcmPacketizer.SAMPLE_RATE * milliseconds / MILLIS_PER_SECOND

    companion object {
        private const val TWO_PI = 2f * PI.toFloat()
        private const val MILLIS_PER_SECOND = 1_000

        fun processPushToTalk(
            pcm16le: ByteArray,
            toneSettings: SpeakerToneSettings = SpeakerToneSettings()
        ): ByteArray =
            SpeakerVoiceProcessor().processPcm(pcm16le, toneSettings = toneSettings)

        fun applyPlaybackTone(
            pcm16le: ByteArray,
            toneSettings: SpeakerToneSettings = SpeakerToneSettings()
        ): ByteArray {
            return SpeakerVoiceProcessor().processPcm(
                pcm16le = pcm16le,
                stateful = false,
                profile = VoiceProfile.Playback,
                toneSettings = toneSettings
            )
        }

        fun applyToneOnly(
            pcm16le: ByteArray,
            toneSettings: SpeakerToneSettings = SpeakerToneSettings()
        ): ByteArray {
            val samples = pcm16le.toFloatSamples()
            if (samples.isEmpty()) return pcm16le
            SpeakerToneEqualizer.applyStateless(samples, toneSettings.normalized())
            return samples.toPcm16le()
        }

        fun measurePcm(pcm16le: ByteArray): SpeakerPcmStats {
            var peak = 0f
            var sumSq = 0f
            var count = 0
            var offset = 0
            while (offset + 1 < pcm16le.size) {
                val value = ((pcm16le[offset].toInt() and 0xFF) or (pcm16le[offset + 1].toInt() shl 8))
                    .toShort()
                    .toInt()
                val normalized = value / SpeakerAudioConfig.Pcm.PCM_I16_NEGATIVE_SCALE
                peak = maxOf(peak, abs(normalized))
                sumSq += normalized * normalized
                count += 1
                offset += 2
            }
            return SpeakerPcmStats(
                rms = if (count == 0) 0f else sqrt(sumSq / count),
                peak = peak,
                samples = count
            )
        }
    }
}

private data class VoiceProcessingParams(
    val targetRms: Float,
    val maxGain: Float,
    val minActiveRms: Float,
    val minActivePeak: Float
)

private enum class VoiceProfile(val params: VoiceProcessingParams) {
    Live(
        VoiceProcessingParams(
            targetRms = SpeakerAudioConfig.Voice.LIVE_TARGET_RMS,
            maxGain = SpeakerAudioConfig.Voice.LIVE_MAX_GAIN,
            minActiveRms = SpeakerAudioConfig.Voice.LIVE_MIN_ACTIVE_RMS,
            minActivePeak = SpeakerAudioConfig.Voice.LIVE_MIN_ACTIVE_PEAK
        )
    ),
    PushToTalk(
        VoiceProcessingParams(
            targetRms = SpeakerAudioConfig.Voice.PTT_TARGET_RMS,
            maxGain = SpeakerAudioConfig.Voice.PTT_MAX_GAIN,
            minActiveRms = SpeakerAudioConfig.Voice.PTT_MIN_ACTIVE_RMS,
            minActivePeak = SpeakerAudioConfig.Voice.PTT_MIN_ACTIVE_PEAK
        )
    ),
    Playback(
        VoiceProcessingParams(
            targetRms = SpeakerAudioConfig.Voice.TTS_TARGET_RMS,
            maxGain = SpeakerAudioConfig.Voice.TTS_MAX_GAIN,
            minActiveRms = SpeakerAudioConfig.Voice.TTS_MIN_ACTIVE_RMS,
            minActivePeak = SpeakerAudioConfig.Voice.TTS_MIN_ACTIVE_PEAK
        )
    )
}

data class SpeakerPcmStats(
    val rms: Float,
    val peak: Float,
    val samples: Int
)

class SpeakerToneEqualizer {
    private val bassShelf = BiquadFilter()
    private val trebleShelf = BiquadFilter()

    fun applyTo(samples: FloatArray, settings: SpeakerToneSettings) {
        val normalized = settings.normalized()
        if (normalized.bassDb != 0f) {
            bassShelf.configureLowShelf(
                frequencyHz = SpeakerAudioConfig.Equalizer.BASS_SHELF_HZ,
                gainDb = normalized.bassDb
            )
            bassShelf.processInPlace(samples)
        }
        if (normalized.trebleDb != 0f) {
            trebleShelf.configureHighShelf(
                frequencyHz = SpeakerAudioConfig.Equalizer.TREBLE_SHELF_HZ,
                gainDb = normalized.trebleDb
            )
            trebleShelf.processInPlace(samples)
        }
        for (i in samples.indices) {
            samples[i] = samples[i].coerceIn(
                SpeakerAudioConfig.Pcm.PCM_FLOAT_MIN,
                SpeakerAudioConfig.Pcm.PCM_FLOAT_MAX
            )
        }
    }

    companion object {
        fun applyStateless(samples: FloatArray, settings: SpeakerToneSettings) {
            SpeakerToneEqualizer().applyTo(samples, settings)
        }
    }
}

private class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun configureLowShelf(frequencyHz: Float, gainDb: Float) {
        configureShelf(frequencyHz, gainDb, lowShelf = true)
    }

    fun configureHighShelf(frequencyHz: Float, gainDb: Float) {
        configureShelf(frequencyHz, gainDb, lowShelf = false)
    }

    fun processInPlace(samples: FloatArray) {
        for (i in samples.indices) {
            val input = samples[i]
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            samples[i] = output
        }
    }

    private fun configureShelf(frequencyHz: Float, gainDb: Float, lowShelf: Boolean) {
        val clampedFrequency = frequencyHz.coerceIn(20f, SpeakerAdpcmPacketizer.SAMPLE_RATE / 2f - 100f)
        val a = 10.0.pow(gainDb.toDouble() / 40.0)
        val omega = 2.0 * PI * clampedFrequency.toDouble() / SpeakerAdpcmPacketizer.SAMPLE_RATE.toDouble()
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val sqrtA = sqrt(a)
        val slope = SpeakerAudioConfig.Equalizer.SHELF_SLOPE.coerceAtLeast(0.1f).toDouble()
        val alpha = sinOmega / 2.0 * sqrt(((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0).coerceAtLeast(0.0))

        val raw = if (lowShelf) {
            ShelfCoefficients(
                b0 = a * ((a + 1.0) - (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha),
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega),
                b2 = a * ((a + 1.0) - (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha),
                a0 = (a + 1.0) + (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha,
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosOmega),
                a2 = (a + 1.0) + (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha
            )
        } else {
            ShelfCoefficients(
                b0 = a * ((a + 1.0) + (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha),
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega),
                b2 = a * ((a + 1.0) + (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha),
                a0 = (a + 1.0) - (a - 1.0) * cosOmega + 2.0 * sqrtA * alpha,
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosOmega),
                a2 = (a + 1.0) - (a - 1.0) * cosOmega - 2.0 * sqrtA * alpha
            )
        }

        b0 = (raw.b0 / raw.a0).toFloat()
        b1 = (raw.b1 / raw.a0).toFloat()
        b2 = (raw.b2 / raw.a0).toFloat()
        a1 = (raw.a1 / raw.a0).toFloat()
        a2 = (raw.a2 / raw.a0).toFloat()
    }
}

private data class ShelfCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a0: Double,
    val a1: Double,
    val a2: Double
)

private fun ByteArray.toFloatSamples(): FloatArray {
    val sampleCount = size / 2
    val samples = FloatArray(sampleCount)
    var offset = 0
    for (i in 0 until sampleCount) {
        val value = ((this[offset].toInt() and 0xFF) or (this[offset + 1].toInt() shl 8))
            .toShort()
            .toInt()
        samples[i] = value / SpeakerAudioConfig.Pcm.PCM_I16_NEGATIVE_SCALE
        offset += 2
    }
    return samples
}

private fun FloatArray.toPcm16le(): ByteArray {
    val pcm = ByteArray(size * 2)
    for (i in indices) {
        val clamped = this[i].coerceIn(
            SpeakerAudioConfig.Pcm.PCM_FLOAT_MIN,
            SpeakerAudioConfig.Pcm.PCM_FLOAT_MAX
        )
        val value = if (clamped < 0f) {
            (clamped * SpeakerAudioConfig.Pcm.PCM_I16_NEGATIVE_SCALE).toInt()
        } else {
            (clamped * SpeakerAudioConfig.Pcm.PCM_I16_POSITIVE_SCALE).toInt()
        }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[i * 2] = (value and 0xFF).toByte()
        pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return pcm
}

private fun FloatArray.windowRms(start: Int, end: Int): Float {
    var sumSq = 0f
    var count = 0
    for (i in start until end) {
        sumSq += this[i] * this[i]
        count += 1
    }
    return if (count == 0) 0f else sqrt(sumSq / count)
}
