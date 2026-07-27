package com.tji.device.product.solarclean.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PostRebootDeviceInfoRefreshTest {
    @Test
    fun retriesOnceWhenDeviceLinkIsStillStabilizing() = runBlocking {
        var attempts = 0
        var retryWaits = 0

        val result = runPostRebootDeviceInfoRefresh(
            maxAttempts = 2,
            waitBeforeFirstAttempt = {},
            waitBeforeRetry = { retryWaits += 1 },
            sendRefresh = {
                attempts += 1
                if (attempts == 1) throw IllegalStateException("mqtt reconnecting")
            }
        )

        assertTrue(result.isSuccess)
        assertEquals(2, attempts)
        assertEquals(1, retryWaits)
    }

    @Test
    fun returnsFinalFailureWithoutLeakingItFromBackgroundJob() = runBlocking {
        val secondFailure = IllegalStateException("broker unavailable")
        var attempts = 0

        val result = runPostRebootDeviceInfoRefresh(
            maxAttempts = 2,
            waitBeforeFirstAttempt = {},
            waitBeforeRetry = {},
            sendRefresh = {
                attempts += 1
                if (attempts == 1) {
                    throw IllegalStateException("device reconnecting")
                }
                throw secondFailure
            }
        )

        assertEquals(2, attempts)
        assertSame(secondFailure, result.exceptionOrNull())
    }

    @Test
    fun cancellationStopsRefreshWithoutRetrying() {
        val cancellation = CancellationException("device page closed")
        var attempts = 0

        try {
            runBlocking {
                runPostRebootDeviceInfoRefresh(
                    maxAttempts = 2,
                    waitBeforeFirstAttempt = {},
                    waitBeforeRetry = { fail("Cancellation must not retry") },
                    sendRefresh = {
                        attempts += 1
                        throw cancellation
                    }
                )
            }
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(1, attempts)
    }
}
