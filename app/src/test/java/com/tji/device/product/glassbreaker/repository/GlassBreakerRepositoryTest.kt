package com.tji.device.product.glassbreaker.repository

import com.tji.device.product.glassbreaker.model.GlassBreakerAck
import com.tji.device.product.glassbreaker.model.GlassBreakerLockState
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassBreakerRepositoryTest {

    @Test
    fun olderAckCompletesFeedbackWithoutRollingBackDeviceState() = runBlocking {
        val repository = GlassBreakerRepo()
        repository.updateState(
            GlassBreakerState(
                serialNumber = SERIAL,
                isOnline = true,
                lockState = GlassBreakerLockState.Unlocked,
                selectedChannel = 3,
                laserEnabled = true,
                timestamp = 200
            )
        )

        repository.updateAck(
            SERIAL,
            GlassBreakerAck(
                msgId = "old-ack",
                ok = true,
                lockState = GlassBreakerLockState.Locked,
                selectedChannel = 1,
                laserEnabled = false,
                timestamp = 100
            )
        )

        val state = repository.devices.value.single()
        assertEquals("old-ack", state.lastAck?.msgId)
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(3, state.selectedChannel)
        assertEquals(true, state.laserEnabled)
        assertEquals(200L, state.timestamp)
    }

    @Test
    fun newerAckCanAdvanceDeviceState() = runBlocking {
        val repository = GlassBreakerRepo()
        repository.updateState(
            GlassBreakerState(
                serialNumber = SERIAL,
                lockState = GlassBreakerLockState.Locked,
                selectedChannel = 1,
                timestamp = 100
            )
        )

        repository.updateAck(
            SERIAL,
            GlassBreakerAck(
                msgId = "new-ack",
                ok = true,
                lockState = GlassBreakerLockState.Unlocked,
                selectedChannel = 4,
                timestamp = 200
            )
        )

        val state = repository.devices.value.single()
        assertEquals(GlassBreakerLockState.Unlocked, state.lockState)
        assertEquals(4, state.selectedChannel)
        assertEquals(200L, state.timestamp)
    }

    @Test
    fun olderOtaAckDoesNotRegressStateOrderingTimestamp() = runBlocking {
        val repository = GlassBreakerRepo()
        repository.updateState(
            GlassBreakerState(serialNumber = SERIAL, timestamp = 300)
        )

        repository.updateOtaAck(
            SERIAL,
            GlassBreakerAck(msgId = "old-ota", ok = true, timestamp = 200)
        )

        val state = repository.devices.value.single()
        assertEquals("old-ota", state.lastOtaAck?.msgId)
        assertEquals(300L, state.timestamp)
    }

    private companion object {
        const val SERIAL = "GLASS-001"
    }
}
