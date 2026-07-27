package com.tji.device.product.speaker.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerMicrophoneFormatTest {
    @Test
    fun qualityControlsCaptureFrameAndMcuCompatibleHadpCodec() {
        val expected = listOf(
            Triple(SpeakerAudioQuality.Low, 640, SpeakerHadpCodec.ImaAdpcm),
            Triple(SpeakerAudioQuality.Medium, 1_280, SpeakerHadpCodec.Pcm16),
            Triple(SpeakerAudioQuality.High, 1_920, SpeakerHadpCodec.Pcm16)
        )

        expected.forEach { (quality, frameBytes, codec) ->
            assertEquals(frameBytes, SpeakerMicrophoneFormat.frameBytes(quality.sampleRate))
            assertEquals(codec, SpeakerMicrophoneFormat.hadpCodec(quality))
        }
    }
}
