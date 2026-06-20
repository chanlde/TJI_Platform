package com.tji.device.product.solarclean.mqtt

import android.util.Log
import com.tji.device.product.solarclean.model.SolarCleanAck
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanDownloadState
import com.tji.device.product.solarclean.model.SolarCleanEvent
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.product.solarclean.model.SolarCleanRouteSlot
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 光伏清洗 MQTT 入站。
 *
 * Topic 采用平台统一布局：`SolarClean/devices/{deviceId}/lifecycle|status|control`。
 * Payload 字段参考 Notion 光伏清洗文档：ack/state/event 语义保留，但不采用文档中的 topic 名。
 */
class SolarCleanMqttInbound(
    private val repository: SolarCleanRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeoutLock = Any()
    private val onlineTimeoutJobs = mutableMapOf<String, Job>()
    private val lastOnlineSignalAt = mutableMapOf<String, Long>()

    suspend fun handleEvent(
        linkSn: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean = false
    ) {
        when (eventType) {
            "online" -> {
                if (isRetained) {
                    Log.d(TAG, "忽略 retained online: deviceId=$linkSn")
                    return
                }
                val timestamp = json.optNullableLong("ts")
                repository.updateOnlineStatus(linkSn, isOnline = true, timestamp = timestamp)
                resetOnlineTimeout(linkSn)
                Log.d(TAG, "SolarClean online: deviceId=$linkSn ts=$timestamp")
            }
            "offline" -> {
                val timestamp = json.optNullableLong("ts")
                cancelOnlineTimeout(linkSn)
                repository.updateOnlineStatus(linkSn, isOnline = false, timestamp = timestamp)
                Log.d(TAG, "SolarClean offline: deviceId=$linkSn ts=$timestamp")
            }
            "ack" -> {
                val ack = parseAck(json)
                repository.updateAck(linkSn, ack)
                Log.d(TAG, "SolarClean ack: $ack")
            }
            "state" -> {
                val state = parseState(linkSn, json, allowRealtimeOnline = !isRetained)
                repository.updateDeviceState(state)
                if (state.isOnline) {
                    resetOnlineTimeout(linkSn)
                } else {
                    cancelOnlineTimeout(linkSn)
                }
                Log.d(TAG, "SolarClean state: $state")
            }
            "deviceInfo" -> {
                val info = parseDeviceInfo(linkSn, json)
                repository.updateDeviceInfo(linkSn, info)
                if (!isRetained && isFreshOrRealtimeSignal(info.timestamp)) {
                    repository.updateOnlineStatus(linkSn, isOnline = true, timestamp = onlineTimestamp(info.timestamp))
                    resetOnlineTimeout(linkSn)
                }
                Log.d(TAG, "SolarClean device info: $info")
            }
            "otaStatus" -> {
                val status = parseOtaStatus(json)
                repository.updateOtaStatus(linkSn, status)
                if (!isRetained) {
                    resetOnlineTimeout(linkSn)
                }
                Log.d(TAG, "SolarClean OTA status: $status")
            }
            "downloadProgress",
            "downloadDone",
            "downloadError",
            "routeExecuteStarted",
            "routeExecuteFinished" -> {
                val event = parseEvent(eventType, json)
                if (event != null) {
                    repository.updateEvent(linkSn, event)
                }
                Log.d(TAG, "SolarClean event: $event")
            }
            else -> Log.d(
                TAG,
                "SolarClean MQTT 未处理 link=$linkSn event=$eventType keys=${json.keys().asSequence().joinToString()}"
            )
        }
    }

    private fun parseAck(json: JSONObject): SolarCleanAck {
        return SolarCleanAck(
            msgId = json.optString("msgId"),
            ofType = json.optString("ofType"),
            ok = json.optBoolean("ok"),
            code = json.optInt("code"),
            message = json.optString("msg").ifBlank { null },
            routeSlots = json.optJSONObject("data")
                ?.optJSONArray("slots")
                ?.toRouteSlots()
                .orEmpty()
        )
    }

    private fun parseState(
        serialNumber: String,
        json: JSONObject,
        allowRealtimeOnline: Boolean
    ): SolarCleanDeviceState {
        val mqtt = json.optJSONObject("mqtt")
        val download = json.optJSONObject("download")?.let {
            SolarCleanDownloadState(
                slot = it.optInt("slot"),
                active = it.optBoolean("active"),
                percent = it.optDouble("pct"),
                bytes = it.optLong("bytes"),
                total = it.optLong("total")
            )
        }
        return SolarCleanDeviceState(
            serialNumber = serialNumber,
            isOnline = allowRealtimeOnline && isFreshOrRealtimeSignal(json.optNullableLong("ts")),
            latitude = json.optNullableDouble("lat"),
            longitude = json.optNullableDouble("lon"),
            altitudeMeters = json.optNullableDouble("alt"),
            speedMetersPerSecond = json.optNullableDouble("speed"),
            yawDegrees = json.optNullableDouble("yaw"),
            pitchDegrees = json.optNullableDouble("pitch"),
            rollDegrees = json.optNullableDouble("roll"),
            satelliteCount = json.optNullableInt("sat"),
            batteryPercent = json.optNullableDouble("battery"),
            waypointIndex = json.optNullableInt("waypoint"),
            waterLevel = json.optNullableInt("water"),
            mqttConnected = mqtt?.optBoolean("tcp"),
            mqttLastError = mqtt?.optNullableInt("lastErr"),
            download = download,
            timestamp = json.optNullableLong("ts")
        )
    }

    private fun parseDeviceInfo(serialNumber: String, json: JSONObject): SolarCleanDeviceInfo {
        val payload = json.payloadObject()
        return SolarCleanDeviceInfo(
            hardwareVersion = payload.optString("hardware_version").ifBlank {
                payload.optString("hardwareVersion").ifBlank {
                    payload.optString("hardware").ifBlank { null }
                }
            },
            firmwareVersion = payload.optString("firmware_version").ifBlank {
                payload.optString("firmwareVersion").ifBlank { null }
            },
            firmwareInnerVersion = payload.optNullableInt("inner_version")
                ?: payload.optNullableInt("innerVersion"),
            slot = payload.optString("slot").ifBlank { null },
            otaStatus = payload.optString("ota_status").ifBlank {
                payload.optString("otaStatus").ifBlank { null }
            },
            lastOtaResult = payload.optString("last_ota_result").ifBlank {
                payload.optString("lastOtaResult").ifBlank { null }
            },
            lastFailReason = payload.optString("last_fail_reason").ifBlank {
                payload.optString("lastFailReason").ifBlank { null }
            },
            batteryPercent = payload.optNullableInt("battery"),
            network = payload.optString("network").ifBlank { null },
            timestamp = payload.optNullableLong("ts") ?: json.optNullableLong("ts")
        )
    }

    private fun parseOtaStatus(json: JSONObject): SolarCleanOtaStatus {
        val payload = json.payloadObject()
        val status = payload.optString("ota_status").ifBlank {
            payload.optString("otaStatus").ifBlank {
                payload.optString("status").ifBlank { "UNKNOWN" }
            }
        }
        return SolarCleanOtaStatus(
            status = status,
            progress = payload.optNullableInt("progress"),
            targetVersion = payload.optString("target_version").ifBlank {
                payload.optString("targetVersion").ifBlank { null }
            },
            targetInnerVersion = payload.optNullableInt("target_inner_version")
                ?: payload.optNullableInt("targetInnerVersion"),
            firmwareVersion = payload.optString("firmware_version").ifBlank {
                payload.optString("firmwareVersion").ifBlank { null }
            },
            currentVersion = payload.optString("current_version").ifBlank {
                payload.optString("currentVersion").ifBlank { null }
            },
            failedVersion = payload.optString("failed_version").ifBlank { null },
            reason = payload.optString("reason").ifBlank { null },
            message = payload.optString("message").ifBlank {
                payload.optString("msg").ifBlank { null }
            },
            downloaded = payload.optNullableLong("downloaded"),
            total = payload.optNullableLong("total"),
            timestamp = payload.optNullableLong("ts") ?: json.optNullableLong("ts")
        )
    }

    private fun parseEvent(eventType: String, json: JSONObject): SolarCleanEvent? {
        return when (eventType) {
            "downloadProgress" -> SolarCleanEvent.DownloadProgress(
                slot = json.optInt("slot"),
                bytes = json.optLong("bytes"),
                total = json.optLong("total"),
                percent = json.optDouble("pct")
            )
            "downloadDone" -> SolarCleanEvent.DownloadDone(
                slot = json.optInt("slot"),
                size = json.optLong("size"),
                checksumOk = json.optNullableBoolean("checksumOk"),
                storedInFlash = json.optNullableBoolean("storedInFlash")
            )
            "downloadError" -> SolarCleanEvent.DownloadError(
                slot = json.optInt("slot"),
                code = json.optInt("code"),
                message = json.optString("msg").ifBlank { null },
                retries = json.optNullableInt("retries")
            )
            "routeExecuteStarted" -> SolarCleanEvent.RouteExecuteStarted(
                slot = json.optNullableInt("slot")
            )
            "routeExecuteFinished" -> SolarCleanEvent.RouteExecuteFinished(
                slot = json.optNullableInt("slot"),
                ok = json.optNullableBoolean("ok")
            )
            "online" -> SolarCleanEvent.Online(json.optNullableLong("ts"))
            "offline" -> SolarCleanEvent.Offline(json.optNullableLong("ts"))
            else -> null
        }
    }

    private fun isFreshOrRealtimeSignal(timestamp: Long?): Boolean {
        if (timestamp == null) return true
        if (!looksLikeUnixMillis(timestamp)) return true
        return System.currentTimeMillis() - timestamp <= STATE_ONLINE_TTL_MS
    }

    private fun onlineTimestamp(timestamp: Long?): Long {
        return timestamp?.takeIf(::looksLikeUnixMillis) ?: System.currentTimeMillis()
    }

    private fun looksLikeUnixMillis(timestamp: Long): Boolean {
        return timestamp >= MIN_REASONABLE_UNIX_MILLIS
    }

    private fun resetOnlineTimeout(serialNumber: String) {
        val signalAt = System.currentTimeMillis()
        synchronized(timeoutLock) {
            lastOnlineSignalAt[serialNumber] = signalAt
            onlineTimeoutJobs.remove(serialNumber)?.cancel()
            onlineTimeoutJobs[serialNumber] = scope.launch {
                markOfflineIfStale(serialNumber, signalAt)
            }
        }
    }

    private suspend fun markOfflineIfStale(serialNumber: String, signalAt: Long) {
        delay(STATE_ONLINE_TTL_MS)
        val shouldMarkOffline = synchronized(timeoutLock) {
            val latestSignalAt = lastOnlineSignalAt[serialNumber] ?: return@synchronized true
            val ageMs = System.currentTimeMillis() - latestSignalAt
            latestSignalAt == signalAt && ageMs >= STATE_ONLINE_TTL_MS
        }
        if (shouldMarkOffline) {
            repository.updateOnlineStatus(serialNumber, isOnline = false, timestamp = System.currentTimeMillis())
            Log.w(TAG, "SolarClean state timeout, mark offline: deviceId=$serialNumber")
            synchronized(timeoutLock) {
                if (lastOnlineSignalAt[serialNumber] == signalAt) {
                    onlineTimeoutJobs.remove(serialNumber)
                }
            }
        }
    }

    private fun cancelOnlineTimeout(serialNumber: String) {
        synchronized(timeoutLock) {
            lastOnlineSignalAt.remove(serialNumber)
            onlineTimeoutJobs.remove(serialNumber)?.cancel()
        }
    }

    fun cleanup() {
        synchronized(timeoutLock) {
            onlineTimeoutJobs.values.forEach { it.cancel() }
            onlineTimeoutJobs.clear()
            lastOnlineSignalAt.clear()
        }
        scope.cancel()
    }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun JSONObject.optNullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> optInt(name)
        }
    }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (has(name) && !isNull(name)) optBoolean(name) else null

    private fun JSONObject.payloadObject(): JSONObject =
        optJSONObject("data") ?: optJSONObject("params") ?: this

    private fun JSONArray.toRouteSlots(): List<SolarCleanRouteSlot> {
        return (0 until length()).map { index ->
            val slot = getJSONObject(index)
            SolarCleanRouteSlot(
                index = slot.optInt("index"),
                bytes = slot.optLong("bytes"),
                valid = slot.optBoolean("valid")
            )
        }
    }

    private companion object {
        const val TAG = "SolarCleanMqttInbound"
        const val STATE_ONLINE_TTL_MS = 10_000L
        const val MIN_REASONABLE_UNIX_MILLIS = 1_600_000_000_000L
    }
}
