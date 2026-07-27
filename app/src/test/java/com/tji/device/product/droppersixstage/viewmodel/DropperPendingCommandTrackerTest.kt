package com.tji.device.product.droppersixstage.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropperPendingCommandTrackerTest {
    @Test
    fun sameDeviceStageCannotAccumulateOverlappingCommands() {
        val tracker = DropperPendingCommandTracker()

        assertTrue(tracker.start("cmd-1", "DROP-1", setOf("DROP-1:stage:2"), "2段自动开钩"))
        assertFalse(tracker.start("cmd-2", "DROP-1", setOf("DROP-1:stage:2"), "2段自动开钩"))
        assertTrue(tracker.isPending("DROP-1:stage:2"))

        val completed = tracker.complete("cmd-1")
        assertEquals("DROP-1", completed?.serialNumber)
        assertEquals("2段自动开钩", completed?.label)
        assertFalse(tracker.isPending("DROP-1:stage:2"))
        assertTrue(tracker.start("cmd-3", "DROP-1", setOf("DROP-1:stage:2"), "2段自动开钩"))
    }

    @Test
    fun differentDevicesAndStagesRemainIndependent() {
        val tracker = DropperPendingCommandTracker()

        assertTrue(tracker.start("cmd-1", "DROP-1", setOf("DROP-1:stage:1"), "设备一"))
        assertTrue(tracker.start("cmd-2", "DROP-1", setOf("DROP-1:stage:2"), "设备一第二段"))
        assertTrue(tracker.start("cmd-3", "DROP-2", setOf("DROP-2:stage:1"), "设备二"))
    }

    @Test
    fun allStagesConflictsWithAnyStageOnTheSameDevice() {
        val tracker = DropperPendingCommandTracker()
        val allDeviceOneStages = (1..6).mapTo(mutableSetOf()) { "DROP-1:stage:$it" }

        assertTrue(tracker.start("all-1", "DROP-1", allDeviceOneStages, "全部抛投"))
        assertFalse(tracker.start("stage-1", "DROP-1", setOf("DROP-1:stage:4"), "4段开钩"))
        assertTrue(tracker.start("other-device", "DROP-2", setOf("DROP-2:stage:4"), "另一设备4段开钩"))

        tracker.complete("all-1")
        assertTrue(tracker.start("stage-2", "DROP-1", setOf("DROP-1:stage:4"), "4段开钩"))
    }

    @Test
    fun activeSingleStageBlocksAllStagesButNotAnotherStage() {
        val tracker = DropperPendingCommandTracker()
        val allStages = (1..6).mapTo(mutableSetOf()) { "DROP-1:stage:$it" }

        assertTrue(tracker.start("stage-1", "DROP-1", setOf("DROP-1:stage:2"), "2段开钩"))
        assertTrue(tracker.start("stage-2", "DROP-1", setOf("DROP-1:stage:3"), "3段开钩"))
        assertFalse(tracker.start("all-1", "DROP-1", allStages, "全部抛投"))
    }
}
