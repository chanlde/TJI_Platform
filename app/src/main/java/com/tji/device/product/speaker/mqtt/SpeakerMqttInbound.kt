package com.tji.device.product.speaker.mqtt

import com.tji.device.product.speaker.core.SpeakerLogger
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
            "online" -> if (!isRetained) {
                repository.updateOnlineStatus(
                    serialNumber = serialNumber,
                    isOnline = true,
                    timestamp = json.optNullableLong("ts")
                )
            }
            "offline" -> repository.updateOnlineStatus(
                serialNumber = serialNumber,
                isOnline = false,
                timestamp = json.optNullableLong("ts")
            )
            "state", "status" -> {
                val current = repository.devices.value.firstOrNull { it.serialNumber == serialNumber }
                val parsed = SpeakerMqttPayloadParser.parseState(
                    serialNumber = serialNumber,
                    json = json,
                    allowOnline = !isRetained,
                    current = current
                )
                val currentOnline = current?.isOnline
                repository.updateState(
                    if (isRetained && currentOnline != null) parsed.copy(isOnline = currentOnline) else parsed
                )
            }
            "ack" -> repository.updateAck(serialNumber, SpeakerMqttPayloadParser.parseAck(json))
            "record_list" -> {
                val parsed = SpeakerMqttPayloadParser.parseRecordList(json)
                val records = parsed.records
                if (records.isEmpty() && parsed.total > 0) {
                    SpeakerLogger.warn(TAG, "Speaker record list has total but parsed empty")
                }
                repository.updateRecords(
                    serialNumber = serialNumber,
                    records = records,
                    offset = parsed.offset,
                    limit = parsed.limit,
                    total = parsed.total,
                    hasMore = parsed.hasMore,
                    timestamp = parsed.timestamp,
                    nextOffset = parsed.nextOffset
                )
            }
            "storage_status" -> {
                val status = SpeakerMqttPayloadParser.parseStorageStatus(json)
                repository.updateStorageStatus(serialNumber, status)
            }
            "record_saved",
            "record_failed",
            "record_progress",
            "record_updated",
            "record_deleted",
            "record_playback" -> {
                val event = SpeakerMqttPayloadParser.parseRecordEvent(eventType, json)
                repository.updateRecordEvent(serialNumber, event)
            }
            else -> Unit
        }
    }

    fun cleanup() = Unit

    private companion object {
        const val TAG = "SpeakerMqttInbound"
    }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
