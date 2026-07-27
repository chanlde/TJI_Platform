package com.tji.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttConnectionCallbackQueueTest {
    @Test
    fun connectedDrainInvokesOnlySuccessCallbacksAndClearsQueue() {
        val events = mutableListOf<String>()
        val queue = MqttConnectionCallbackQueue(capacity = 2)

        assertTrue(queue.offer(onConnected = { events += "connected-1" }, onFailed = { events += "failed-1" }))
        assertTrue(queue.offer(onConnected = { events += "connected-2" }, onFailed = { events += "failed-2" }))

        queue.drainConnected()

        assertEquals(listOf("connected-1", "connected-2"), events)
        assertEquals(0, queue.size())
    }

    @Test
    fun failedDrainKeepsFailurePairedWithItsRequest() {
        val events = mutableListOf<String>()
        val queue = MqttConnectionCallbackQueue(capacity = 2)
        val failure = IllegalStateException("offline")

        queue.offer(onConnected = { events += "connected" }, onFailed = { events += it.message.orEmpty() })
        queue.drainFailed(failure)

        assertEquals(listOf("offline"), events)
        assertEquals(0, queue.size())
    }

    @Test
    fun queueRejectsWorkBeyondCapacity() {
        val queue = MqttConnectionCallbackQueue(capacity = 1)

        assertTrue(queue.offer(onConnected = {}, onFailed = null))
        assertFalse(queue.offer(onConnected = {}, onFailed = null))
        assertEquals(1, queue.size())
    }

    @Test
    fun callbackFailureDoesNotPreventRemainingCallbacks() {
        val events = mutableListOf<String>()
        val callbackFailures = mutableListOf<String>()
        val queue = MqttConnectionCallbackQueue(capacity = 2)

        queue.offer(onConnected = { error("broken callback") }, onFailed = null)
        queue.offer(onConnected = { events += "second" }, onFailed = null)
        queue.drainConnected { callbackFailures += it.message.orEmpty() }

        assertEquals(listOf("second"), events)
        assertEquals(listOf("broken callback"), callbackFailures)
    }

    @Test
    fun inactiveWorkIsReclaimedBeforeCapacityCheck() {
        var firstActive = true
        val queue = MqttConnectionCallbackQueue(capacity = 1)

        assertTrue(queue.offer(onConnected = {}, onFailed = null, isActive = { firstActive }))
        firstActive = false

        assertTrue(queue.offer(onConnected = {}, onFailed = null))
        assertEquals(1, queue.size())
    }

    @Test
    fun inactiveWorkIsSkippedWhenConnectionCompletes() {
        val events = mutableListOf<String>()
        val queue = MqttConnectionCallbackQueue(capacity = 2)

        queue.offer(
            onConnected = { events += "stale" },
            onFailed = null,
            isActive = { false }
        )
        queue.offer(onConnected = { events += "active" }, onFailed = null)

        queue.drainConnected()

        assertEquals(listOf("active"), events)
        assertEquals(0, queue.size())
    }

    @Test
    fun canceledCoroutineCanPruneItsQueuedCallbacksImmediately() {
        var active = true
        val queue = MqttConnectionCallbackQueue(capacity = 2)

        queue.offer(
            onConnected = {},
            onFailed = {},
            isActive = { active }
        )
        active = false
        queue.pruneInactive()

        assertEquals(0, queue.size())
    }

    @Test
    fun explicitDisconnectFailureCompletesEveryActiveWaiter() {
        val failures = mutableListOf<String>()
        val queue = MqttConnectionCallbackQueue(capacity = 3)
        val disconnectFailure = IllegalStateException("connection closed")

        queue.offer(onConnected = {}, onFailed = { failures += "publish:${it.message}" })
        queue.offer(onConnected = {}, onFailed = { failures += "subscribe:${it.message}" })
        queue.offer(
            onConnected = {},
            onFailed = { failures += "canceled:${it.message}" },
            isActive = { false }
        )

        queue.drainFailed(disconnectFailure)

        assertEquals(
            listOf("publish:connection closed", "subscribe:connection closed"),
            failures
        )
        assertEquals(0, queue.size())
    }
}
