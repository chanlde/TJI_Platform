package com.tji.device.product.solarclean.viewmodel

import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolarControlSettingsTrackerTest {
    @Test
    fun failedCommandRollsBackToConfirmedValue() {
        val tracker = SolarControlSettingsTracker()
        val initial = SolarCleanControlSettings(pumpPressurePercent = 40.0)
        val optimistic = tracker.begin(
            SERIAL,
            "pressure-1",
            SolarControlField.PumpPressure,
            SolarControlValue.Level(80.0),
            initial
        )

        val resolved = tracker.resolve("pressure-1", successful = false, current = optimistic)

        assertEquals(40.0, resolved?.settings?.pumpPressurePercent ?: Double.NaN, 0.0)
    }

    @Test
    fun oldFailureKeepsNewerPendingValue() {
        val tracker = SolarControlSettingsTracker()
        val initial = SolarCleanControlSettings(pumpPressurePercent = 40.0)
        val first = tracker.begin(
            SERIAL,
            "pressure-1",
            SolarControlField.PumpPressure,
            SolarControlValue.Level(60.0),
            initial
        )
        val second = tracker.begin(
            SERIAL,
            "pressure-2",
            SolarControlField.PumpPressure,
            SolarControlValue.Level(80.0),
            first
        )

        val afterOldFailure = tracker.resolve("pressure-1", successful = false, current = second)
        val afterNewFailure = tracker.resolve(
            "pressure-2",
            successful = false,
            current = afterOldFailure!!.settings
        )

        assertEquals(80.0, afterOldFailure.settings.pumpPressurePercent, 0.0)
        assertEquals(40.0, afterNewFailure?.settings?.pumpPressurePercent ?: Double.NaN, 0.0)
    }

    @Test
    fun newerSuccessMakesLateOlderAckObsolete() {
        val tracker = SolarControlSettingsTracker()
        val initial = SolarCleanControlSettings(sprayAngleDegrees = 10.0)
        val first = tracker.begin(
            SERIAL,
            "angle-1",
            SolarControlField.SprayAngle,
            SolarControlValue.Level(20.0),
            initial
        )
        val second = tracker.begin(
            SERIAL,
            "angle-2",
            SolarControlField.SprayAngle,
            SolarControlValue.Level(30.0),
            first
        )

        val newerSuccess = tracker.resolve("angle-2", successful = true, current = second)

        assertEquals(30.0, newerSuccess?.settings?.sprayAngleDegrees ?: Double.NaN, 0.0)
        assertNull(tracker.resolve("angle-1", successful = true, current = newerSuccess!!.settings))
    }

    @Test
    fun sameFieldOnDifferentDevicesIsIndependent() {
        val tracker = SolarControlSettingsTracker()
        val initial = SolarCleanControlSettings()
        val firstDevice = tracker.begin(
            "SOLAR-1",
            "pump-1",
            SolarControlField.Pump,
            SolarControlValue.Toggle(true),
            initial
        )
        tracker.begin(
            "SOLAR-2",
            "pump-2",
            SolarControlField.Pump,
            SolarControlValue.Toggle(true),
            initial
        )

        val firstFailure = tracker.resolve("pump-1", successful = false, current = firstDevice)

        assertEquals(false, firstFailure?.settings?.pumpOn)
        assertEquals("SOLAR-2", tracker.serialNumberFor("pump-2"))
    }

    private companion object {
        const val SERIAL = "SOLAR-001"
    }
}
