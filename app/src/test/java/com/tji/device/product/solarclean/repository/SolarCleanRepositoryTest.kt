package com.tji.device.product.solarclean.repository

import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCleanRepositoryTest {

    @Test
    fun controlSettingsPersistAcrossReadersAndAreClamped() = runBlocking {
        val repo = SolarCleanRepo()

        repo.updateControlSettings(SERIAL) {
            it.copy(
                pumpPressurePercent = 120.0,
                sprayAngleDegrees = -5.0,
                swingSpeedPercent = 75.0,
                pumpOn = true,
                swingOn = true
            )
        }

        val settings = repo.controlSettings.value.getValue(SERIAL)
        assertEquals(100.0, settings.pumpPressurePercent, 0.0)
        assertEquals(0.0, settings.sprayAngleDegrees, 0.0)
        assertEquals(75.0, settings.swingSpeedPercent, 0.0)
        assertTrue(settings.pumpOn)
        assertTrue(settings.swingOn)
    }

    @Test
    fun deviceInfoCannotRegressStateOrderingTimestamp() = runBlocking {
        val repo = SolarCleanRepo()
        repo.updateDeviceState(
            SolarCleanDeviceState(
                serialNumber = SERIAL,
                batteryPercent = 80.0,
                timestamp = 300
            )
        )

        repo.updateDeviceInfo(
            SERIAL,
            SolarCleanDeviceInfo(
                hardwareVersion = "HW-1",
                firmwareVersion = "1.0",
                timestamp = 200
            )
        )
        repo.updateDeviceState(
            SolarCleanDeviceState(
                serialNumber = SERIAL,
                batteryPercent = 10.0,
                timestamp = 250
            )
        )

        val state = repo.devices.value.single()
        assertEquals("HW-1", state.deviceInfo?.hardwareVersion)
        assertEquals(80.0, state.batteryPercent ?: Double.NaN, 0.0)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun olderDeviceInfoAndOtaStatusCannotReplaceNewerPayloads() = runBlocking {
        val repo = SolarCleanRepo()
        repo.updateDeviceInfo(
            SERIAL,
            SolarCleanDeviceInfo(
                hardwareVersion = "HW-NEW",
                firmwareVersion = "2.0",
                timestamp = 300
            )
        )
        repo.updateDeviceInfo(
            SERIAL,
            SolarCleanDeviceInfo(
                hardwareVersion = "HW-OLD",
                firmwareVersion = "1.0",
                timestamp = 200
            )
        )
        repo.updateOtaStatus(
            SERIAL,
            SolarCleanOtaStatus(status = "COMPLETED", progress = 100, timestamp = 400)
        )
        repo.updateOtaStatus(
            SERIAL,
            SolarCleanOtaStatus(status = "DOWNLOADING", progress = 20, timestamp = 350)
        )

        val state = repo.devices.value.single()
        assertEquals("HW-NEW", state.deviceInfo?.hardwareVersion)
        assertEquals("COMPLETED", state.otaStatus?.status)
        assertEquals(100, state.otaStatus?.progress)
        assertEquals(400L, state.timestamp)
    }

    private companion object {
        const val SERIAL = "T36393932"
    }
}
