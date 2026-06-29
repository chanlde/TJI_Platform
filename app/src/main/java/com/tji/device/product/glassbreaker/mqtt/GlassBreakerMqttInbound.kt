package com.tji.device.product.glassbreaker.mqtt

import android.util.Log
import com.tji.device.product.glassbreaker.model.GlassBreakerAck
import com.tji.device.product.glassbreaker.model.GlassBreakerFireState
import com.tji.device.product.glassbreaker.model.GlassBreakerLockState
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import org.json.JSONObject

class GlassBreakerMqttInbound(
    private val repository: GlassBreakerRepository
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
            "state", "status" -> repository.updateState(parseState(serialNumber, json))
            "ack" -> repository.updateAck(serialNumber, parseAck(json))
            "otaAck" -> repository.updateOtaAck(serialNumber, parseAck(json))
            else -> Log.d(TAG, "GlassBreaker MQTT ignored: deviceId=$serialNumber event=$eventType")
        }
    }

    fun cleanup() = Unit

    private fun parseState(
        serialNumber: String,
        json: JSONObject
    ): GlassBreakerState {
        val payload = json.payloadObject()
        val deviceId = json.optFirstString(payload, "deviceId", "device_id").ifBlank { serialNumber }
        val selectedChannel = json.optFirstInt(payload, "selectedChannel", "selected_channel")
            ?.takeIf { it in 1..4 }
        return GlassBreakerState(
            serialNumber = deviceId,
            name = json.optFirstString(payload, "name", "product").ifBlank { null },
            isOnline = false,
            lockState = json.optFirstString(payload, "lockState", "lock_state").ifBlank { GlassBreakerLockState.Locked },
            selectedChannel = selectedChannel,
            laserEnabled = json.optFirstBoolean(payload, "laserEnabled", "laser_enabled") ?: false,
            fireState = json.optFirstString(payload, "fireState", "fire_state").ifBlank { GlassBreakerFireState.Idle },
            armRemainingMs = json.optFirstLong(payload, "armRemainingMs", "arm_remaining_ms"),
            batteryPercent = json.optFirstInt(payload, "battery", "batteryPercent", "battery_percent"),
            hardwareVersion = json.optFirstString(payload, "hardwareVersion", "hardware_version", "hardware").ifBlank { null },
            firmwareVersion = json.optFirstString(payload, "firmwareVersion", "firmware_version", "version").ifBlank { null },
            firmwareInnerVersion = json.optFirstInt(payload, "innerVersion", "inner_version"),
            timestamp = json.optFirstLong(payload, "ts", "timestamp")
        )
    }

    private fun parseAck(json: JSONObject): GlassBreakerAck {
        val payload = json.payloadObject()
        return GlassBreakerAck(
            msgId = json.optFirstString(payload, "msgId", "cmdId").ifBlank { "unknown-${System.currentTimeMillis()}" },
            ok = json.optFirstBoolean(payload, "ok") ?: false,
            code = json.optFirstInt(payload, "code"),
            message = json.optFirstString(payload, "msg", "message").ifBlank { null },
            ofCmd = json.optFirstInt(payload, "ofCmd", "cmd"),
            ofType = json.optFirstString(payload, "ofType", "cmdName", "command").ifBlank { null },
            lockState = json.optFirstString(payload, "lockState", "lock_state").ifBlank { null },
            selectedChannel = json.optFirstInt(payload, "selectedChannel", "selected_channel")?.takeIf { it in 1..4 },
            laserEnabled = json.optFirstBoolean(payload, "laserEnabled", "laser_enabled"),
            fireState = json.optFirstString(payload, "fireState", "fire_state").ifBlank { null },
            timestamp = json.optFirstLong(payload, "ts", "timestamp")
        )
    }

    private companion object {
        const val TAG = "GlassBreakerMqtt"
    }
}

private fun JSONObject.payloadObject(): JSONObject =
    optJSONObject("data") ?: optJSONObject("params") ?: this

private fun JSONObject.optFirstString(payload: JSONObject, vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        optString(key).ifBlank { payload.optString(key) }.ifBlank { null }
    }.orEmpty()

private fun JSONObject.optFirstBoolean(payload: JSONObject, vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { key ->
        when {
            has(key) && !isNull(key) -> optBoolean(key)
            payload.has(key) && !payload.isNull(key) -> payload.optBoolean(key)
            else -> null
        }
    }

private fun JSONObject.optFirstInt(payload: JSONObject, vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { key ->
        when {
            has(key) && !isNull(key) -> optInt(key)
            payload.has(key) && !payload.isNull(key) -> payload.optInt(key)
            else -> null
        }
    }

private fun JSONObject.optFirstLong(payload: JSONObject, vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key ->
        when {
            has(key) && !isNull(key) -> optLong(key)
            payload.has(key) && !payload.isNull(key) -> payload.optLong(key)
            else -> null
        }
    }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
