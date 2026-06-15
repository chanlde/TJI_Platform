package com.tji.device.product.ota

import org.json.JSONObject
import kotlin.math.roundToInt

object ProductOtaMqttParser {
    fun resolveEventType(json: JSONObject): String {
        val rawType = json.optFirstString(
            "event_type",
            "eventType",
            "type",
            "cmdName",
            "command"
        )
        val key = rawType.normalizedEventKey()
        return when {
            key in DEVICE_INFO_ALIASES -> "deviceInfo"
            key in OTA_STATUS_ALIASES -> "otaStatus"
            rawType.equals("status", ignoreCase = true) && hasDeviceInfoPayload(json) -> "deviceInfo"
            rawType.equals("status", ignoreCase = true) && hasOtaStatusPayload(json) -> "otaStatus"
            rawType.isNotBlank() -> rawType
            hasDeviceInfoPayload(json) -> "deviceInfo"
            hasOtaStatusPayload(json) -> "otaStatus"
            else -> ""
        }
    }

    fun hasDeviceInfoPayload(json: JSONObject): Boolean {
        return json.hasAny(
            "hardwareVersion",
            "hardware_version",
            "hardware",
            "firmwareVersion",
            "firmware_version",
            "innerVersion",
            "inner_version",
            "lastOtaResult",
            "last_ota_result",
            "lastFailReason",
            "last_fail_reason"
        )
    }

    fun hasOtaStatusPayload(json: JSONObject): Boolean {
        return json.hasAny(
            "otaStatus",
            "ota_status",
            "progress",
            "targetVersion",
            "target_version",
            "targetInnerVersion",
            "target_inner_version",
            "downloaded",
            "downloadedBytes",
            "total",
            "fileSize"
        )
    }

    fun parseDeviceInfo(json: JSONObject): ProductDeviceInfo? {
        if (!hasDeviceInfoPayload(json)) return null
        return ProductDeviceInfo(
            hardwareVersion = json.optFirstString(
                "hardwareVersion",
                "hardware_version",
                "hardware"
            ).ifBlank { null },
            firmwareVersion = json.optFirstString(
                "firmwareVersion",
                "firmware_version",
                "version"
            ).ifBlank { null },
            firmwareInnerVersion = json.optFirstInt("innerVersion", "inner_version"),
            otaStatus = json.optFirstString("otaStatus", "ota_status", "status").ifBlank { null },
            lastOtaResult = json.optFirstString("lastOtaResult", "last_ota_result").ifBlank { null },
            lastFailReason = json.optFirstString("lastFailReason", "last_fail_reason").ifBlank { null },
            batteryPercent = json.optFirstInt("battery", "batteryPercent", "battery_percent"),
            timestamp = json.optFirstLong("ts", "timestamp")
        )
    }

    fun parseOtaStatus(json: JSONObject): ProductOtaStatus? {
        if (!hasOtaStatusPayload(json)) return null
        val status = json.optFirstString("otaStatus", "ota_status", "status").ifBlank { "UNKNOWN" }
        return ProductOtaStatus(
            status = status,
            cmdId = json.optFirstString("cmdId", "msgId").ifBlank { null },
            seq = json.optFirstLong("seq"),
            progress = json.optFirstProgressPercent("progress", "pct", "percent"),
            targetVersion = json.optFirstString("targetVersion", "target_version").ifBlank { null },
            targetInnerVersion = json.optFirstInt("targetInnerVersion", "target_inner_version"),
            firmwareVersion = json.optFirstString(
                "firmwareVersion",
                "firmware_version",
                "currentVersion",
                "current_version"
            ).ifBlank { null },
            reason = json.optFirstString("reason").ifBlank { null },
            message = json.optFirstString("message", "msg").ifBlank { null },
            downloaded = json.optFirstLong("downloaded", "downloadedBytes", "downloaded_bytes"),
            total = json.optFirstLong("total", "fileSize", "file_size"),
            timestamp = json.optFirstLong("ts", "timestamp")
        )
    }

    private fun JSONObject.hasAny(vararg keys: String): Boolean {
        val payload = payloadObject()
        return keys.any { has(it) || payload.has(it) }
    }

    private fun JSONObject.optFirstString(vararg keys: String): String {
        val payload = payloadObject()
        return keys.firstNotNullOfOrNull { key ->
            optString(key).ifBlank {
                payload.optString(key)
            }.ifBlank { null }
        }.orEmpty()
    }

    private fun JSONObject.optFirstInt(vararg keys: String): Int? {
        val payload = payloadObject()
        return keys.firstNotNullOfOrNull { key ->
            when {
                has(key) -> optInt(key)
                payload.has(key) -> payload.optInt(key)
                else -> null
            }
        }
    }

    private fun JSONObject.optFirstLong(vararg keys: String): Long? {
        val payload = payloadObject()
        return keys.firstNotNullOfOrNull { key ->
            when {
                has(key) -> optLong(key)
                payload.has(key) -> payload.optLong(key)
                else -> null
            }
        }
    }

    private fun JSONObject.optFirstProgressPercent(vararg keys: String): Int? {
        val payload = payloadObject()
        return keys.firstNotNullOfOrNull { key ->
            val value = when {
                has(key) -> opt(key)
                payload.has(key) -> payload.opt(key)
                else -> null
            } ?: return@firstNotNullOfOrNull null
            value.toProgressPercent()
        }
    }

    private fun Any.toProgressPercent(): Int? {
        val number = when (this) {
            is Number -> toDouble()
            is String -> trim().removeSuffix("%").toDoubleOrNull()
            else -> null
        } ?: return null
        val percent = if (number in 0.0..1.0) number * 100 else number
        return percent.roundToInt().coerceIn(0, 100)
    }

    private fun JSONObject.payloadObject(): JSONObject {
        return optJSONObject("data") ?: optJSONObject("params") ?: this
    }

    private fun String.normalizedEventKey(): String {
        return trim()
            .replace("_", "")
            .replace("-", "")
            .lowercase()
    }

    private val DEVICE_INFO_ALIASES = setOf(
        "deviceinfo",
        "getdeviceinfo",
        "info"
    )

    private val OTA_STATUS_ALIASES = setOf(
        "otastatus",
        "otainfo",
        "otaprogress"
    )
}
