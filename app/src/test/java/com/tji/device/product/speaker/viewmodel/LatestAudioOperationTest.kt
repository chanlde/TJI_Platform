package com.tji.device.product.speaker.viewmodel

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestAudioOperationTest {
    @Test
    fun replacingOperationCancelsPreviousJob() {
        val operations = LatestAudioOperation()
        val previous = Job()
        val latest = Job()

        operations.replaceWith(previous)
        operations.replaceWith(latest)

        assertTrue(previous.isCancelled)
        assertTrue(latest.isActive)
    }

    @Test
    fun staleCompletionCannotClearLatestJob() {
        val operations = LatestAudioOperation()
        val previous = Job()
        val latest = Job()

        operations.replaceWith(previous)
        operations.replaceWith(latest)
        operations.clearIfCurrent(previous)
        operations.cancel()

        assertTrue(latest.isCancelled)
        assertFalse(latest.isActive)
    }
}
