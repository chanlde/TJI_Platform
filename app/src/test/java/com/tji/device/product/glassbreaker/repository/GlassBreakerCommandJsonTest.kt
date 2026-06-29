package com.tji.device.product.glassbreaker.repository

import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.glassbreaker.model.GlassBreakerCommandCode
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassBreakerCommandJsonTest {

    @Test
    fun selectChannelUsesNumericCmdAndParamsChannel() {
        val json = GlassBreakerCommand.SelectChannel("m-select", 3).toGlassBreakerJson("T0000001")

        assertEquals(1, json.getInt("v"))
        assertEquals("m-select", json.getString("msgId"))
        assertEquals("T0000001", json.getString("deviceId"))
        assertEquals(GlassBreakerCommandCode.SELECT_CHANNEL, json.getInt("cmd"))
        assertEquals("SELECT_CHANNEL", json.getString("cmdName"))
        assertEquals(3, json.getJSONObject("params").getInt("channel"))
    }

    @Test
    fun fireChannelUsesSelectedChannelParam() {
        val json = GlassBreakerCommand.FireChannel("m-fire", 2).toGlassBreakerJson("T0000001")

        assertEquals(GlassBreakerCommandCode.FIRE_CHANNEL, json.getInt("cmd"))
        assertEquals("FIRE_CHANNEL", json.getString("cmdName"))
        assertEquals(2, json.getJSONObject("params").getInt("channel"))
    }

    @Test
    fun laserSwitchUsesBooleanOnParam() {
        val json = GlassBreakerCommand.LaserSwitch("m-laser", true).toGlassBreakerJson("T0000001")

        assertEquals(GlassBreakerCommandCode.LASER_SWITCH, json.getInt("cmd"))
        assertEquals("LASER_SWITCH", json.getString("cmdName"))
        assertEquals(true, json.getJSONObject("params").getBoolean("on"))
    }

    @Test
    fun unlockAndLockUseFormalCommandCodes() {
        val unlock = GlassBreakerCommand.Unlock("m-unlock").toGlassBreakerJson("T0000001")
        val lock = GlassBreakerCommand.Lock("m-lock").toGlassBreakerJson("T0000001")

        assertEquals(GlassBreakerCommandCode.UNLOCK, unlock.getInt("cmd"))
        assertEquals("UNLOCK", unlock.getString("cmdName"))
        assertEquals(GlassBreakerCommandCode.LOCK, lock.getInt("cmd"))
        assertEquals("LOCK", lock.getString("cmdName"))
    }
}
