package com.tji.device.product.speaker.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class SpeakerPttProcessorTest {
    @Test
    fun rejectsStationaryLowLevelNoise() {
        val pcm = synthesize { index ->
            if ((index and 1) == 0) 0.003f else -0.003f
        }

        assertFalse(SpeakerPttProcessor.analyze(pcm, SAMPLE_RATE).hasSpeech)
        assertTrue(SpeakerPttProcessor.process(pcm, SAMPLE_RATE).isEmpty())
    }

    @Test
    fun keepsSpeechAndAttenuatesQuietWindows() {
        val pcm = synthesize { index ->
            val time = index.toFloat() / SAMPLE_RATE
            val noise = if ((index and 1) == 0) 0.003f else -0.003f
            val speech = if (time in 0.30f..0.85f) {
                0.14f * sin(2f * PI.toFloat() * 440f * time)
            } else {
                0f
            }
            noise + speech
        }

        val analysis = SpeakerPttProcessor.analyze(pcm, SAMPLE_RATE)
        val processed = SpeakerPttProcessor.process(pcm, SAMPLE_RATE)

        assertTrue(analysis.hasSpeech)
        assertTrue(analysis.activeWindows >= 2)
        assertTrue(processed.isNotEmpty())
        assertTrue(rms(processed, 0, 200) < rms(pcm, 0, 200) * 0.45f)
        assertTrue(rms(processed, 400, 700) > 0.04f)
        assertTrue(processed.takeLast(SAMPLE_RATE / 50 * 2).all { it == 0.toByte() })
    }

    private fun synthesize(sample: (Int) -> Float): ByteArray {
        val samples = SAMPLE_RATE * 1_200 / 1_000
        return ByteArray(samples * 2).also { output ->
            repeat(samples) { index ->
                val value = (sample(index).coerceIn(-1f, 0.999f) * 32768f).toInt().toShort()
                output[index * 2] = value.toInt().toByte()
                output[index * 2 + 1] = (value.toInt() shr 8).toByte()
            }
        }
    }

    private fun rms(pcm: ByteArray, startMs: Int, endMs: Int): Float {
        val start = SAMPLE_RATE * startMs / 1_000
        val end = minOf(pcm.size / 2, SAMPLE_RATE * endMs / 1_000)
        var sum = 0.0
        for (index in start until end) {
            val offset = index * 2
            val sample = (
                (pcm[offset].toInt() and 0xff) or
                    (pcm[offset + 1].toInt() shl 8)
                ).toShort() / 32768f
            sum += sample * sample
        }
        return sqrt(sum / (end - start)).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
