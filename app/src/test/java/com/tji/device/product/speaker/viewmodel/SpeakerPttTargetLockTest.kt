package com.tji.device.product.speaker.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerPttTargetLockTest {
    @Test
    fun targetIsCapturedAtPressTime() {
        val lock = SpeakerPttTargetLock()

        assertTrue(lock.claim("SPEAKER-1"))
        assertFalse(lock.claim("SPEAKER-2"))
        assertEquals("SPEAKER-1", lock.release())
        assertNull(lock.release())
    }

    @Test
    fun oldScreenCannotCancelNewDeviceOperation() {
        val lock = SpeakerPttTargetLock()
        lock.claim("SPEAKER-2")

        assertFalse(lock.clearIfOwnedBy("SPEAKER-1"))
        assertEquals("SPEAKER-2", lock.release())
    }

    @Test
    fun concurrentPressesCanClaimOnlyOneTarget() = runBlocking {
        val lock = SpeakerPttTargetLock()

        val claims = (1..100).map { index ->
            async(Dispatchers.Default) {
                lock.claim("SPEAKER-$index")
            }
        }.awaitAll()

        assertEquals(1, claims.count { it })
        assertTrue(lock.release()?.startsWith("SPEAKER-") == true)
    }
}
