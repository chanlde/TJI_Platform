package com.tji.device.product.droppersixstage.mqtt

import android.util.Log
import com.tji.device.product.droppersixstage.model.DropperSixStageAck
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import org.json.JSONArray
import org.json.JSONObject

class DropperSixStageMqttInbound(
    private val repository: DropperSixStageRepository
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
            "identity" -> repository.updateState(parseIdentity(serialNumber, json))
            "offline" -> repository.updateOnlineStatus(
                serialNumber = serialNumber,
                isOnline = false,
                timestamp = json.optNullableLong("ts")
            )
            "state", "status" -> repository.updateState(parseState(serialNumber, json, allowOnline = !isRetained))
            "ack" -> repository.updateAck(serialNumber, parseAck(json))
            else -> Log.d(TAG, "六段抛投 MQTT 未处理 deviceId=$serialNumber event=$eventType")
        }
    }

    fun cleanup() = Unit

    private fun parseIdentity(
        serialNumber: String,
        json: JSONObject
    ): DropperSixStageState {
        val payloadDeviceId = json.optString("deviceId").ifBlank { serialNumber }
        return DropperSixStageState(
            serialNumber = payloadDeviceId,
            name = json.optString("name").ifBlank { json.optString("product").ifBlank { null } },
            isOnline = json.optBoolean("online", true),
            firmwareVersion = json.optString("fw").ifBlank {
                json.optString("firmware_version").ifBlank { null }
            },
            timestamp = json.optNullableLong("ts") ?: System.currentTimeMillis()
        )
    }

    private fun parseState(
        serialNumber: String,
        json: JSONObject,
        allowOnline: Boolean
    ): DropperSixStageState {
        return DropperSixStageState(
            serialNumber = serialNumber,
            name = json.optString("name").ifBlank { null },
            isOnline = allowOnline,
            stages = parseStages(json.optJSONArray("stages")),
            batteryPercent = json.optNullableInt("battery"),
            firmwareVersion = json.optString("firmware_version").ifBlank { null },
            timestamp = json.optNullableLong("ts")
        )
    }

    private fun parseStages(array: JSONArray?): List<DropperStageState> {
        if (array == null || array.length() == 0) return DropperStageState.defaults()
        val parsed = mutableListOf<DropperStageState>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parsed += DropperStageState(
                index = item.optInt("stage", index + 1),
                isOpen = item.optBoolean("open", false),
                payloadLoaded = item.optNullableBoolean("loaded")
            )
        }
        return DropperStageState.defaults().map { fallback ->
            parsed.firstOrNull { it.index == fallback.index } ?: fallback
        }
    }

    private fun parseAck(json: JSONObject): DropperSixStageAck {
        return DropperSixStageAck(
            msgId = json.optString("msgId"),
            ok = json.optBoolean("ok"),
            stage = json.optNullableInt("stage"),
            message = json.optString("msg").ifBlank { null }
        )
    }

    private companion object {
        const val TAG = "DropperSixMqttInbound"
    }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null
