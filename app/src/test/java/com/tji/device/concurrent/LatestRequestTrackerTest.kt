package com.tji.device.concurrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestTrackerTest {
    @Test
    fun onlyLatestRequestForSameTargetCanCommit() {
        val tracker = LatestRequestTracker<String>()
        val first = tracker.begin("DEVICE-1")
        val second = tracker.begin("DEVICE-1")

        assertFalse(tracker.isLatest("DEVICE-1", first))
        assertTrue(tracker.isLatest("DEVICE-1", second))
    }

    @Test
    fun differentTargetsRemainIndependent() {
        val tracker = LatestRequestTracker<String>()
        val first = tracker.begin("DEVICE-1")
        val second = tracker.begin("DEVICE-2")

        assertTrue(tracker.isLatest("DEVICE-1", first))
        assertTrue(tracker.isLatest("DEVICE-2", second))
    }

    @Test
    fun accountChangeInvalidatesEveryPendingRequest() {
        val tracker = LatestRequestTracker<String>()
        val request = tracker.begin("DEVICE-1")

        tracker.invalidateAll()

        assertFalse(tracker.isLatest("DEVICE-1", request))
    }
}
