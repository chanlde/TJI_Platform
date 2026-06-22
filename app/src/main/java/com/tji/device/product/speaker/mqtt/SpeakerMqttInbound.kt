package com.tji.device.product.speaker.mqtt

import android.util.Log
import com.tji.device.product.speaker.core.SpeakerMqttPayloadParser
import com.tji.device.product.speaker.repository.SpeakerRepository
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
            "state", "status" -> repository.updateState(
                SpeakerMqttPayloadParser.parseState(serialNumber, json, allowOnline = !isRetained)
            )
            "ack" -> repository.updateAck(serialNumber, SpeakerMqttPayloadParser.parseAck(json))
            "record_list" -> {
                val parsed = SpeakerMqttPayloadParser.parseRecordList(json)
                val records = parsed.records
                Log.d(
                    TAG,
                    "Speaker record list parsed deviceId=$serialNumber offset=${parsed.offset} " +
                        "limit=${parsed.limit} total=${parsed.total} " +
                        "count=${records.size} first=${records.firstOrNull()?.recordId.orEmpty()} " +
                        "last=${records.lastOrNull()?.recordId.orEmpty()}"
                )
                if (records.isEmpty() && parsed.total > 0) {
                    Log.w(TAG, "Speaker record list has total but parsed empty: ${json.toString().take(600)}")
                }
                repository.updateRecords(
                    serialNumber = serialNumber,
                    records = records,
                    offset = parsed.offset,
                    limit = parsed.limit,
                    total = parsed.total,
                    hasMore = parsed.hasMore,
                    timestamp = parsed.timestamp
                )
            }
            "storage_status" -> {
                val status = SpeakerMqttPayloadParser.parseStorageStatus(json)
                Log.d(
                    TAG,
                    "Speaker storage parsed deviceId=$serialNumber ok=${status.ok} " +
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
                val event = SpeakerMqttPayloadParser.parseRecordEvent(eventType, json)
                Log.d(
                    TAG,
                    "Speaker record event deviceId=$serialNumber type=${event.type} ok=${event.ok} " +
                        "code=${event.code} recordId=${event.recordId} progress=${event.progress} msg=${event.message}"
                )
                repository.updateRecordEvent(serialNumber, event)
            }
            else -> Log.d(TAG, "Speaker MQTT ignored deviceId=$serialNumber event=$eventType")
        }
    }

    fun cleanup() = Unit

    private companion object {
        const val TAG = "SpeakerMqttInbound"
    }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
