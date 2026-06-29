package com.tji.device.product.speaker.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
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
    fun pushToTalkSpeechDetectorRejectsSilenceAndSteadyNoise() {
        val silence = ByteArray(SpeakerAdpcmPacketizer.SAMPLE_RATE * 2)
        val steadyNoise = syntheticVoicePcm(
            amplitude = 0.006f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )

        assertTrue(!SpeakerVoiceProcessor.hasPushToTalkSpeech(silence))
        assertTrue(!SpeakerVoiceProcessor.hasPushToTalkSpeech(steadyNoise))
    }

    @Test
    fun pushToTalkSpeechDetectorAcceptsSpeechOverNoise() {
        val input = syntheticNoiseThenVoicePcm(
            noiseAmplitude = 0.003f,
            voiceAmplitude = 0.050f,
            noiseSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE / 2,
            voiceSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )

        assertTrue(SpeakerVoiceProcessor.hasPushToTalkSpeech(input))
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
    fun pushToTalkTailFadesToSilenceToAvoidEndPop() {
        val input = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val output = SpeakerVoiceProcessor.processPushToTalk(input)
        val trailingSilenceBytes = SpeakerAdpcmPacketizer.SAMPLE_RATE *
            SpeakerAudioConfig.Voice.PTT_TRAILING_SILENCE_MS /
            1_000 *
            2
        val outputTail = output.takeLast(trailingSilenceBytes).toByteArray()
        val outputBody = output.dropLast(trailingSilenceBytes).toByteArray()

        assertTrue("PTT output should include trailing settle silence", output.size >= trailingSilenceBytes)
        assertEquals(0f, stats(outputTail).peak, 0.000001f)
        assertTrue("PTT voice body should still be audible", stats(outputBody).rms > 0.02f)
        assertEquals(0f, stats(outputBody.takeLast(2).toByteArray()).peak, 0.000001f)
    }

    @Test
    fun pushToTalkDropsReleaseTailAndLongPostSpeechSilence() {
        val voice = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val longSilence = ByteArray(SpeakerAdpcmPacketizer.SAMPLE_RATE * 2 * 2)
        val releaseClick = syntheticVoicePcm(
            amplitude = 0.20f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE *
                SpeakerAudioConfig.Voice.PTT_RELEASE_GUARD_MS /
                1_000
        )
        val input = voice + longSilence + releaseClick
        val output = SpeakerVoiceProcessor.processPushToTalk(input)
        val trailingSilenceBytes = SpeakerAdpcmPacketizer.SAMPLE_RATE *
            SpeakerAudioConfig.Voice.PTT_TRAILING_SILENCE_MS /
            1_000 *
            2
        val body = output.dropLast(trailingSilenceBytes).toByteArray()

        assertTrue("PTT release tail should be removed before processing", body.size < input.size / 2)
        assertTrue("PTT should keep the actual spoken part", stats(body).rms > 0.02f)
        assertEquals(0f, stats(output.takeLast(trailingSilenceBytes).toByteArray()).peak, 0.000001f)
    }


    @Test
    fun ttsPlaybackIsNormalizedCloseToPushToTalkLevel() {
        val input = syntheticVoicePcm(amplitude = 0.006f, sampleCount = 1_600)
        val output = SpeakerVoiceProcessor.applyPlaybackTone(input)

        val inputStats = stats(input)
        val trailingSilenceBytes = SpeakerAdpcmPacketizer.SAMPLE_RATE *
            SpeakerAudioConfig.Voice.TTS_TRAILING_SILENCE_MS /
            1_000 *
            2
        val outputBody = output.dropLast(trailingSilenceBytes).toByteArray()
        val outputStats = stats(outputBody)

        println(
            "tts normalize: input rms=${inputStats.rms} peak=${inputStats.peak}, " +
                "processed rms=${outputStats.rms} peak=${outputStats.peak}"
        )

        assertTrue("TTS playback should be boosted when source audio is quiet", outputStats.rms > inputStats.rms * 3f)
        assertTrue("TTS playback should remain audible without aggressive first-word boosting", outputStats.rms > 0.012f)
        assertTrue("TTS playback must stay below digital clipping", outputStats.peak <= 0.981f)
    }

    @Test
    fun ttsPlaybackAddsEdgeSmoothingAndTrailingSilence() {
        val input = syntheticVoicePcm(amplitude = 0.08f, sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE)
        val output = SpeakerVoiceProcessor.applyPlaybackTone(input)
        val trailingSilenceBytes = SpeakerAdpcmPacketizer.SAMPLE_RATE *
            SpeakerAudioConfig.Voice.TTS_TRAILING_SILENCE_MS /
            1_000 *
            2
        val body = output.dropLast(trailingSilenceBytes).toByteArray()
        val tail = output.takeLast(trailingSilenceBytes).toByteArray()

        assertTrue("TTS output should include trailing settle silence", output.size > input.size)
        assertEquals(0f, stats(body.take(2).toByteArray()).peak, 0.000001f)
        assertEquals(0f, stats(body.takeLast(2).toByteArray()).peak, 0.000001f)
        assertEquals(0f, stats(tail).peak, 0.000001f)
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
    fun pushToTalkOfflineNoiseDiagnostics() {
        val cases = listOf(
            "silence" to ByteArray(SpeakerAdpcmPacketizer.SAMPLE_RATE * 2),
            "low_hum" to syntheticHumPcm(
                amplitude = 0.004f,
                sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
            ),
            "low_noise_with_click" to (
                syntheticHumPcm(amplitude = 0.003f, sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE / 2) +
                    syntheticClickPcm(amplitude = 0.16f, sampleCount = 80) +
                    syntheticHumPcm(amplitude = 0.003f, sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE / 2)
                ),
            "speech_over_noise" to syntheticNoiseThenVoicePcm(
                noiseAmplitude = 0.003f,
                voiceAmplitude = 0.050f,
                noiseSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE / 2,
                voiceSamples = SpeakerAdpcmPacketizer.SAMPLE_RATE
            )
        )

        cases.forEach { (name, raw) ->
            val hasSpeech = SpeakerVoiceProcessor.hasPushToTalkSpeech(raw)
            val processed = SpeakerVoiceProcessor.processPushToTalk(raw)
            val roundTrip = adpcmRoundTrip(processed)
            val rawMetrics = metrics(raw)
            val processedMetrics = metrics(processed)
            val roundTripMetrics = metrics(roundTrip)
            val snr = snrDb(processed, roundTrip)

            println(
                "ptt diagnostic[$name] hasSpeech=$hasSpeech " +
                    "raw=${rawMetrics.compact()} processed=${processedMetrics.compact()} " +
                    "adpcm=${roundTripMetrics.compact()} snrDb=$snr"
            )

            assertTrue("processed audio must not clip for $name", processedMetrics.clippingRatio <= 0.0001f)
            assertTrue("ADPCM roundtrip must not clip for $name", roundTripMetrics.clippingRatio <= 0.0001f)
            assertTrue("frame boundary jumps should stay bounded for $name", processedMetrics.maxFrameJump <= 1.2f)
        }
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
    fun recordStorePacketHeaderCarriesDeviceTaskAndLastPacket() {
        val packetizer = SpeakerAdpcmPacketizer(
            SpeakerUdpStreamContext(
                deviceId = "T12345678",
                taskId = "STORE_T12345678_1",
                type = SpeakerUdpStreamType.RecordStore,
                talkId = "REC_T12345678_1"
            )
        )
        val packet = packetizer.packetize(syntheticVoicePcm(amplitude = 0.08f), isLastPacket = true)!!
        val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val deviceId = "T12345678"
        val taskId = "STORE_T12345678_1"
        val talkId = "REC_T12345678_1"
        val expectedHeaderLen = 28 + deviceId.length + taskId.length + talkId.length

        assertEquals(0xA55A, header.short.toInt() and 0xFFFF)
        assertEquals(2, header.get().toInt() and 0xFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(expectedHeaderLen, header.short.toInt() and 0xFFFF)
        assertEquals(0x03, header.short.toInt() and 0xFFFF)
        assertEquals(0, header.int)
        assertEquals(0, header.int)
        assertEquals(8_000, header.short.toInt() and 0xFFFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(40, header.get().toInt() and 0xFF)
        assertEquals(164, header.short.toInt() and 0xFFFF)
        assertEquals(320, header.short.toInt() and 0xFFFF)
        val deviceLen = header.get().toInt() and 0xFF
        val taskLen = header.get().toInt() and 0xFF
        val talkLen = header.get().toInt() and 0xFF
        assertEquals(deviceId.length, deviceLen)
        assertEquals(taskId.length, taskLen)
        assertEquals(talkId.length, talkLen)
        assertEquals(0, header.get().toInt() and 0xFF)
        val deviceBytes = ByteArray(deviceLen)
        header.get(deviceBytes)
        val taskBytes = ByteArray(taskLen)
        header.get(taskBytes)
        val talkBytes = ByteArray(talkLen)
        header.get(talkBytes)

        assertEquals(deviceId, deviceBytes.toString(Charsets.UTF_8))
        assertEquals(taskId, taskBytes.toString(Charsets.UTF_8))
        assertEquals(talkId, talkBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun liveTalkPacketHeaderCarriesSessionAndTalkIds() {
        val packetizer = SpeakerAdpcmPacketizer(
            SpeakerUdpStreamContext(
                deviceId = "T12345678",
                taskId = "LIVE_T12345678_ABCD",
                type = SpeakerUdpStreamType.LiveTalk,
                talkId = "TALK_T12345678_ABCD"
            )
        )
        val packet = packetizer.packetize(syntheticVoicePcm(amplitude = 0.08f))!!
        val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val deviceId = "T12345678"
        val sessionId = "LIVE_T12345678_ABCD"
        val talkId = "TALK_T12345678_ABCD"
        val expectedHeaderLen = 28 + deviceId.length + sessionId.length + talkId.length

        assertEquals(0xA55A, header.short.toInt() and 0xFFFF)
        assertEquals(2, header.get().toInt() and 0xFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(expectedHeaderLen, header.short.toInt() and 0xFFFF)
        assertEquals(0x04, header.short.toInt() and 0xFFFF)
        assertEquals(0, header.int)
        assertEquals(0, header.int)
        assertEquals(8_000, header.short.toInt() and 0xFFFF)
        assertEquals(1, header.get().toInt() and 0xFF)
        assertEquals(40, header.get().toInt() and 0xFF)
        assertEquals(164, header.short.toInt() and 0xFFFF)
        assertEquals(320, header.short.toInt() and 0xFFFF)
        val deviceLen = header.get().toInt() and 0xFF
        val sessionLen = header.get().toInt() and 0xFF
        val talkLen = header.get().toInt() and 0xFF
        assertEquals(deviceId.length, deviceLen)
        assertEquals(sessionId.length, sessionLen)
        assertEquals(talkId.length, talkLen)
        assertEquals(0, header.get().toInt() and 0xFF)
        val deviceBytes = ByteArray(deviceLen)
        header.get(deviceBytes)
        val sessionBytes = ByteArray(sessionLen)
        header.get(sessionBytes)
        val talkBytes = ByteArray(talkLen)
        header.get(talkBytes)

        assertEquals(deviceId, deviceBytes.toString(Charsets.UTF_8))
        assertEquals(sessionId, sessionBytes.toString(Charsets.UTF_8))
        assertEquals(talkId, talkBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun hadpEncoderCreatesFullHadpFileWithHeaderAndCrc() {
        val pcm = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val hadp = SpeakerHadpEncoder.encode(pcm, recordId = "REC_T12345678_1")
        val header = ByteBuffer.wrap(hadp.data, 0, 128).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(25, hadp.frameCount)
        assertEquals(SpeakerHadpCodec.Pcm16, hadp.codec)
        assertEquals(25 * 640, hadp.audioBytes)
        assertEquals(128 + 25 * 640, hadp.fileSize)
        assertEquals(hadp.fileSize, hadp.data.size)
        assertEquals(1_000, hadp.durationMs)
        assertEquals("HADP", hadp.data.copyOfRange(0, 4).toString(Charsets.UTF_8))
        header.position(4)
        assertEquals(1, header.short.toInt() and 0xFFFF)
        assertEquals(128, header.short.toInt() and 0xFFFF)
        assertEquals(2, header.short.toInt() and 0xFFFF)
        header.position(12)
        assertEquals(8_000, header.int)
        assertEquals(1, header.short.toInt() and 0xFFFF)
        assertEquals(40, header.short.toInt() and 0xFFFF)
        assertEquals(640, header.short.toInt() and 0xFFFF)
        assertEquals(320, header.short.toInt() and 0xFFFF)
        assertEquals(25, header.int)
        assertEquals(25 * 640, header.int)
        assertEquals(1_000, header.int)
        assertTrue(hadp.crc32.matches(Regex("0x[0-9A-F]{8}")))
        assertTrue(hadp.audioCrc32.matches(Regex("0x[0-9A-F]{8}")))
    }

    @Test
    fun hadpDecoderReturnsOriginalPcmForPcm16Hadp() {
        val pcm = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val hadp = SpeakerHadpEncoder.encode(pcm, recordId = "REC_PCM16_TEST")
        val decoded = SpeakerHadpDecoder.decodePcm16le(hadp)

        assertEquals(SpeakerHadpCodec.Pcm16, hadp.codec)
        assertArrayEquals(pcm, decoded)
    }

    @Test
    fun hadpEncoderStillSupportsImaAdpcm() {
        val pcm = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val hadp = SpeakerHadpEncoder.encode(
            pcm = pcm,
            recordId = "REC_T12345678_1",
            codec = SpeakerHadpCodec.ImaAdpcm
        )
        val header = ByteBuffer.wrap(hadp.data, 0, 128).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(SpeakerHadpCodec.ImaAdpcm, hadp.codec)
        assertEquals(25 * 164, hadp.audioBytes)
        header.position(8)
        assertEquals(1, header.short.toInt() and 0xFFFF)
        header.position(20)
        assertEquals(164, header.short.toInt() and 0xFFFF)
        assertEquals(320, header.short.toInt() and 0xFFFF)
    }

    @Test
    fun hadpDecoderStillSupportsImaAdpcmHadp() {
        val pcm = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val hadp = SpeakerHadpEncoder.encode(
            pcm = pcm,
            recordId = "REC_ADPCM_TEST",
            codec = SpeakerHadpCodec.ImaAdpcm
        )
        val decoded = SpeakerHadpDecoder.decodePcm16le(hadp)

        assertEquals(SpeakerHadpCodec.ImaAdpcm, hadp.codec)
        assertEquals(pcm.size, decoded.size)
        assertTrue(stats(decoded).rms > 0.01f)
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

    private fun syntheticHumPcm(amplitude: Float, sampleCount: Int): ByteArray {
        val pcm = ByteArray(sampleCount * 2)
        var seed = 0x13579BDF
        for (i in 0 until sampleCount) {
            seed = seed * 1103515245 + 12345
            val random = (((seed ushr 16) and 0x7FFF) / 16_383.5f) - 1f
            val t = i.toFloat() / SpeakerAdpcmPacketizer.SAMPLE_RATE
            val hum = sin(2f * PI.toFloat() * 180f * t) * 0.65f +
                sin(2f * PI.toFloat() * 900f * t) * 0.25f +
                random * 0.10f
            val value = (hum * amplitude).coerceIn(-1f, 1f).toPcm16()
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun syntheticClickPcm(amplitude: Float, sampleCount: Int): ByteArray {
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val envelope = 1f - (i.toFloat() / sampleCount.toFloat()).coerceIn(0f, 1f)
            val value = (amplitude * envelope * if (i % 2 == 0) 1f else -1f).toPcm16()
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun adpcmRoundTrip(pcm: ByteArray): ByteArray {
        val packetizer = SpeakerAdpcmPacketizer(useNative = false)
        val output = ArrayList<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + SpeakerAdpcmPacketizer.PCM_FRAME_BYTES, pcm.size)
            val frame = pcm.copyOfRange(offset, end)
            val packet = packetizer.packetize(frame, isLastPacket = end >= pcm.size)
            if (packet != null) {
                output += SpeakerAdpcmDecoder.decodeBlock(
                    block = packet,
                    offset = 20,
                    expectedSamples = frame.size / 2
                )
            }
            offset = end
        }
        packetizer.close()
        return output.flattenToByteArray()
    }

    private fun List<ByteArray>.flattenToByteArray(): ByteArray {
        val output = ByteArray(sumOf { it.size })
        var offset = 0
        forEach {
            it.copyInto(output, destinationOffset = offset)
            offset += it.size
        }
        return output
    }

    private fun metrics(pcm: ByteArray): AudioDiagnostics {
        var peak = 0f
        var sumSq = 0f
        var diffSq = 0f
        var clipping = 0
        var count = 0
        var previous: Float? = null
        var maxFrameJump = 0f
        var frameEndSample: Float? = null
        var offset = 0
        while (offset + 1 < pcm.size) {
            val sampleIndex = count
            val sample = pcm.readPcm16(offset) / 32768f
            val absSample = abs(sample)
            peak = maxOf(peak, absSample)
            sumSq += sample * sample
            if (absSample >= 0.999f) clipping += 1
            previous?.let {
                val diff = sample - it
                diffSq += diff * diff
            }
            if (sampleIndex % (SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / 2) == 0) {
                frameEndSample?.let {
                    maxFrameJump = maxOf(maxFrameJump, abs(sample - it))
                }
            }
            if ((sampleIndex + 1) % (SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / 2) == 0) {
                frameEndSample = sample
            }
            previous = sample
            count += 1
            offset += 2
        }
        val rms = if (count == 0) 0f else sqrt(sumSq / count)
        val diffRms = if (count <= 1) 0f else sqrt(diffSq / (count - 1))
        return AudioDiagnostics(
            rms = rms,
            peak = peak,
            crest = if (rms > 0f) peak / rms else 0f,
            highFrequencyProxy = if (rms > 0f) diffRms / rms else 0f,
            clippingRatio = if (count == 0) 0f else clipping.toFloat() / count.toFloat(),
            maxFrameJump = maxFrameJump
        )
    }

    private fun snrDb(reference: ByteArray, candidate: ByteArray): Float {
        val sampleCount = minOf(reference.size, candidate.size) / 2
        if (sampleCount <= 0) return 0f
        var signal = 0f
        var noise = 0f
        for (i in 0 until sampleCount) {
            val a = reference.readPcm16(i * 2) / 32768f
            val b = candidate.readPcm16(i * 2) / 32768f
            signal += a * a
            val error = a - b
            noise += error * error
        }
        if (noise <= 0f) return 99f
        return 10f * log10((signal / noise).coerceAtLeast(Float.MIN_VALUE))
    }

    private fun ByteArray.readPcm16(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) or (this[offset + 1].toInt() shl 8)).toShort().toInt()

    private fun Float.toPcm16(): Int =
        (coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()

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

    private data class AudioDiagnostics(
        val rms: Float,
        val peak: Float,
        val crest: Float,
        val highFrequencyProxy: Float,
        val clippingRatio: Float,
        val maxFrameJump: Float
    ) {
        fun compact(): String =
            "rms=$rms peak=$peak crest=$crest hf=$highFrequencyProxy clip=$clippingRatio jump=$maxFrameJump"
    }
}
