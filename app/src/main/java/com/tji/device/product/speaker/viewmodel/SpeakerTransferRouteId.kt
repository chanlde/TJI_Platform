package com.tji.device.product.speaker.viewmodel

import java.util.Locale

private const val MAX_SPEAKER_ROUTE_ID_BYTES = 32

/**
 * Builds an ASCII-only UDP route id while retaining the timestamp suffix used for uniqueness.
 */
internal fun speakerTransferRouteId(
    prefix: String,
    serialNumber: String,
    timestampMillis: Long
): String {
    require(timestampMillis >= 0) { "timestampMillis 不能为负数" }

    val cleanPrefix = prefix
        .filter { it.isAsciiLetterOrDigit() }
        .uppercase(Locale.US)
        .ifBlank { "ID" }
    val suffix = java.lang.Long.toString(timestampMillis, 36).uppercase(Locale.US)
    val maxDeviceLength = MAX_SPEAKER_ROUTE_ID_BYTES - cleanPrefix.length - suffix.length - 2
    require(maxDeviceLength > 0) { "传输 ID 前缀过长" }

    val cleanDeviceId = serialNumber
        .filter { it.isAsciiLetterOrDigit() }
        .ifBlank { "DEVICE" }
        .take(maxDeviceLength)
    return "${cleanPrefix}_${cleanDeviceId}_$suffix"
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
