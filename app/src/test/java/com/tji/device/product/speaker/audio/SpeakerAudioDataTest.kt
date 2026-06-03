package com.tji.device.product.speaker.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class SpeakerAudioDataTest {
    @Test
    fun voiceProcessorBoostsQuietVoiceInsteadOfReducingIt() {
        val input = syntheticVoicePcm(amplitude = 0.004f, sampleCount = 1_600)
        val output = SpeakerVoiceProcessor.processPushToTalk(input)

        val inputStats = stats(input)
        val outputStats = stats(output)
        println(
            "quiet voice: input rms=${inputStats.rms} peak=${inputStats.peak}, " +
                "processed rms=${outputStats.rms} peak=${outputStats.peak}"
        )

        assertTrue("input should represent a quiet but valid voice sample", inputStats.rms > 0.001f)
        assertTrue("processed voice RMS should be boosted", outputStats.rms > inputStats.rms * 5f)
        assertTrue("processed voice peak should be boosted", outputStats.peak > inputStats.peak * 5f)
        assertTrue("processed voice must stay below digital clipping", outputStats.peak <= 0.981f)
    }

    @Test
    fun voiceProcessorKeepsSilenceSilent() {
        val silence = ByteArray(SpeakerAdpcmPacketizer.PCM_FRAME_BYTES)
        val output = SpeakerVoiceProcessor.processPushToTalk(silence)

        assertEquals(0f, stats(output).rms, 0.000001f)
        assertEquals(0f, stats(output).peak, 0.000001f)
    }

    @Test
    fun pushToTalkReducesBackgroundNoiseBeforeBoostingSpeech() {
        val input = syntheticNoiseThenVoicePcm(
            noiseAmplitude = 0.003f,
            voiceAmplitude = 0.030f,
            noiseSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE / 2,
            voiceSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val output = SpeakerVoiceProcessor.processPushToTalk(input)

        val inputNoise = stats(input.copyOfRange(0, SpeakerAdpcmPacketizer.SAMPLE_RATE))
        val outputNoise = stats(output.copyOfRange(0, SpeakerAdpcmPacketizer.SAMPLE_RATE))
        val inputVoice = stats(input.copyOfRange(SpeakerAdpcmPacketizer.SAMPLE_RATE, input.size))
        val outputVoice = stats(output.copyOfRange(SpeakerAdpcmPacketizer.SAMPLE_RATE, output.size))

        println(
            "ptt denoise: noise ${inputNoise.rms}->${outputNoise.rms}, " +
                "voice ${inputVoice.rms}->${outputVoice.rms}"
        )

        assertTrue("PTT gate should reduce background noise before AGC", outputNoise.rms < inputNoise.rms)
        assertTrue("PTT speech should still be boosted clearly", outputVoice.rms > inputVoice.rms * 2f)
    }

    @Test
    fun ttsPlaybackIsNormalizedCloseToPushToTalkLevel() {
        val input = syntheticVoicePcm(amplitude = 0.006f, sampleCount = 1_600)
        val output = SpeakerVoiceProcessor.applyPlaybackTone(input)

        val inputStats = stats(input)
        val outputStats = stats(output)

        println(
            "tts normalize: input rms=${inputStats.rms} peak=${inputStats.peak}, " +
                "processed rms=${outputStats.rms} peak=${outputStats.peak}"
        )

        assertTrue("TTS playback should be boosted when source audio is quiet", outputStats.rms > inputStats.rms * 5f)
        assertTrue("TTS playback should be loud enough to match PTT perceptually", outputStats.rms > 0.075f)
        assertTrue("TTS playback must stay below digital clipping", outputStats.peak <= 0.981f)
    }

    @Test
    fun liveProcessorDoesNotExplodeQuietBackgroundNoise() {
        val input = syntheticVoicePcm(amplitude = 0.004f)
        val output = SpeakerVoiceProcessor().processFrame(input)

        val inputStats = stats(input)
        val outputStats = stats(output)

        println(
            "live quiet noise: input rms=${inputStats.rms} peak=${inputStats.peak}, " +
                "processed rms=${outputStats.rms} peak=${outputStats.peak}"
        )
        assertTrue("live mode should not amplify low-level background into a blast", outputStats.rms <= inputStats.rms)
    }

    @Test
    fun adpcmPacketHeaderMatchesHydroLinkUdpProtocol() {
        val packetizer = SpeakerAdpcmPacketizer()
        val packet = packetizer.packetize(syntheticVoicePcm(amplitude = 0.08f))!!
        val header = ByteBuffer.wrap(packet, 0, 20).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0xA55A, header.short.toInt() and 0xFFFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(0, header.int)
        assertEquals(0, header.int)
        assertEquals(8_000, header.short.toInt() and 0xFFFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(0, header.get().toInt() and 0xFF)
        assertEquals(164, header.short.toInt() and 0xFFFF)
        assertEquals(320, header.short.toInt() and 0xFFFF)
        assertEquals(184, packet.size)
        println(
            "adpcm packet: total=${packet.size}, payload=164, samples=320, " +
                "magic=0xA55A, codec=1, sampleRate=8000"
        )

        val nextPacket = packetizer.packetize(syntheticVoicePcm(amplitude = 0.08f))!!
        val nextHeader = ByteBuffer.wrap(nextPacket, 0, 20).order(ByteOrder.LITTLE_ENDIAN)
        nextHeader.position(4)
        assertEquals(1, nextHeader.int)
        assertEquals(320, nextHeader.int)
    }

    @Test
    fun maxOutputGainControlPacketMatchesHydroLinkUdpProtocol() = runBlocking {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
            receiver.soTimeout = 1_000
            val relay = SpeakerAudioRelay(
                SpeakerRelayConfig(
                    host = "127.0.0.1",
                    port = receiver.localPort
                )
            )

            relay.sendOutputGain(1f)

            val bytes = ByteArray(8)
            val packet = DatagramPacket(bytes, bytes.size)
            receiver.receive(packet)
            val body = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals(8, packet.length)
            assertEquals(0xA55B, body.short.toInt() and 0xFFFF)
            assertEquals(1, body.get().toInt() and 0xFF)
            assertEquals(6, body.get().toInt() and 0xFF)
            assertEquals(256, body.short.toInt() and 0xFFFF)
            assertEquals(0, body.short.toInt() and 0xFFFF)
        }
    }

    private fun syntheticVoicePcm(
        amplitude: Float,
        sampleCount: Int = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / 2
    ): ByteArray {
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val t = i.toFloat() / SpeakerAdpcmPacketizer.SAMPLE_RATE
            val sample = amplitude * (
                sin(2f * PI.toFloat() * 420f * t) * 0.65f +
                    sin(2f * PI.toFloat() * 1_250f * t) * 0.35f
                )
            val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun syntheticNoiseThenVoicePcm(
        noiseAmplitude: Float,
        voiceAmplitude: Float,
        noiseSamples: Int,
        voiceSamples: Int
    ): ByteArray {
        val pcm = ByteArray((noiseSamples + voiceSamples) * 2)
        for (i in 0 until noiseSamples + voiceSamples) {
            val t = i.toFloat() / SpeakerAdpcmPacketizer.SAMPLE_RATE
            val noise = noiseAmplitude * sin(2f * PI.toFloat() * 170f * t)
            val voice = if (i >= noiseSamples) {
                voiceAmplitude * (
                    sin(2f * PI.toFloat() * 420f * t) * 0.65f +
                        sin(2f * PI.toFloat() * 1_250f * t) * 0.35f
                    )
            } else {
                0f
            }
            val value = ((noise + voice).coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun stats(pcm: ByteArray): AudioStats {
        var peak = 0f
        var sumSq = 0f
        var count = 0
        var offset = 0
        while (offset + 1 < pcm.size) {
            val value = ((pcm[offset].toInt() and 0xFF) or (pcm[offset + 1].toInt() shl 8))
                .toShort()
                .toInt()
            val normalized = value / 32768f
            peak = maxOf(peak, abs(normalized))
            sumSq += normalized * normalized
            count += 1
            offset += 2
        }
        return AudioStats(
            rms = if (count == 0) 0f else sqrt(sumSq / count),
            peak = peak
        )
    }

    private data class AudioStats(
        val rms: Float,
        val peak: Float
    )
}
