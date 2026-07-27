package com.tji.device.ui.floating

import com.tji.device.di.ProductFloatingQuickControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class FloatingQuickToggleExecutorTest {
    @Test
    fun reportsPublishFailureInsteadOfLeakingCoroutineException() = runBlocking {
        val publishFailure = IllegalStateException("broker unavailable")
        var reportedFailure: Throwable? = null

        executeFloatingQuickToggle(
            control = ProductFloatingQuickControl { _, _, _ -> throw publishFailure },
            linkSerial = "LINK-01",
            switchSerial = "SWITCH-01",
            targetAngle = 90,
            onFailure = { reportedFailure = it }
        )

        assertSame(publishFailure, reportedFailure)
    }

    @Test
    fun forwardsTargetAndDoesNotReportSuccessfulControl() = runBlocking {
        var received: Triple<String, String, Int>? = null
        var failureCount = 0

        executeFloatingQuickToggle(
            control = ProductFloatingQuickControl { link, switch, angle ->
                received = Triple(link, switch, angle)
            },
            linkSerial = "LINK-01",
            switchSerial = "SWITCH-01",
            targetAngle = 0,
            onFailure = { failureCount += 1 }
        )

        assertEquals(Triple("LINK-01", "SWITCH-01", 0), received)
        assertEquals(0, failureCount)
    }

    @Test
    fun cancellationStillCancelsTheOwningViewModelJob() {
        val cancellation = CancellationException("screen closed")

        try {
            runBlocking {
                executeFloatingQuickToggle(
                    control = ProductFloatingQuickControl { _, _, _ -> throw cancellation },
                    linkSerial = "LINK-01",
                    switchSerial = "SWITCH-01",
                    targetAngle = 90,
                    onFailure = { fail("Cancellation must not be reported as a command failure") }
                )
            }
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
