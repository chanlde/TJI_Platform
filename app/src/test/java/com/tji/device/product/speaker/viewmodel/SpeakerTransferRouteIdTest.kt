package com.tji.device.product.speaker.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTransferRouteIdTest {
    @Test
    fun `all speaker transfer ids fit UDP route limit`() {
        val serial = "T5TNBFM4Q-超长 设备编号-1234567890-ABCDEFGHIJ"
        val timestamp = 1_754_000_000_000L

        listOf("TTS", "STTS", "TONE", "STONE", "PTT", "TALK", "REC", "STORE")
            .map { speakerTransferRouteId(it, serial, timestamp) }
            .forEach { routeId ->
                assertTrue(routeId.encodeToByteArray().size in 1..32)
                assertFalse(routeId.any(Char::isWhitespace))
            }
    }

    @Test
    fun `timestamp suffix remains unique when device id is truncated`() {
        val serial = "DEVICE_WITH_A_VERY_LONG_SERIAL_NUMBER_1234567890"

        val first = speakerTransferRouteId("STTS", serial, 1_754_000_000_000L)
        val second = speakerTransferRouteId("STTS", serial, 1_754_000_000_001L)

        assertNotEquals(first, second)
        assertTrue(first.encodeToByteArray().size <= 32)
        assertTrue(second.encodeToByteArray().size <= 32)
    }

    @Test
    fun `invalid serial falls back to ascii device id`() {
        val routeId = speakerTransferRouteId("tts!", "设备 编号", 1L)

        assertTrue(routeId.startsWith("TTS_DEVICE_"))
        assertTrue(routeId.encodeToByteArray().size <= 32)
    }
}
