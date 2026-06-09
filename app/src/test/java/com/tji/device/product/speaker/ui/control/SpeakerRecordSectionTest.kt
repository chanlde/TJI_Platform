package com.tji.device.product.speaker.ui.control

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerRecordSectionTest {

    @Test
    fun formatsDurationForEmptySecondsAndMinutes() {
        assertEquals("--", formatDuration(0))
        assertEquals("8秒", formatDuration(8_900))
        assertEquals("2分5秒", formatDuration(125_000))
    }

    @Test
    fun formatsBytesForEmptyKbAndMb() {
        assertEquals("--", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 KB", formatBytes(2_048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }
}
