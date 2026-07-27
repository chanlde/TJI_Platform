package com.tji.device.product.common

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceCommandAckTimingTest {
    @Test
    fun `ACK window starts after publish completes`() = runBlocking {
        val events = mutableListOf<String>()

        publishThenWaitForDeviceAck(
            ackTimeoutMs = 3_000L,
            publish = {
                events += "publish-start"
                events += "publish-complete"
            },
            waitForTimeout = { timeoutMs ->
                events += "wait-$timeoutMs"
            }
        )

        assertEquals(
            listOf("publish-start", "publish-complete", "wait-3000"),
            events
        )
    }

    @Test
    fun `publish failure never starts ACK window`() {
        val events = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                publishThenWaitForDeviceAck(
                    ackTimeoutMs = 3_000L,
                    publish = {
                        events += "publish"
                        error("broker unavailable")
                    },
                    waitForTimeout = {
                        events += "wait"
                    }
                )
            }
        }

        assertEquals(listOf("publish"), events)
    }

    @Test
    fun `non-positive timeout is rejected before publish`() {
        var published = false

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                publishThenWaitForDeviceAck(
                    ackTimeoutMs = 0L,
                    publish = { published = true }
                )
            }
        }

        assertEquals(false, published)
    }
}
