package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import com.tji.device.data.session.DeviceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOtaStartGuardTest {
    private val deviceKey = DeviceKey(ProductType.SolarClean, "SOLAR-01")

    @Test
    fun keepsReservationWhileActiveAndReleasesForTerminalStatus() {
        val guard = ProductOtaStartGuard()

        assertSame(OtaStartReservation.Accepted, guard.reserve(deviceKey, "ota-1"))
        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-1"),
            guard.reserve(deviceKey, "ota-2")
        )
        assertFalse(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_DOWNLOADING", cmdId = "ota-1")
            )
        )
        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-1"),
            guard.reserve(deviceKey, "ota-3")
        )
        assertFalse(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_SUCCESS", cmdId = "older-ota")
            )
        )
        assertTrue(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_SUCCESS", cmdId = "ota-1")
            )
        )
        assertSame(OtaStartReservation.Accepted, guard.reserve(deviceKey, "ota-4"))
    }

    @Test
    fun publishFailureOnlyReleasesMatchingReservation() {
        val guard = ProductOtaStartGuard()
        val otherDevice = DeviceKey(ProductType.SolarClean, "SOLAR-02")

        guard.reserve(deviceKey, "ota-1")

        assertFalse(guard.releaseAfterPublishFailure(otherDevice, "ota-1"))
        assertFalse(guard.releaseAfterPublishFailure(deviceKey, "old-command"))
        assertTrue(guard.isActive(deviceKey, "ota-1"))
        assertTrue(guard.releaseAfterPublishFailure(deviceKey, "ota-1"))
        assertFalse(guard.isActive(deviceKey, "ota-1"))
        assertSame(OtaStartReservation.Accepted, guard.reserve(deviceKey, "ota-2"))
    }

    @Test
    fun reservationsRemainIndependentAcrossDeviceSwitches() {
        val guard = ProductOtaStartGuard()
        val otherDevice = DeviceKey(ProductType.Speaker, "SPEAKER-01")

        assertSame(OtaStartReservation.Accepted, guard.reserve(deviceKey, "ota-solar"))
        assertSame(OtaStartReservation.Accepted, guard.reserve(otherDevice, "ota-speaker"))

        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-solar"),
            guard.reserve(deviceKey, "ota-solar-duplicate")
        )
        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-speaker"),
            guard.reserve(otherDevice, "ota-speaker-duplicate")
        )
    }

    @Test
    fun retryableFailureReleasesOnlyItsOwnDevice() {
        val guard = ProductOtaStartGuard()
        val otherDevice = DeviceKey(ProductType.Speaker, "SPEAKER-01")
        guard.reserve(deviceKey, "ota-solar")
        guard.reserve(otherDevice, "ota-speaker")

        assertTrue(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_FAILED", cmdId = "ota-solar")
            )
        )

        assertSame(OtaStartReservation.Accepted, guard.reserve(deviceKey, "ota-solar-retry"))
        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-speaker"),
            guard.reserve(otherDevice, "ota-speaker-duplicate")
        )
    }

    @Test
    fun rollbackInProgressDoesNotReleaseReservation() {
        val guard = ProductOtaStartGuard()
        guard.reserve(deviceKey, "ota-1")

        assertFalse(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_ROLLBACK", cmdId = "ota-1")
            )
        )
        assertEquals(
            OtaStartReservation.AlreadyReserved("ota-1"),
            guard.reserve(deviceKey, "ota-2")
        )
        assertTrue(
            guard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = "OTA_ROLLED_BACK", cmdId = "ota-1")
            )
        )
    }
}
