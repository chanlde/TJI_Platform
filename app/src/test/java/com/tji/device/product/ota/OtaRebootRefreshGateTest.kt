package com.tji.device.product.ota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaRebootRefreshGateTest {
    @Test
    fun refreshRequiresOfflineThenOnlineAndOccursOncePerOtaCycle() {
        val gate = OtaRebootRefreshGate()

        assertFalse(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = true))
        assertFalse(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = false))
        assertTrue(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = true))
        assertFalse(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = true))

        assertFalse(gate.shouldRefresh(SERIAL, waitingForReboot = false, isOnline = true))
        assertFalse(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = false))
        assertTrue(gate.shouldRefresh(SERIAL, waitingForReboot = true, isOnline = true))
    }

    private companion object {
        const val SERIAL = "SOLAR-001"
    }
}
