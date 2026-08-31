package com.tji.device.product.speaker.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpeakerMediaTransferProtocolTest {
    @Test
    fun mediaTransferGoldenVectorMatchesMcuContract() {
        val file = byteArrayOf(
            'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(),
            'S'.code.toByte(),
            *ByteArray(22),
            0x01, 0x01, 0x11
        )
        val packets = SpeakerMediaTransferClient(token = "test-token").buildChunks(request(file))

        assertEquals(1, packets.size)
        assertArrayEquals(
            (
                "5aa50202370015000000000000000000803e011459000000090a0800" +
                    "5435544e42464d345153544f52455f544553545245435f54455354" +
                    "4d54523101025000000001001d0000004331bf5c28000000c05d0000" +
                    "4331bf5c0414010054657374323032362d30372d33305430303a3030" +
                    "3a30305a4f6767530000000000000000000000000000000000000000" +
                    "0000010111"
                ).hexToBytes(),
            packets.single()
        )
    }

    @Test
    fun missingRelayCredentialReportsConfigurationErrorBeforeNetworkAccess() = runBlocking {
        val failure = runCatching {
            SpeakerMediaTransferClient(token = "").send(request(byteArrayOf(1)))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("语音传输服务未配置", failure?.message)
    }

    private fun request(file: ByteArray): SpeakerMediaTransferRequest =
        SpeakerMediaTransferRequest(
            deviceId = "T5TNBFM4Q",
            sessionId = "STORE_TEST",
            recordId = "REC_TEST",
            name = "Test",
            createdAt = "2026-07-30T00:00:00Z",
            opusFile = SpeakerOpusFile(
                data = file,
                sampleRate = 16_000,
                channels = 1,
                packetMs = 20,
                bitrate = 24_000,
                fileSize = file.size,
                crc32 = "0x5CBF3143",
                durationMs = 40,
                packetCount = 2
            ),
            mode = SpeakerMediaTransferMode.Store,
            volume = 80
        )

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
