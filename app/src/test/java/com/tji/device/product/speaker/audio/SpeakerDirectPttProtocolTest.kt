package com.tji.device.product.speaker.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerDirectPttProtocolTest {
    @Test
    fun directPttGoldenVectorMatchesMcuContract() {
        val packets = SpeakerDirectPttClient(token = "test-token").buildChunks(
            deviceId = "T5TNBFM4Q",
            sessionId = "PTT_T5TNBFM4Q_TEST",
            talkId = "TALK_T5TNBFM4Q_TEST",
            frames = listOf(
                byteArrayOf(0x11, 0x22, 0x33),
                byteArrayOf(0x44, 0x55)
            ),
            durationMs = 40,
            bitrate = 32_000,
            volume = 80
        )

        assertEquals(1, packets.size)
        assertArrayEquals(
            (
                "5aa502024a001500000000000000000080bb01142500800709121300" +
                    "5435544e42464d34515054545f5435544e42464d34515f54455354" +
                    "54414c4b5f5435544e42464d34515f544553544450543101025000" +
                    "000001000200000028000000007d00008b3ed597030011223302004455"
                ).hexToBytes(),
            packets.single()
        )
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
