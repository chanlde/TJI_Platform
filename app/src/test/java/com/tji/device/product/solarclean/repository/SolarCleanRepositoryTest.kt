package com.tji.device.product.solarclean.repository

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

    private companion object {
        const val SERIAL = "T36393932"
    }
}
