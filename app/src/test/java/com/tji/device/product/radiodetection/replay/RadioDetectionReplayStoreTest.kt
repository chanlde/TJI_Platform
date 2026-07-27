package com.tji.device.product.radiodetection.replay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioDetectionReplayStoreTest {

    @Test
    fun burstPersistsFirstAndTrailingLatestPayload() = runBlocking {
        var elapsedMillis = 1_000L
        val persistence = RecordingReplayPersistence()
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            monotonicMillis = { elapsedMillis },
            persistIntervalMillis = 20L
        )

        store.recordRidPayload(SERIAL, "payload-1")
        elapsedMillis += 5L
        store.recordRidPayload(SERIAL, "payload-2")
        elapsedMillis += 5L
        store.recordRidPayload(SERIAL, "payload-3")

        assertEquals(listOf("payload-1"), persistence.writes)
        assertEquals("payload-3", store.latestPayload(SERIAL))

        elapsedMillis += 10L
        delay(50L)

        assertEquals(listOf("payload-1", "payload-3"), persistence.writes)
    }

    @Test
    fun duplicateAndBlankPayloadsDoNotCreateDiskWrites() = runBlocking {
        val persistence = RecordingReplayPersistence()
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            persistIntervalMillis = 20L
        )

        store.recordRidPayload(SERIAL, "same")
        store.recordRidPayload(SERIAL, " same ")
        store.recordRidPayload(SERIAL, " ")
        store.recordRidPayload("", "ignored")
        delay(30L)

        assertEquals(listOf("same"), persistence.writes)
    }

    @Test
    fun readsPersistedPayloadIntoMemoryCache() = runBlocking {
        val persistence = RecordingReplayPersistence(
            stored = mutableMapOf(SERIAL to "persisted")
        )
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext)
        )

        assertEquals("persisted", store.latestPayload(SERIAL))
        persistence.stored.clear()
        assertEquals("persisted", store.latestPayload(SERIAL))
        assertNull(store.latestPayload("missing"))
    }

    private class RecordingReplayPersistence(
        val stored: MutableMap<String, String> = mutableMapOf()
    ) : RadioReplayPersistence {
        val writes = mutableListOf<String>()

        override fun read(serialNumber: String): String? = stored[serialNumber]

        override fun write(serialNumber: String, payload: String) {
            stored[serialNumber] = payload
            writes += payload
        }
    }

    private companion object {
        const val SERIAL = "FC100-REPLAY-001"
    }
}
