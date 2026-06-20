package com.tji.device.product.speaker.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class SpeakerCoreGoldenFixtureTest {
    @Test
    fun kotlinSpeakerCoreOutputsMatchGoldenFixtures() {
        val fixtures = buildFixtures()
        val fixtureDir = fixtureRoot()
        if (System.getenv("TJI_UPDATE_SPEAKER_GOLDEN") == "1") {
            writeFixtures(fixtureDir, fixtures)
        }

        fixtures.binary.forEach { (name, expected) ->
            assertArrayEquals(name, expected, Files.readAllBytes(fixtureDir.resolve(name)))
        }

        val expectedMetadata = fixtures.metadata.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
        assertEquals(expectedMetadata, String(Files.readAllBytes(fixtureDir.resolve(METADATA_FILE)), Charsets.UTF_8))
    }

    private fun buildFixtures(): GoldenFixtures {
        val pcm = syntheticVoicePcm(
            amplitude = 0.08f,
            sampleCount = SpeakerAdpcmPacketizer.SAMPLE_RATE
        )
        val legacyPacketizer = SpeakerAdpcmPacketizer()
        val legacyFrame0 = legacyPacketizer.packetize(
            pcm.copyOfRange(0, SpeakerAdpcmPacketizer.PCM_FRAME_BYTES)
        ) ?: error("failed to packetize legacy frame 0")
        val legacyFrame1 = legacyPacketizer.packetize(
            pcm.copyOfRange(SpeakerAdpcmPacketizer.PCM_FRAME_BYTES, SpeakerAdpcmPacketizer.PCM_FRAME_BYTES * 2)
        ) ?: error("failed to packetize legacy frame 1")

        val recordStorePacketizer = SpeakerAdpcmPacketizer(
            SpeakerUdpStreamContext(
                deviceId = DEVICE_ID,
                taskId = STORE_TASK_ID,
                type = SpeakerUdpStreamType.RecordStore,
                talkId = RECORD_ID
            )
        )
        val recordStoreLast = recordStorePacketizer.packetize(
            pcm.copyOfRange(0, SpeakerAdpcmPacketizer.PCM_FRAME_BYTES),
            isLastPacket = true
        ) ?: error("failed to packetize record-store frame")

        val pcm16Hadp = SpeakerHadpEncoder.encode(
            pcm = pcm,
            recordId = PCM16_RECORD_ID,
            codec = SpeakerHadpCodec.Pcm16
        )
        val adpcmHadp = SpeakerHadpEncoder.encode(
            pcm = pcm,
            recordId = ADPCM_RECORD_ID,
            codec = SpeakerHadpCodec.ImaAdpcm
        )

        val metadata = linkedMapOf(
            "adpcm.audioBytes" to adpcmHadp.audioBytes.toString(),
            "adpcm.audioCrc32" to adpcmHadp.audioCrc32,
            "adpcm.crc32" to adpcmHadp.crc32,
            "adpcm.durationMs" to adpcmHadp.durationMs.toString(),
            "adpcm.fileSize" to adpcmHadp.fileSize.toString(),
            "adpcm.frameBytes" to adpcmHadp.frameBytes.toString(),
            "adpcm.frameCount" to adpcmHadp.frameCount.toString(),
            "adpcm.samplesPerFrame" to adpcmHadp.samplesPerFrame.toString(),
            "legacyFrame0.bytes" to legacyFrame0.size.toString(),
            "legacyFrame1.bytes" to legacyFrame1.size.toString(),
            "pcm.bytes" to pcm.size.toString(),
            "pcm16.audioBytes" to pcm16Hadp.audioBytes.toString(),
            "pcm16.audioCrc32" to pcm16Hadp.audioCrc32,
            "pcm16.crc32" to pcm16Hadp.crc32,
            "pcm16.durationMs" to pcm16Hadp.durationMs.toString(),
            "pcm16.fileSize" to pcm16Hadp.fileSize.toString(),
            "pcm16.frameBytes" to pcm16Hadp.frameBytes.toString(),
            "pcm16.frameCount" to pcm16Hadp.frameCount.toString(),
            "pcm16.samplesPerFrame" to pcm16Hadp.samplesPerFrame.toString(),
            "recordStoreLast.bytes" to recordStoreLast.size.toString()
        )

        return GoldenFixtures(
            binary = linkedMapOf(
                PCM_FILE to pcm,
                LEGACY_FRAME0_FILE to legacyFrame0,
                LEGACY_FRAME1_FILE to legacyFrame1,
                RECORD_STORE_LAST_FILE to recordStoreLast,
                PCM16_HADP_FILE to pcm16Hadp.data,
                ADPCM_HADP_FILE to adpcmHadp.data
            ),
            metadata = metadata
        )
    }

    private fun writeFixtures(fixtureDir: Path, fixtures: GoldenFixtures) {
        Files.createDirectories(fixtureDir)
        fixtures.binary.forEach { (name, data) ->
            Files.write(fixtureDir.resolve(name), data)
        }
        val metadata = fixtures.metadata.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
        Files.write(fixtureDir.resolve(METADATA_FILE), metadata.toByteArray(Charsets.UTF_8))
    }

    private fun fixtureRoot(): Path {
        val fromRoot = Paths.get("app/src/test/resources/speaker-core-golden")
        return if (Files.exists(fromRoot.parent)) {
            fromRoot
        } else {
            Paths.get("src/test/resources/speaker-core-golden")
        }
    }

    private fun syntheticVoicePcm(amplitude: Float, sampleCount: Int): ByteArray {
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

    private data class GoldenFixtures(
        val binary: Map<String, ByteArray>,
        val metadata: Map<String, String>
    )

    private companion object {
        const val DEVICE_ID = "T12345678"
        const val RECORD_ID = "REC_T12345678_1"
        const val STORE_TASK_ID = "STORE_T12345678_1"
        const val PCM16_RECORD_ID = "REC_PCM16_TEST"
        const val ADPCM_RECORD_ID = "REC_ADPCM_TEST"

        const val PCM_FILE = "voice_1s_8k_pcm16le.raw"
        const val LEGACY_FRAME0_FILE = "legacy_adpcm_packet_frame0.bin"
        const val LEGACY_FRAME1_FILE = "legacy_adpcm_packet_frame1.bin"
        const val RECORD_STORE_LAST_FILE = "v2_record_store_last_packet.bin"
        const val PCM16_HADP_FILE = "hadp_pcm16_1s.hadp"
        const val ADPCM_HADP_FILE = "hadp_ima_adpcm_1s.hadp"
        const val METADATA_FILE = "metadata.properties"
    }
}
