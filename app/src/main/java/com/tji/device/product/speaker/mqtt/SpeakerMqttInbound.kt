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
            "record_list" -> {
                val records = parseRecords(json.optJSONArray("items") ?: json.optJSONArray("records"))
                Log.d(
                    TAG,
                    "Speaker record list parsed sn=$serialNumber offset=${json.optInt("offset", 0)} " +
                        "limit=${json.optInt("limit", 8)} total=${json.optInt("total", json.optInt("count", records.size))} " +
                        "count=${records.size} first=${records.firstOrNull()?.recordId.orEmpty()} " +
                        "last=${records.lastOrNull()?.recordId.orEmpty()}"
                )
                if (records.isEmpty() && json.optInt("total", 0) > 0) {
                    Log.w(TAG, "Speaker record list has total but parsed empty: ${json.toString().take(600)}")
                }
                repository.updateRecords(
                    serialNumber = serialNumber,
                    records = records,
                    offset = json.optInt("offset", 0),
                    limit = json.optInt("limit", 8).coerceIn(1, 8),
                    total = json.optInt("total", json.optInt("count", records.size)),
                    hasMore = json.optBoolean("hasMore", false),
                    timestamp = json.optNullableLong("ts")
                )
            }
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
            "record_progress",
            "record_verify",
            "record_updated",
            "record_deleted",
            "record_playback" -> {
                val event = parseRecordEvent(eventType, json)
                Log.d(
                    TAG,
                    "Speaker record event sn=$serialNumber type=${event.type} ok=${event.ok} " +
                        "code=${event.code} recordId=${event.recordId} progress=${event.progress} msg=${event.message}"
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
            outputQuality = json.optString("outputQuality")
                .ifBlank { json.optString("audioQuality") }
                .ifBlank { json.optString("quality") }
                .ifBlank { null },
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
            progress = json.optInt("progress", 0),
            downloadedBytes = json.optLong("downloadedBytes", 0L),
            totalBytes = json.optLong("totalBytes", 0L),
            headerSize = json.optInt("headerSize", json.optInt("headerBytes", 0)),
            frameBytes = json.optInt("frameBytes", 0),
            samplesPerFrame = json.optInt("samplesPerFrame", 0),
            frameCount = json.optInt("frameCount", 0),
            audioBytes = json.optLong("audioBytes", 0L),
            audioCrc32 = json.optString("audioCrc32").ifBlank { null },
            fileCrc32 = json.optString("fileCrc32").ifBlank { json.optString("crc32").ifBlank { null } },
            firstSamples = json.optJSONArray("firstSamples").toIntList(),
            name = json.optString("name").ifBlank { null },
            fileSize = json.optLong("fileSize", 0L),
            durationMs = json.optLong("durationMs", 0L),
            codec = json.optString("codec").ifBlank { "pcm16" },
            sampleRate = json.optInt("sampleRate", 8_000),
            channels = json.optInt("channels", 1),
            packetMs = json.optInt("packetMs", 40),
            crc32 = json.optString("crc32").ifBlank { null },
            createdAt = json.optString("createdAt").ifBlank { null },
            storeTaskId = json.optString("storeTaskId").ifBlank { null },
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

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optInt(index))
        }
    }
}
