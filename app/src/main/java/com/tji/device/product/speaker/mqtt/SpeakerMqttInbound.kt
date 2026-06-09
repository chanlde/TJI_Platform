package com.tji.device.product.speaker.mqtt

import android.util.Log
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import com.tji.device.product.speaker.repository.SpeakerRepository
import org.json.JSONArray
import org.json.JSONObject

class SpeakerMqttInbound(
    private val repository: SpeakerRepository
) {
    suspend fun handleEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean = false
    ) {
        when (eventType) {
            "online" -> repository.updateOnlineStatus(
                serialNumber = serialNumber,
                isOnline = true,
                timestamp = json.optNullableLong("ts")
            )
            "offline" -> repository.updateOnlineStatus(
                serialNumber = serialNumber,
                isOnline = false,
                timestamp = json.optNullableLong("ts")
            )
            "state", "status" -> repository.updateState(parseState(serialNumber, json, allowOnline = !isRetained))
            "ack" -> repository.updateAck(serialNumber, parseAck(json))
            "record_list" -> repository.updateRecords(
                serialNumber = serialNumber,
                records = parseRecords(json.optJSONArray("items") ?: json.optJSONArray("records")),
                offset = json.optInt("offset", 0),
                limit = json.optInt("limit", 8).coerceIn(1, 8),
                total = json.optInt("total", json.optInt("count", 0)),
                hasMore = json.optBoolean("hasMore", false),
                timestamp = json.optNullableLong("ts")
            )
            "storage_status" -> {
                val status = parseStorageStatus(json)
                Log.d(
                    TAG,
                    "Speaker storage parsed sn=$serialNumber ok=${status.ok} " +
                        "free=${status.freeBytes} total=${status.totalBytes} " +
                        "records=${status.recordCount}/${status.maxRecords} backend=${status.backend}"
                )
                repository.updateStorageStatus(serialNumber, status)
            }
            "record_saved",
            "record_failed",
            "record_updated",
            "record_deleted",
            "record_playback" -> {
                val event = parseRecordEvent(eventType, json)
                Log.d(
                    TAG,
                    "Speaker record event sn=$serialNumber type=${event.type} ok=${event.ok} " +
                        "code=${event.code} recordId=${event.recordId} msg=${event.message}"
                )
                repository.updateRecordEvent(serialNumber, event)
            }
            else -> Log.d(TAG, "Speaker MQTT ignored sn=$serialNumber event=$eventType")
        }
    }

    fun cleanup() = Unit

    private fun parseState(
        serialNumber: String,
        json: JSONObject,
        allowOnline: Boolean
    ): SpeakerDeviceState =
        SpeakerDeviceState(
            serialNumber = serialNumber,
            name = json.optString("name").ifBlank { null },
            isOnline = allowOnline,
            playing = json.optBoolean("playing", false),
            currentFile = json.optString("currentFile").ifBlank { null },
            volume = json.optNullableInt("volume")?.coerceIn(0, 100) ?: 35,
            servoAngle = json.optNullableInt("servoAngle"),
            lastError = json.optString("lastError").ifBlank { null },
            network = json.optString("network").ifBlank { null },
            timestamp = json.optNullableLong("ts")
        )

    private fun parseAck(json: JSONObject): SpeakerAck =
        SpeakerAck(
            msgId = json.optString("cmdId").ifBlank { json.optString("msgId") },
            ofType = json.optString("ofType").ifBlank { json.optString("cmdName") },
            ofCmd = json.optInt("ofCmd", json.optInt("cmd", -1)),
            ok = json.optBoolean("ok"),
            code = json.optInt("code", -1),
            message = json.optString("msg"),
            timestamp = json.optNullableLong("ts")
        )

    private fun parseRecords(array: JSONArray?): List<SpeakerRecord> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val recordId = item.optString("recordId")
                if (recordId.isBlank()) continue
                add(
                    SpeakerRecord(
                        recordId = recordId,
                        name = item.optString("name").ifBlank { recordId },
                        fileSize = item.optLong("fileSize", 0L),
                        durationMs = item.optLong("durationMs", 0L),
                        codec = item.optString("codec").ifBlank { "ima_adpcm" },
                        sampleRate = item.optInt("sampleRate", 8_000),
                        channels = item.optInt("channels", 1),
                        packetMs = item.optInt("packetMs", 40),
                        crc32 = item.optString("crc32").ifBlank { null },
                        createdAt = item.optString("createdAt").ifBlank { null },
                        createdMs = item.optNullableLong("createdMs"),
                        path = item.optString("path").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun parseStorageStatus(json: JSONObject): SpeakerStorageStatus =
        SpeakerStorageStatus(
            ok = json.optBoolean("ok", true),
            backend = json.optString("backend").ifBlank { null },
            totalBytes = json.optLong("totalBytes", 0L),
            freeBytes = json.optLong("freeBytes", 0L),
            recordCount = json.optInt("recordCount", 0),
            maxRecords = json.optInt("maxRecords", 0),
            code = json.optInt("code", 0),
            message = json.optString("msg"),
            timestamp = json.optNullableLong("ts")
        )

    private fun parseRecordEvent(eventType: String, json: JSONObject): SpeakerRecordEvent =
        SpeakerRecordEvent(
            type = eventType,
            recordId = json.optString("recordId").ifBlank { null },
            ok = json.optBoolean("ok", eventType != "record_failed"),
            code = json.optInt("code", 0),
            message = json.optString("msg"),
            timestamp = json.optNullableLong("ts")
        )

    private companion object {
        const val TAG = "SpeakerMqttInbound"
    }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
