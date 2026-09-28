package com.tji.device.product.glassbreaker.viewmodel

import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBreakerPendingCommandTrackerTest {
    @Test
    fun oppositeSafetyLockCommandsCannotOverlap() {
        val tracker = GlassBreakerPendingCommandTracker()
        val unlock = GlassBreakerCommand.Unlock("unlock-1")
        val lock = GlassBreakerCommand.Lock("lock-1")

        assertTrue(tracker.start(unlock.msgId, SERIAL, unlock.conflictKey(SERIAL), "解锁"))
        assertFalse(tracker.start(lock.msgId, SERIAL, lock.conflictKey(SERIAL), "上锁"))

        assertEquals("解锁", tracker.complete(unlock.msgId)?.label)
        assertTrue(tracker.start(lock.msgId, SERIAL, lock.conflictKey(SERIAL), "上锁"))
    }

    @Test
    fun selectingDifferentChannelsUsesOneConflictFamily() {
        val tracker = GlassBreakerPendingCommandTracker()
        val channelOne = GlassBreakerCommand.SelectChannel("select-1", 1)
        val channelTwo = GlassBreakerCommand.SelectChannel("select-2", 2)

        assertEquals(
            channelOne.conflictKey(SERIAL),
            channelTwo.conflictKey(SERIAL)
        )
        assertTrue(
            tracker.start(channelOne.msgId, SERIAL, channelOne.conflictKey(SERIAL), "选择 1 通道")
        )
        assertFalse(
            tracker.start(channelTwo.msgId, SERIAL, channelTwo.conflictKey(SERIAL), "选择 2 通道")
        )
    }

    @Test
    fun laserDirectionsConflictButDifferentDevicesRemainIndependent() {
        val tracker = GlassBreakerPendingCommandTracker()
        val laserOn = GlassBreakerCommand.LaserSwitch("laser-on", true)
        val laserOff = GlassBreakerCommand.LaserSwitch("laser-off", false)

        assertEquals(laserOn.conflictKey(SERIAL), laserOff.conflictKey(SERIAL))
        assertTrue(tracker.start(laserOn.msgId, SERIAL, laserOn.conflictKey(SERIAL), "开启激光"))
        assertFalse(tracker.start(laserOff.msgId, SERIAL, laserOff.conflictKey(SERIAL), "关闭激光"))
        assertTrue(
            tracker.start(
                "laser-other",
                OTHER_SERIAL,
                laserOn.conflictKey(OTHER_SERIAL),
                "其他设备开启激光"
            )
        )
    }

    @Test
    fun fireCommandsShareOneConflictFamilyRegardlessOfChannel() {
        val fireOne = GlassBreakerCommand.FireChannel("fire-1", 1)
        val fireTwo = GlassBreakerCommand.FireChannel("fire-2", 2)

        assertEquals(fireOne.conflictKey(SERIAL), fireTwo.conflictKey(SERIAL))
    }

    @Test
    fun ackFromAnotherDeviceCannotCompleteOrReleaseSafetyLockCommand() {
        val tracker = GlassBreakerPendingCommandTracker()
        val lock = GlassBreakerCommand.Lock("lock-1")
        val unlock = GlassBreakerCommand.Unlock("unlock-1")

        assertTrue(tracker.start(lock.msgId, SERIAL, lock.conflictKey(SERIAL), "上锁"))
        assertNull(tracker.complete(lock.msgId, OTHER_SERIAL))
        assertFalse(tracker.start(unlock.msgId, SERIAL, unlock.conflictKey(SERIAL), "解锁"))
        assertEquals("上锁", tracker.complete(lock.msgId, SERIAL)?.label)
        assertTrue(tracker.start(unlock.msgId, SERIAL, unlock.conflictKey(SERIAL), "解锁"))
    }

    private companion object {
        const val SERIAL = "GLASS-001"
        const val OTHER_SERIAL = "GLASS-002"
    }
}
