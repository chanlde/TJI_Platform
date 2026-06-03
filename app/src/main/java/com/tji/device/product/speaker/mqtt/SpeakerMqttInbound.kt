package com.tji.device.product.speaker.mqtt

import android.util.Log
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerDeviceState
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
            "online" -> {
                if (!isRetained) {
                    repository.updateOnlineStatus(serialNumber, isOnline = true, timestamp = json.optNullableLong("ts"))
                }
            }
            "offline" -> repository.updateOnlineStatus(
                serialNumber = serialNumber,
                isOnline = false,
                timestamp = json.optNullableLong("ts")
            )
            "state", "status" -> repository.updateState(parseState(serialNumber, json, allowOnline = !isRetained))
            "ack" -> repository.updateAck(serialNumber, parseAck(json))
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
            msgId = json.optString("msgId"),
            ofType = json.optString("ofType").ifBlank { json.optString("cmdName") },
            ofCmd = json.optInt("ofCmd", json.optInt("cmd", -1)),
            ok = json.optBoolean("ok"),
            code = json.optInt("code", -1),
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
