package com.tji.device.product.common

import com.tji.device.product.droppersixstage.model.DROPPER_MAX_OPEN_DURATION_MS
import com.tji.device.product.droppersixstage.model.DROPPER_MIN_OPEN_DURATION_MS
import com.tji.device.product.droppersixstage.model.DropperControlLimits
import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.model.SolarCleanControlLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceCommandParameterPolicyTest {
    @Test
    fun `solar continuous controls clamp finite values`() {
        assertEquals(0.0, SolarCleanControlLimits.normalizePressure(-1.0)!!, 0.0)
        assertEquals(100.0, SolarCleanControlLimits.normalizePressure(120.0)!!, 0.0)
        assertEquals(40.0, SolarCleanControlLimits.normalizeSprayAngle(90.0)!!, 0.0)
        assertEquals(0.0, SolarCleanControlLimits.normalizeSwingSpeed(-20.0)!!, 0.0)
    }

    @Test
    fun `solar controls reject non-finite values before JSON serialization`() {
        assertNull(SolarCleanControlLimits.normalizePressure(Double.NaN))
        assertNull(SolarCleanControlLimits.normalizeSprayAngle(Double.POSITIVE_INFINITY))
        assertThrows(IllegalArgumentException::class.java) {
            SolarCleanCommand.SwingSpeed("invalid-speed", Double.NaN)
        }
    }

    @Test
    fun `dropper duration clamps but actuator target never does`() {
        assertEquals(
            DROPPER_MIN_OPEN_DURATION_MS,
            DropperControlLimits.normalizeOpenDuration(-1)
        )
        assertEquals(
            DROPPER_MAX_OPEN_DURATION_MS,
            DropperControlLimits.normalizeOpenDuration(Int.MAX_VALUE)
        )
        assertThrows(IllegalArgumentException::class.java) {
            DropperSixStageCommand.StageSwitch("invalid-stage", stage = 7, open = true)
        }
    }

    @Test
    fun `glass breaker rejects invalid hardware channel`() {
        assertThrows(IllegalArgumentException::class.java) {
            GlassBreakerCommand.SelectChannel("invalid-select", channel = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GlassBreakerCommand.FireChannel("invalid-fire", channel = 5)
        }
    }
}
