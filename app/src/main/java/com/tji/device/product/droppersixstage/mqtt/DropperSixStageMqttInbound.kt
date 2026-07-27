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
            "identity" -> repository.updateState(
                parseIdentity(serialNumber, json, allowOnline = !isRetained)
            )
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
        json: JSONObject,
        allowOnline: Boolean
    ): DropperSixStageState {
        val payloadDeviceId = json.optString("deviceId").ifBlank { serialNumber }
        val current = repository.devices.value.firstOrNull { it.serialNumber == payloadDeviceId }
        return DropperSixStageState(
            serialNumber = payloadDeviceId,
            name = json.optString("name").ifBlank { json.optString("product").ifBlank { null } },
            isOnline = allowOnline && (
                if (json.has("online")) json.optNullableBoolean("online") == true else true
            ),
            stages = current?.stages ?: DropperStageState.defaults(),
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
        val current = repository.devices.value.firstOrNull { it.serialNumber == serialNumber }
        return DropperSixStageState(
            serialNumber = serialNumber,
            name = json.optString("name").ifBlank { null },
            isOnline = allowOnline,
            stages = parseStages(
                array = json.optJSONArray("stages"),
                currentStages = current?.stages ?: DropperStageState.defaults()
            ),
            batteryPercent = json.optNullableInt("battery"),
            firmwareVersion = json.optString("firmware_version").ifBlank { null },
            timestamp = json.optNullableLong("ts")
        )
    }

    private fun parseStages(
        array: JSONArray?,
        currentStages: List<DropperStageState>
    ): List<DropperStageState> {
        if (array == null || array.length() == 0) return currentStages
        val updates = mutableMapOf<Int, DropperStageState>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val stage = item.optNullableInt("stage") ?: (index + 1)
            val current = currentStages.firstOrNull { it.index == stage } ?: continue
            updates[stage] = current.copy(
                isOpen = item.optNullableBoolean("open") ?: current.isOpen,
                payloadLoaded = item.optNullableBoolean("loaded") ?: current.payloadLoaded
            )
        }
        return currentStages.map { current ->
            updates[current.index] ?: current
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
    if (has(name) && !isNull(name)) runCatching { getLong(name) }.getOrNull() else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) runCatching { getInt(name) }.getOrNull() else null

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (has(name) && !isNull(name)) runCatching { getBoolean(name) }.getOrNull() else null
