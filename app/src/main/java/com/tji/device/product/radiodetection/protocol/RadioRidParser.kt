package com.tji.device.product.radiodetection.protocol

import org.json.JSONObject

data class RadioRidPacket(
    val sourceDeviceId: String?,
    val targetId: String,
    val rssi: Int,
    val frequencyCode: Int?,
    val frequencyLabel: String,
    val uaType: Int?,
    val status: Int?,
    val headingDegrees: Int?,
    val speedMetersPerSecond: Double?,
    val verticalSpeedMetersPerSecond: Double?,
    val droneLongitude: Double,
    val droneLatitude: Double,
    val altitudeGeoMeters: Double?,
    val altitudeBaroMeters: Double?,
    val heightMeters: Double?,
    val operatorLongitude: Double,
    val operatorLatitude: Double,
    val operatorAltitudeMeters: Double?,
    val timestampMillis: Long?
)

object RadioRidParser {
    fun parse(rawPayload: String): RadioRidPacket? {
        val payload = decodeHexPayload(rawPayload) ?: return null
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null

        return when {
            json.optString("type").equals("rid", ignoreCase = true) -> parseEsp32Rid(json)
            json.has("data") -> parsePsdkRid(json)
            else -> null
        }
    }

    private fun decodeHexPayload(rawPayload: String): String? {
        val compactHex = rawPayload.filterNot { it.isWhitespace() }
        if (compactHex.length < 2 || compactHex.length % 2 != 0) return null
        if (!compactHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

        return runCatching {
            val bytes = ByteArray(compactHex.length / 2) { index ->
                compactHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            bytes.toString(Charsets.UTF_8).trim().takeIf { it.startsWith("{") }
        }.getOrNull()
    }

    private fun parsePsdkRid(json: JSONObject): RadioRidPacket? {
        val data = json.optJSONObject("data") ?: return null
        val osid = data.optString("osid").trim().ifBlank { return null }
        val frequency = data.optNullableInt("Fre")

        return RadioRidPacket(
            sourceDeviceId = json.optString("devId").ifBlank { null },
            targetId = osid,
            rssi = data.optNullableInt("RSSI") ?: 0,
            frequencyCode = frequency,
            frequencyLabel = frequency?.let(::psdkFrequencyLabel) ?: "未知",
            uaType = data.optNullableInt("UAType"),
            status = data.optNullableInt("Status"),
            headingDegrees = data.optNullableInt("Heading"),
            speedMetersPerSecond = data.optNullableDouble("Speed"),
            verticalSpeedMetersPerSecond = data.optNullableDouble("Uprate"),
            droneLongitude = data.optFirstNullableDouble("Lon", "lon", "drone_lon", "Drone_Lon") ?: 0.0,
            droneLatitude = data.optFirstNullableDouble("Lat", "lat", "drone_lat", "Drone_Lat") ?: 0.0,
            altitudeGeoMeters = data.optNullableDouble("AltGeo"),
            altitudeBaroMeters = data.optNullableDouble("AltBaro"),
            heightMeters = data.optNullableDouble("Height"),
            operatorLongitude = data.optFirstNullableDouble(
                "Op_Lon",
                "op_lon",
                "operator_lon",
                "Operator_Lon",
                "pilot_lon",
                "Pilot_Lon"
            ) ?: 0.0,
            operatorLatitude = data.optFirstNullableDouble(
                "Op_Lat",
                "op_lat",
                "operator_lat",
                "Operator_Lat",
                "pilot_lat",
                "Pilot_Lat"
            ) ?: 0.0,
            operatorAltitudeMeters = data.optFirstNullableDouble("Op_Alt", "op_alt", "operator_alt", "Operator_Alt", "pilot_alt"),
            timestampMillis = data.optNullableLong("UATime")?.normalizeTimestampMillis()
        )
    }

    private fun parseEsp32Rid(json: JSONObject): RadioRidPacket? {
        val targetId = json.optString("sn").ifBlank {
            json.optString("operator_id").ifBlank {
                json.optString("mac")
            }
        }.trim()
        if (targetId.isBlank()) return null

        val channel = json.optNullableInt("channel")
        return RadioRidPacket(
            sourceDeviceId = json.optString("devId").ifBlank { null },
            targetId = targetId,
            rssi = json.optNullableInt("rssi") ?: 0,
            frequencyCode = channel,
            frequencyLabel = esp32FrequencyLabel(channel),
            uaType = json.optNullableInt("ua_type"),
            status = json.optNullableInt("status"),
            headingDegrees = json.optNullableInt("heading"),
            speedMetersPerSecond = json.optNullableDouble("speed"),
            verticalSpeedMetersPerSecond = json.optNullableDouble("vertical_speed"),
            droneLongitude = json.optFirstNullableDouble("drone_lon", "Lon", "lon") ?: 0.0,
            droneLatitude = json.optFirstNullableDouble("drone_lat", "Lat", "lat") ?: 0.0,
            altitudeGeoMeters = json.optNullableDouble("alt_geo"),
            altitudeBaroMeters = json.optNullableDouble("alt_baro"),
            heightMeters = json.optNullableDouble("height"),
            operatorLongitude = json.optFirstNullableDouble("operator_lon", "op_lon", "pilot_lon", "Op_Lon") ?: 0.0,
            operatorLatitude = json.optFirstNullableDouble("operator_lat", "op_lat", "pilot_lat", "Op_Lat") ?: 0.0,
            operatorAltitudeMeters = json.optFirstNullableDouble("operator_alt", "op_alt", "pilot_alt", "Op_Alt"),
            timestampMillis = json.optNullableLong("timestamp")?.normalizeTimestampMillis()
        )
    }

    private fun psdkFrequencyLabel(code: Int): String = when (code) {
        0 -> "5.8GHz"
        1, 4, 5 -> "2.4GHz"
        2 -> "蓝牙"
        else -> "未知"
    }

    private fun esp32FrequencyLabel(channel: Int?): String = when (channel) {
        null -> "未知"
        in 1..14 -> "2.4GHz"
        else -> "5GHz"
    }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return runCatching { getInt(name) }.getOrNull()
}

private fun JSONObject.optNullableLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { getLong(name) }.getOrNull()
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return runCatching { getDouble(name) }.getOrNull()
}

private fun JSONObject.optFirstNullableDouble(vararg names: String): Double? =
    names.firstNotNullOfOrNull { name -> optNullableDouble(name) }

private fun Long.normalizeTimestampMillis(): Long =
    if (this in 1..9_999_999_999L) this * 1000 else this
