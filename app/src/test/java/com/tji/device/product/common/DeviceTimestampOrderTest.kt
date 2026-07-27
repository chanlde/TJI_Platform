package com.tji.device.product.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimestampOrderTest {
    @Test
    fun detectsRegressionOnlyInsideSameClockDomain() {
        assertTrue(isOlderDeviceTimestamp(incoming = 100, current = 300))
        assertTrue(isOlderDeviceTimestamp(incoming = 1_700_000_000, current = 1_700_000_100))
        assertTrue(isOlderDeviceTimestamp(incoming = 1_700_000_000_000, current = 1_700_000_000_100))

        assertFalse(isOlderDeviceTimestamp(incoming = 100, current = 1_700_000_000_000))
        assertFalse(isOlderDeviceTimestamp(incoming = 1_700_000_000, current = 1_700_000_000_000))
        assertFalse(isOlderDeviceTimestamp(incoming = null, current = 300))
    }

    @Test
    fun mergeDoesNotRegressWithinSameClockDomain() {
        assertEquals(300L, mergeDeviceTimestamp(current = 300, incoming = 100))
        assertEquals(400L, mergeDeviceTimestamp(current = 300, incoming = 400))
        assertEquals(300L, mergeDeviceTimestamp(current = 300, incoming = null))
    }
}
