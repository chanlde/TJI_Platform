package com.tji.device.product.radiodetection.replay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        var wallClock = 10_000L
        val persistence = RecordingReplayPersistence(
            stored = mutableMapOf(SERIAL to RadioReplayRecord("persisted", wallClock))
        )
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            wallClockMillis = { wallClock }
        )

        assertEquals("persisted", store.latestPayload(SERIAL))
        persistence.stored.clear()
        assertEquals("persisted", store.latestPayload(SERIAL))
        assertNull(store.latestPayload("missing"))
    }

    @Test
    fun expiredReplayIsRemovedInsteadOfRestored() = runBlocking {
        val now = 100_000L
        val persistence = RecordingReplayPersistence(
            stored = mutableMapOf(SERIAL to RadioReplayRecord("expired", recordedAtMillis = 1L))
        )
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            wallClockMillis = { now },
            replayTtlMillis = 10_000L
        )

        assertNull(store.latestPayload(SERIAL))
        assertNull(persistence.stored[SERIAL])
    }

    @Test
    fun replayCacheKeepsOnlyNewestConfiguredDeviceCount() = runBlocking {
        var now = 1_000L
        val persistence = RecordingReplayPersistence()
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            wallClockMillis = { now },
            maxReplayDevices = 3
        )

        repeat(4) { index ->
            store.recordRidPayload("SERIAL-$index", "payload-$index")
            now += 1L
        }

        assertNull(store.latestPayload("SERIAL-0"))
        assertEquals(setOf("SERIAL-1", "SERIAL-2", "SERIAL-3"), persistence.stored.keys)
    }

    @Test
    fun clearAllRemovesMemoryDiskAndPendingPersistence() = runBlocking {
        var elapsedMillis = 1_000L
        val persistence = RecordingReplayPersistence()
        val store = RadioDetectionReplayStore(
            persistence = persistence,
            persistenceScope = CoroutineScope(coroutineContext),
            monotonicMillis = { elapsedMillis },
            persistIntervalMillis = 20L
        )

        store.recordRidPayload(SERIAL, "payload-1")
        elapsedMillis += 1L
        store.recordRidPayload(SERIAL, "payload-2")
        store.clearAll()
        delay(30L)

        assertNull(store.latestPayload(SERIAL))
        assertTrue(persistence.stored.isEmpty())
        assertEquals(listOf("payload-1"), persistence.writes)
    }

    private class RecordingReplayPersistence(
        val stored: MutableMap<String, RadioReplayRecord> = mutableMapOf()
    ) : RadioReplayPersistence {
        val writes = mutableListOf<String>()

        override fun read(serialNumber: String): RadioReplayRecord? = stored[serialNumber]

        override fun write(serialNumber: String, replay: RadioReplayRecord) {
            stored[serialNumber] = replay
            writes += replay.payload
        }

        override fun remove(serialNumber: String) {
            stored.remove(serialNumber)
        }

        override fun prune(olderThanMillis: Long, maxEntries: Int) {
            stored.entries.removeAll { it.value.recordedAtMillis < olderThanMillis }
            stored.entries.sortedByDescending { it.value.recordedAtMillis }
                .drop(maxEntries)
                .forEach { stored.remove(it.key) }
        }

        override fun clearAll() {
            stored.clear()
        }
    }

    private companion object {
        const val SERIAL = "FC100-REPLAY-001"
    }
}
