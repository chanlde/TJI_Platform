package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerHadpFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerCoreNativeTest {
    @Test
    fun nativeWrapperReturnsUnavailableOnJvmUnitTests() {
        val pcm = ByteArray(SpeakerAdpcmPacketizer.PCM_FRAME_BYTES)

        assertFalse(SpeakerCoreNative.isAvailable())
        assertNull(
            SpeakerCoreNative.encodeHadpOrNull(
                pcm16le = pcm,
                recordId = "REC_NATIVE_JVM_TEST",
                codec = SpeakerHadpCodec.Pcm16
            )
        )
    }

    @Test
    fun shadowVerifierDoesNotFailWhenNativeLibraryIsUnavailable() {
        val kotlinHadp = SpeakerHadpFile(
            data = byteArrayOf(1, 2, 3),
            codec = SpeakerHadpCodec.Pcm16,
            sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE,
            channels = SpeakerAdpcmPacketizer.CHANNELS,
            packetMs = SpeakerAdpcmPacketizer.PACKET_MS,
            frameBytes = SpeakerHadpCodec.Pcm16.frameBytes,
            samplesPerFrame = SpeakerHadpCodec.Pcm16.samplesPerFrame,
            fileSize = 3,
            crc32 = "0x00000000",
            durationMs = 40,
            frameCount = 1,
            audioBytes = 3,
            audioCrc32 = "0x00000000"
        )

        val result = SpeakerCoreShadowVerifier.compareHadp(
            kotlinHadp = kotlinHadp,
            pcm16le = ByteArray(SpeakerAdpcmPacketizer.PCM_FRAME_BYTES),
            recordId = "REC_NATIVE_JVM_TEST"
        )

        assertTrue(result is SpeakerCoreShadowResult.NativeUnavailable)
    }
}
