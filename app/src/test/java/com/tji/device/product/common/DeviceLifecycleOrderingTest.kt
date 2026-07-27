package com.tji.device.product.common

import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepo
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.speaker.repository.SpeakerRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLifecycleOrderingTest {
    @Test
    fun staleDropperOnlineCannotOverrideNewerOffline() = runBlocking {
        val repository = DropperSixStageRepo()
        repository.updateOnlineStatus(SERIAL, isOnline = false, timestamp = 300L)
        repository.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 100L)

        val state = repository.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun staleGlassOnlineCannotOverrideNewerOffline() = runBlocking {
        val repository = GlassBreakerRepo()
        repository.updateOnlineStatus(SERIAL, isOnline = false, timestamp = 300L)
        repository.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 100L)

        val state = repository.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun staleSolarOnlineCannotOverrideNewerOffline() = runBlocking {
        val repository = SolarCleanRepo()
        repository.updateOnlineStatus(SERIAL, isOnline = false, timestamp = 300L)
        repository.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 100L)

        val state = repository.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(300L, state.timestamp)
    }

    @Test
    fun staleSpeakerOnlineCannotOverrideNewerOffline() = runBlocking {
        val repository = SpeakerRepo()
        repository.updateOnlineStatus(SERIAL, isOnline = false, timestamp = 300L)
        repository.updateOnlineStatus(SERIAL, isOnline = true, timestamp = 100L)

        val state = repository.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(300L, state.timestamp)
    }

    private companion object {
        const val SERIAL = "DEVICE-001"
    }
}
