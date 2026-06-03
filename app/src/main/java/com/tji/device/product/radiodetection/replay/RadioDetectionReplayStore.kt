package com.tji.device.product.radiodetection.replay

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class RadioDetectionReplayStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordRidPayload(serialNumber: String, rawPayload: String) {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return

        val records = readRecords(serialNumber).toMutableList()
        if (records.lastOrNull()?.payload == trimmed) return

        records += RadioDetectionReplayRecord(
            payload = trimmed,
            capturedAtMillis = System.currentTimeMillis()
        )
        val next = records.takeLast(MAX_RECORDS)
        prefs.edit()
            .putString(keyFor(serialNumber), next.toJsonArray().toString())
            .apply()

        Log.d(TAG, "Recorded RadioDetection RID replay payload: sn=$serialNumber count=${next.size}")
    }

    fun latestPayload(serialNumber: String): String? =
        readRecords(serialNumber).lastOrNull()?.payload

    fun payloadCount(serialNumber: String): Int =
        readRecords(serialNumber).size

    private fun readRecords(serialNumber: String): List<RadioDetectionReplayRecord> {
        val raw = prefs.getString(keyFor(serialNumber), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RadioDetectionReplayRecord(
                    payload = item.getString("payload"),
                    capturedAtMillis = item.optLong("capturedAtMillis")
                )
            }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to read RadioDetection replay cache: sn=$serialNumber", throwable)
            emptyList()
        }
    }

    private fun List<RadioDetectionReplayRecord>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { record ->
                array.put(
                    JSONObject().apply {
                        put("payload", record.payload)
                        put("capturedAtMillis", record.capturedAtMillis)
                    }
                )
            }
        }

    private fun keyFor(serialNumber: String): String =
        "rid_payloads_$serialNumber"

    private data class RadioDetectionReplayRecord(
        val payload: String,
        val capturedAtMillis: Long
    )

    private companion object {
        const val TAG = "RadioDetectionReplay"
        const val PREFS_NAME = "radio_detection_replay"
        const val MAX_RECORDS = 20
    }
}
