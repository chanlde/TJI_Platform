package com.tji.device.product.common

/**
 * 判断设备消息是否在同一时钟域内发生时间回退。
 *
 * MCU 可能上报启动时长、Unix 秒或 Unix 毫秒；不同域不能直接比较，否则 App 生成的 lifecycle
 * 墙钟时间会让后续 MCU uptime 状态永久被当成旧消息。
 */
fun isOlderDeviceTimestamp(incoming: Long?, current: Long?): Boolean {
    if (incoming == null || current == null) return false
    if (timestampDomain(incoming) != timestampDomain(current)) return false
    return incoming < current
}

/**
 * 合并设备时间戳：同一时钟域内只允许前进；缺失的新值沿用当前值。
 *
 * 不同时钟域仍接受新值，以免墙钟时间、Unix 秒和 MCU uptime 之间互相锁死。
 */
fun mergeDeviceTimestamp(current: Long?, incoming: Long?): Long? =
    if (isOlderDeviceTimestamp(incoming, current)) current else incoming ?: current

private fun timestampDomain(value: Long): TimestampDomain = when {
    value >= MIN_UNIX_MILLIS -> TimestampDomain.UnixMillis
    value >= MIN_UNIX_SECONDS -> TimestampDomain.UnixSeconds
    else -> TimestampDomain.DeviceUptime
}

private enum class TimestampDomain {
    DeviceUptime,
    UnixSeconds,
    UnixMillis
}

private const val MIN_UNIX_SECONDS = 1_000_000_000L
private const val MIN_UNIX_MILLIS = 1_000_000_000_000L
