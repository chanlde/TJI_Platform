package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerHadpFile
import com.tji.device.product.speaker.model.SpeakerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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
        assertNull(SpeakerCoreNative.createAdpcmPacketizerOrNull())
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
        assertEquals("speakerCoreShadow status=nativeUnavailable", result.toLogLine())
    }

    @Test
    fun shadowMatchLogLineIncludesStableSummaryFields() {
        val result = SpeakerCoreShadowResult.Match(
            label = "hadp:pcm16",
            byteCount = 768,
            crc32 = "0x1234ABCD"
        )

        assertEquals(
            "speakerCoreShadow status=match label=hadp:pcm16 byteCount=768 crc32=0x1234ABCD",
            result.toLogLine()
        )
    }

    @Test
    fun shadowMismatchLogLineIncludesMismatchDiagnostics() {
        val result = SpeakerCoreShadowResult.Mismatch(
            label = "v2-adpcm-packet",
            kotlinSize = 238,
            nativeSize = 237,
            mismatchOffset = 28,
            kotlinCrc32 = "0x11111111",
            nativeCrc32 = "0x22222222",
            kotlinHeader = "5aa502014f000200",
            nativeHeader = "5aa502014e000200"
        )

        assertEquals(
            "speakerCoreShadow status=mismatch label=v2-adpcm-packet kotlinSize=238 nativeSize=237 " +
                "mismatchOffset=28 kotlinCrc32=0x11111111 nativeCrc32=0x22222222 " +
                "kotlinHeader=5aa502014f000200 nativeHeader=5aa502014e000200",
            result.toLogLine()
        )
    }

    @Test
    fun commandJsonFallbackBuildsStandardCommand() {
        val json = SpeakerCommandJson.encode(
            command = SpeakerCommand.SetVolume(
                msgId = "speaker-volume-1",
                volume = 150
            ),
            deviceId = "T12345678",
            timestampMs = 123456789L
        )

        assertEquals(1, json.getInt("v"))
        assertEquals("T12345678", json.getString("deviceId"))
        assertEquals("speaker-volume-1", json.getString("cmdId"))
        assertEquals("speaker-volume-1", json.getString("msgId"))
        assertEquals(123456789L, json.getLong("ts"))
        assertEquals(105, json.getInt("cmd"))
        assertEquals("SET_VOLUME", json.getString("cmdName"))
        assertEquals(100, json.getJSONObject("params").getInt("volume"))
    }

    @Test
    fun commandJsonFallbackBuildsStartTalkCommandWithTopLevelIds() {
        val json = SpeakerCommandJson.encode(
            command = SpeakerCommand.StartTalk(
                msgId = "speaker-talk-start-1",
                sessionId = "PLAY_PTT_1",
                talkId = "PLAY_PTT_1"
            ),
            deviceId = "T12345678",
            timestampMs = 123456789L
        )

        assertEquals(108, json.getInt("cmd"))
        assertEquals("START_TALK", json.getString("cmdName"))
        assertEquals("PLAY_PTT_1", json.getString("sessionId"))
        assertEquals("PLAY_PTT_1", json.getString("talkId"))
        assertEquals("ima_adpcm", json.getString("codec"))
        assertEquals(8_000, json.getInt("sampleRate"))
        assertEquals(1, json.getInt("channels"))
        assertEquals(40, json.getInt("packetMs"))
    }

    @Test
    fun commandJsonFallbackBuildsRecordDownloadCommand() {
        val json = SpeakerCommandJson.encode(
            command = SpeakerCommand.RecordDownload(
                msgId = "speaker-record-download-1",
                recordId = "REC_1",
                storeTaskId = "STORE_1",
                createdAt = "2026-06-21T09:00:00+08:00",
                name = "录音 09:00",
                downloadUrl = "http://example.com/REC_1.hadp",
                fileSize = 4228,
                crc32 = "0x1234ABCD",
                durationMs = 1000,
                codec = "ima_adpcm",
                sampleRate = 8_000,
                channels = 1,
                packetMs = 40,
                frameBytes = 164,
                samplesPerFrame = 320,
                temporary = true,
                visible = false,
                autoPlay = true,
                playbackVolume = 150
            ),
            deviceId = "T12345678",
            timestampMs = 123456789L
        )

        assertEquals(1, json.getInt("v"))
        assertEquals("T12345678", json.getString("deviceId"))
        assertEquals("speaker-record-download-1", json.getString("cmdId"))
        assertEquals("RECORD_DOWNLOAD", json.getString("cmdName"))
        assertEquals("REC_1", json.getString("recordId"))
        assertEquals("STORE_1", json.getString("storeTaskId"))
        assertEquals("录音 09:00", json.getString("name"))
        assertEquals(4228L, json.getLong("fileSize"))
        assertEquals("ima_adpcm", json.getString("codec"))
        assertEquals(true, json.getBoolean("temporary"))
        assertEquals(false, json.getBoolean("visible"))
        assertEquals(true, json.getBoolean("autoPlay"))
        assertEquals(100, json.getInt("playbackVolume"))
    }

    @Test
    fun audioToolFallbackResamplesPcm16() {
        val input = syntheticVoicePcm(sampleRate = 8_000, sampleCount = 800)

        val output = SpeakerCoreAudioEngine.resamplePcm16(
            pcm16le = input,
            sourceSampleRate = 8_000,
            targetSampleRate = 16_000
        )

        assertEquals(input.size * 2, output.size)
        assertEquals(0, output.size % 2)
    }

    @Test
    fun audioToolFallbackGeneratesTone() {
        val tone = SpeakerCoreAudioEngine.generateTonePcm16(
            frequencyHz = 1_000,
            durationMs = 640,
            amplitude = 0.35f
        )

        assertEquals(8_000 * 640 / 1_000 * 2, tone.size)
        assertEquals(0, tone[0].toInt())
        assertEquals(0, tone[1].toInt())
    }

    @Test
    fun audioToolFallbackPrependsSilenceAndPadsFrame() {
        val input = syntheticVoicePcm(sampleRate = 8_000, sampleCount = 320)

        val withSilence = SpeakerCoreAudioEngine.prependSilencePcm16(
            pcm16le = input,
            durationMs = 120,
            sampleRate = 8_000
        )
        assertEquals(input.size + 8_000 * 120 / 1_000 * 2, withSilence.size)
        assertEquals(input.toList(), withSilence.takeLast(input.size))

        val padded = SpeakerCoreAudioEngine.padPcm16ToFrame(input.copyOf(input.size - 20), frameBytes = 640)
        assertEquals(640, padded.size)
    }

    @Test
    fun audioToolFallbackDecodesWavPcm16Mono() {
        val wav = buildPcm16Wav(
            sampleRate = 16_000,
            channels = 2,
            interleavedSamples = shortArrayOf(
                1_000, 3_000,
                -1_000, -3_000,
                2_000, 4_000,
                -2_000, -4_000
            )
        )

        val pcm = SpeakerCoreAudioEngine.decodeWavPcm16Mono(wav, targetSampleRate = 8_000)

        assertEquals(4, pcm.size)
        assertEquals(0, pcm.readLeI16(0))
    }

    @Test
    fun audioToolFallbackConvertsFloat32ToPcm16() {
        val pcm = SpeakerCoreAudioEngine.float32ToPcm16(
            samples = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f),
            sourceSampleRate = 8_000,
            targetSampleRate = 8_000
        )

        assertEquals(10, pcm.size)
        assertEquals(Short.MIN_VALUE.toInt(), pcm.readLeI16(0))
        assertEquals(Short.MAX_VALUE.toInt(), pcm.readLeI16(8))
    }

    private fun syntheticVoicePcm(
        sampleRate: Int,
        sampleCount: Int,
        amplitude: Float = 0.08f
    ): ByteArray {
        val pcm = ByteArray(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val phase = 2.0 * PI * 440.0 * index.toDouble() / sampleRate.toDouble()
            val sample = (sin(phase) * Short.MAX_VALUE * amplitude).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val offset = index * 2
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun buildPcm16Wav(
        sampleRate: Int,
        channels: Int,
        interleavedSamples: ShortArray
    ): ByteArray {
        val dataBytes = interleavedSamples.size * 2
        val out = ArrayList<Byte>(44 + dataBytes)
        out.appendAscii("RIFF")
        out.appendLe32(36 + dataBytes)
        out.appendAscii("WAVE")
        out.appendAscii("fmt ")
        out.appendLe32(16)
        out.appendLe16(1)
        out.appendLe16(channels)
        out.appendLe32(sampleRate)
        out.appendLe32(sampleRate * channels * 2)
        out.appendLe16(channels * 2)
        out.appendLe16(16)
        out.appendAscii("data")
        out.appendLe32(dataBytes)
        interleavedSamples.forEach { out.appendLe16(it.toInt()) }
        return out.toByteArray()
    }

    private fun MutableList<Byte>.appendAscii(value: String) {
        value.toByteArray(Charsets.US_ASCII).forEach(::add)
    }

    private fun MutableList<Byte>.appendLe16(value: Int) {
        add((value and 0xFF).toByte())
        add(((value shr 8) and 0xFF).toByte())
    }

    private fun MutableList<Byte>.appendLe32(value: Int) {
        add((value and 0xFF).toByte())
        add(((value shr 8) and 0xFF).toByte())
        add(((value shr 16) and 0xFF).toByte())
        add(((value shr 24) and 0xFF).toByte())
    }

    private fun ByteArray.readLeI16(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) or (this[offset + 1].toInt() shl 8)).toShort().toInt()
}
