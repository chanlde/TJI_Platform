package com.tji.device.product.common

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingPreservingCancellationTest {
    @Test
    fun ordinaryFailureRemainsAvailableAsResult() {
        val result = runCatchingPreservingCancellation<Int> {
            error("operation failed")
        }

        assertTrue(result.isFailure)
        assertEquals("operation failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancellationIsAlwaysRethrown() {
        val cancellation = CancellationException("screen closed")

        val thrown = assertThrows(CancellationException::class.java) {
            runCatchingPreservingCancellation<Unit> {
                throw cancellation
            }
        }

        assertTrue(thrown === cancellation)
    }
}
