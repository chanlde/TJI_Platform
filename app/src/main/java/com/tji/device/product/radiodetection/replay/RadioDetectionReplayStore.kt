package com.tji.device.product.radiodetection.replay

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.tji.device.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class RadioDetectionReplayStore internal constructor(
    private val persistence: RadioReplayPersistence,
    private val persistenceScope: CoroutineScope,
    private val monotonicMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
    private val persistIntervalMillis: Long = PERSIST_INTERVAL_MS,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val replayTtlMillis: Long = REPLAY_TTL_MS,
    private val maxReplayDevices: Int = MAX_REPLAY_DEVICES
) {
    constructor(context: Context) : this(
        persistence = SharedPreferencesRadioReplayPersistence(context.applicationContext),
        persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val persistenceLock = Any()
    private val latestReplayBySerial = mutableMapOf<String, RadioReplayRecord>()
    private val lastPersistedAtBySerial = mutableMapOf<String, Long>()
    private val pendingPersistenceJobs = mutableMapOf<String, Job>()

    fun recordRidPayload(serialNumber: String, rawPayload: String) {
        if (serialNumber.isBlank()) return
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return

        synchronized(persistenceLock) {
            val recordedAtMillis = wallClockMillis()
            pruneMemoryLocked(recordedAtMillis)
            if (latestReplayBySerial[serialNumber]?.payload == trimmed) return
            ensureCapacityForNewSerialLocked(serialNumber)
            latestReplayBySerial[serialNumber] = RadioReplayRecord(trimmed, recordedAtMillis)

            val now = monotonicMillis()
            val lastPersistedAt = lastPersistedAtBySerial[serialNumber]
            if (
                lastPersistedAt == null ||
                now - lastPersistedAt >= persistIntervalMillis
            ) {
                pendingPersistenceJobs.remove(serialNumber)?.cancel()
                persistLatestLocked(serialNumber, latestReplayBySerial.getValue(serialNumber))
                return
            }

            if (pendingPersistenceJobs[serialNumber]?.isActive == true) return
            val remainingDelay = (persistIntervalMillis - (now - lastPersistedAt))
                .coerceAtLeast(1L)
            lateinit var pendingJob: Job
            pendingJob = persistenceScope.launch {
                delay(remainingDelay)
                synchronized(persistenceLock) {
                    if (pendingPersistenceJobs[serialNumber] !== pendingJob) return@synchronized
                    pendingPersistenceJobs.remove(serialNumber)
                    latestReplayBySerial[serialNumber]?.let { latest ->
                        persistLatestLocked(serialNumber, latest)
                    }
                }
            }
            pendingPersistenceJobs[serialNumber] = pendingJob
        }
    }

    fun latestPayload(serialNumber: String): String? = synchronized(persistenceLock) {
        val now = wallClockMillis()
        pruneMemoryLocked(now)
        latestReplayBySerial[serialNumber]?.payload
            ?: persistence.read(serialNumber)?.let { replay ->
                if (replay.isExpired(now, replayTtlMillis)) {
                    persistence.remove(serialNumber)
                    null
                } else {
                    ensureCapacityForNewSerialLocked(serialNumber)
                    latestReplayBySerial[serialNumber] = replay
                    replay.payload
                }
            }
    }

    fun clearAll() = synchronized(persistenceLock) {
        pendingPersistenceJobs.values.forEach { it.cancel() }
        pendingPersistenceJobs.clear()
        latestReplayBySerial.clear()
        lastPersistedAtBySerial.clear()
        persistence.clearAll()
    }

    private fun persistLatestLocked(serialNumber: String, replay: RadioReplayRecord) {
        try {
            persistence.write(serialNumber, replay)
            persistence.prune(
                olderThanMillis = wallClockMillis() - replayTtlMillis,
                maxEntries = maxReplayDevices
            )
            lastPersistedAtBySerial[serialNumber] = monotonicMillis()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Recorded latest RadioDetection RID replay payload: sn=$serialNumber")
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to persist RadioDetection replay payload", throwable)
        }
    }

    private fun pruneMemoryLocked(nowMillis: Long) {
        val expiredSerials = latestReplayBySerial
            .filterValues { it.isExpired(nowMillis, replayTtlMillis) }
            .keys
            .toList()
        expiredSerials.forEach(::removeSerialLocked)
    }

    private fun ensureCapacityForNewSerialLocked(serialNumber: String) {
        if (serialNumber in latestReplayBySerial) return
        while (latestReplayBySerial.size >= maxReplayDevices) {
            val oldestSerial = latestReplayBySerial.minByOrNull { it.value.recordedAtMillis }?.key
                ?: return
            removeSerialLocked(oldestSerial)
        }
    }

    private fun removeSerialLocked(serialNumber: String) {
        latestReplayBySerial.remove(serialNumber)
        lastPersistedAtBySerial.remove(serialNumber)
        pendingPersistenceJobs.remove(serialNumber)?.cancel()
        persistence.remove(serialNumber)
    }

    private companion object {
        const val TAG = "RadioDetectionReplay"
        const val PERSIST_INTERVAL_MS = 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val REPLAY_TTL_MS = 24 * 60 * 60 * 1_000L
        const val MAX_REPLAY_DEVICES = 64
    }
}

internal data class RadioReplayRecord(
    val payload: String,
    val recordedAtMillis: Long
) {
    fun isExpired(nowMillis: Long, ttlMillis: Long): Boolean =
        nowMillis - recordedAtMillis > ttlMillis
}

internal interface RadioReplayPersistence {
    fun read(serialNumber: String): RadioReplayRecord?
    fun write(serialNumber: String, replay: RadioReplayRecord)
    fun remove(serialNumber: String)
    fun prune(olderThanMillis: Long, maxEntries: Int)
    fun clearAll()
}

private class SharedPreferencesRadioReplayPersistence(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : RadioReplayPersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(serialNumber: String): RadioReplayRecord? {
        prefs.getString(latestKeyFor(serialNumber), null)?.let { payload ->
            val recordedAt = prefs.getLong(recordedAtKeyFor(serialNumber), 0L)
                .takeIf { it > 0L }
                ?: nowMillis().also { migratedAt ->
                    prefs.edit { putLong(recordedAtKeyFor(serialNumber), migratedAt) }
                }
            return RadioReplayRecord(payload, recordedAt)
        }
        val migratedPayload = readLegacyLatestPayload(serialNumber) ?: return null
        return RadioReplayRecord(migratedPayload, nowMillis()).also { replay ->
            write(serialNumber, replay)
        }
    }

    override fun write(serialNumber: String, replay: RadioReplayRecord) {
        prefs.edit {
            putString(latestKeyFor(serialNumber), replay.payload)
            putLong(recordedAtKeyFor(serialNumber), replay.recordedAtMillis)
        }
    }

    override fun remove(serialNumber: String) {
        prefs.edit {
            remove(latestKeyFor(serialNumber))
            remove(recordedAtKeyFor(serialNumber))
            remove(legacyKeyFor(serialNumber))
        }
    }

    override fun prune(olderThanMillis: Long, maxEntries: Int) {
        val records = prefs.all.keys
            .filter { it.startsWith(LATEST_PREFIX) }
            .mapNotNull { key ->
                val serial = key.removePrefix(LATEST_PREFIX)
                val recordedAt = prefs.getLong(recordedAtKeyFor(serial), 0L)
                serial.takeIf { it.isNotBlank() }?.let { it to recordedAt }
            }
            .sortedByDescending { it.second }
        val toRemove = records
            .filter { (_, recordedAt) -> recordedAt <= 0L || recordedAt < olderThanMillis }
            .map { it.first }
            .toMutableSet()
        records.filterNot { it.first in toRemove }
            .drop(maxEntries)
            .forEach { toRemove += it.first }
        toRemove.forEach(::remove)
    }

    override fun clearAll() {
        prefs.edit { clear() }
    }

    private fun readLegacyLatestPayload(serialNumber: String): String? {
        val raw = prefs.getString(legacyKeyFor(serialNumber), null) ?: return null
        return runCatching {
            val records = JSONArray(raw)
            records.optJSONObject(records.length() - 1)?.optString("payload")?.ifBlank { null }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to migrate RadioDetection replay cache", throwable)
            null
        }
    }

    private fun legacyKeyFor(serialNumber: String): String =
        "rid_payloads_$serialNumber"

    private fun latestKeyFor(serialNumber: String): String =
        "$LATEST_PREFIX$serialNumber"

    private fun recordedAtKeyFor(serialNumber: String): String =
        "rid_latest_recorded_at_$serialNumber"

    private companion object {
        const val TAG = "RadioDetectionReplay"
        const val PREFS_NAME = "radio_detection_replay"
        const val LATEST_PREFIX = "rid_latest_payload_"
    }
}
