package com.tji.device.product.radiodetection.viewmodel

import com.tji.device.product.radiodetection.model.RadioRgbAck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioRgbCommandTrackerTest {
    @Test
    fun oldAckCannotOverwriteNewerPendingCommand() {
        val tracker = RadioRgbCommandTracker()
        tracker.start("RADIO-1", "rgb-1", "发送 1")
        tracker.start("RADIO-2", "rgb-2", "发送 2")

        val oldFeedback = tracker.complete(ack("rgb-1", ok = true))
        val currentFeedback = tracker.complete(ack("rgb-2", ok = true))

        assertNull(oldFeedback)
        assertEquals("rgb-2", currentFeedback?.msgId)
        assertEquals("RADIO-2", currentFeedback?.serialNumber)
        assertEquals(true, currentFeedback?.success)
    }

    @Test
    fun publishFailureAndTimeoutAreAppliedOnlyOnce() {
        val tracker = RadioRgbCommandTracker()
        tracker.start("RADIO-1", "rgb-1", "发送")

        val failure = tracker.fail("rgb-1", "发送失败")

        assertEquals(false, failure?.success)
        assertNull(tracker.timeout("rgb-1"))
        assertNull(tracker.markPublished("rgb-1"))
    }

    private fun ack(msgId: String, ok: Boolean) = RadioRgbAck(
        msgId = msgId,
        ok = ok,
        code = if (ok) 0 else 121,
        message = if (ok) "rgb preview applied" else "bad params",
        timestamp = null
    )
}
