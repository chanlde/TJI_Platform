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
    private val persistIntervalMillis: Long = PERSIST_INTERVAL_MS
) {
    constructor(context: Context) : this(
        persistence = SharedPreferencesRadioReplayPersistence(context.applicationContext),
        persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val persistenceLock = Any()
    private val latestPayloadBySerial = mutableMapOf<String, String>()
    private val lastPersistedAtBySerial = mutableMapOf<String, Long>()
    private val pendingPersistenceJobs = mutableMapOf<String, Job>()

    fun recordRidPayload(serialNumber: String, rawPayload: String) {
        if (serialNumber.isBlank()) return
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return

        synchronized(persistenceLock) {
            if (latestPayloadBySerial.put(serialNumber, trimmed) == trimmed) return

            val now = monotonicMillis()
            val lastPersistedAt = lastPersistedAtBySerial[serialNumber]
            if (
                lastPersistedAt == null ||
                now - lastPersistedAt >= persistIntervalMillis
            ) {
                pendingPersistenceJobs.remove(serialNumber)?.cancel()
                persistLatestLocked(serialNumber, trimmed)
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
                    latestPayloadBySerial[serialNumber]?.let { latest ->
                        persistLatestLocked(serialNumber, latest)
                    }
                }
            }
            pendingPersistenceJobs[serialNumber] = pendingJob
        }
    }

    fun latestPayload(serialNumber: String): String? = synchronized(persistenceLock) {
        latestPayloadBySerial[serialNumber]
            ?: persistence.read(serialNumber)?.also { payload ->
                latestPayloadBySerial[serialNumber] = payload
            }
    }

    private fun persistLatestLocked(serialNumber: String, payload: String) {
        try {
            persistence.write(serialNumber, payload)
            lastPersistedAtBySerial[serialNumber] = monotonicMillis()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Recorded latest RadioDetection RID replay payload: sn=$serialNumber")
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to persist RadioDetection replay payload: sn=$serialNumber", throwable)
        }
    }

    private companion object {
        const val TAG = "RadioDetectionReplay"
        const val PERSIST_INTERVAL_MS = 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal interface RadioReplayPersistence {
    fun read(serialNumber: String): String?
    fun write(serialNumber: String, payload: String)
}

private class SharedPreferencesRadioReplayPersistence(context: Context) : RadioReplayPersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(serialNumber: String): String? {
        prefs.getString(latestKeyFor(serialNumber), null)?.let { return it }
        val migratedPayload = readLegacyLatestPayload(serialNumber) ?: return null
        write(serialNumber, migratedPayload)
        return migratedPayload
    }

    override fun write(serialNumber: String, payload: String) {
        prefs.edit {
            putString(latestKeyFor(serialNumber), payload)
        }
    }

    private fun readLegacyLatestPayload(serialNumber: String): String? {
        val raw = prefs.getString(legacyKeyFor(serialNumber), null) ?: return null
        return runCatching {
            val records = JSONArray(raw)
            records.optJSONObject(records.length() - 1)?.optString("payload")?.ifBlank { null }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to migrate RadioDetection replay cache: sn=$serialNumber", throwable)
            null
        }
    }

    private fun legacyKeyFor(serialNumber: String): String =
        "rid_payloads_$serialNumber"

    private fun latestKeyFor(serialNumber: String): String =
        "rid_latest_payload_$serialNumber"

    private companion object {
        const val TAG = "RadioDetectionReplay"
        const val PREFS_NAME = "radio_detection_replay"
    }
}
