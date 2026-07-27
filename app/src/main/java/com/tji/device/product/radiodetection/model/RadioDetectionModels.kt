package com.tji.device.product.radiodetection.model

import com.tji.device.product.runtime.ProductRuntimePayload

data class RadioDetectionRuntime(
    val serialNumber: String,
    val displayName: String,
    val isOnline: Boolean,
    val targetCount: Int,
    val lastUpdateText: String
) : ProductRuntimePayload

data class RadioDetectionUiState(
    val deviceName: String,
    val deviceSerial: String,
    val networkStatus: String,
    val payloadStatus: String,
    val detectionRange: String,
    val onlineDeviceCount: Int,
    val encryptedLinkEnabled: Boolean,
    val runningDuration: String,
    val currentCoordinate: RadioCoordinate,
    val targets: List<RadioDetectionTarget>,
    val pilot: RadioPilot,
    val tracks: List<RadioTrackRecord>,
    val zones: List<RadioWarningZone>,
    val enforcementRecords: List<RadioEnforcementRecord>,
    val listEntries: List<RadioListEntry>,
    val rgbAck: RadioRgbAck? = null
) {
    val discoveredTargetCount: Int get() = targets.size
    val blacklistCount: Int get() = targets.count { it.listStatus == RadioListStatus.Blacklist }
    val whitelistCount: Int get() = targets.count { it.listStatus == RadioListStatus.Whitelist }
    val unknownCount: Int get() = targets.count { it.listStatus == RadioListStatus.Unknown }
}

data class RadioCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double
) {
    fun compactText(): String =
        "纬度 %.6f / 经度 %.6f".format(latitude, longitude)
}

data class RadioDetectionTarget(
    val id: String,
    val name: String,
    val type: String,
    val serialNumber: String,
    val listStatus: RadioListStatus,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int,
    val speedMetersPerSecond: Int,
    val headingDegrees: Int,
    val frequencyLabel: String,
    val signalLevel: RadioSignalLevel,
    /** 本机墙钟接收时间，仅用于向用户展示“几秒前”。 */
    val lastSeenAtMillis: Long,
    val pilotName: String,
    val pilotLatitude: Double,
    val pilotLongitude: Double,
    val pilotDistanceText: String,
    val mapXPercent: Float,
    val mapYPercent: Float,
    /**
     * 设备在 RID 包中提供的采样时间，仅用于拒绝乱序包。
     * 与 [lastSeenAtMillis] 的本机接收时间分开，避免网络延迟让旧坐标覆盖新坐标。
     */
    val sourceTimestampMillis: Long? = null,
    /**
     * 本机单调时钟接收时间，仅用于 TTL。手机墙钟校时不能让目标提前过期或长期存活。
     * 默认值兼容预览和旧测试构造；生产数据由仓库显式写入。
     */
    val lastSeenElapsedMillis: Long = lastSeenAtMillis
) {
    val lastSeenText: String
        get() {
            val elapsedSeconds = ((System.currentTimeMillis() - lastSeenAtMillis).coerceAtLeast(0L) / 1_000L)
            return when {
                elapsedSeconds < 5 -> "刚刚"
                elapsedSeconds < 60 -> "${elapsedSeconds}秒前"
                else -> "${elapsedSeconds / 60}分钟前"
            }
        }
}

enum class RadioListStatus(val label: String) {
    Blacklist("黑名单"),
    Whitelist("白名单"),
    Unknown("未知")
}

enum class RadioSignalLevel(val label: String) {
    Strong("强"),
    Medium("中"),
    Weak("弱")
}

data class RadioPilot(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceToPrimaryTarget: String,
    val mapXPercent: Float,
    val mapYPercent: Float
)

data class RadioTrackRecord(
    val targetName: String,
    val serialNumber: String,
    val timeRange: String,
    val duration: String,
    val pointCount: Int,
    val maxAltitudeMeters: Int,
    val maxSpeedMetersPerSecond: Int,
    val status: RadioListStatus
)

data class RadioWarningZone(
    val name: String,
    val shape: String,
    val level: String,
    val center: String,
    val range: String,
    val enabled: Boolean,
    val createdAt: String
)

data class RadioEnforcementRecord(
    val recordNumber: String,
    val targetName: String,
    val targetSerialNumber: String,
    val status: String,
    val handledAt: String,
    val location: String,
    val operator: String,
    val note: String
)

data class RadioListEntry(
    val serialNumber: String,
    val type: String,
    val maker: String,
    val status: RadioListStatus,
    val reason: String,
    val createdBy: String,
    val createdAt: String
)

enum class RadioDetectionTab(val label: String) {
    Monitor("监控"),
    Tracks("轨迹"),
    Zones("预警区"),
    Enforcement("执法"),
    Lists("名单")
}

object RadioDetectionSampleData {
    fun uiState(deviceName: String, deviceSerial: String): RadioDetectionUiState {
        return RadioDetectionUiState(
            deviceName = deviceName.ifBlank { "频谱检测仪" },
            deviceSerial = deviceSerial.ifBlank { "T1640618D" },
            networkStatus = "等待数据",
            payloadStatus = "等待 RID",
            detectionRange = "1km",
            onlineDeviceCount = 0,
            encryptedLinkEnabled = false,
            runningDuration = "--",
            currentCoordinate = RadioCoordinate(0.0, 0.0, 0.0),
            targets = emptyList(),
            pilot = RadioPilot(
                name = "",
                latitude = 0.0,
                longitude = 0.0,
                distanceToPrimaryTarget = "-",
                mapXPercent = 0.5f,
                mapYPercent = 0.5f
            ),
            tracks = emptyList(),
            zones = emptyList(),
            enforcementRecords = emptyList(),
            listEntries = emptyList()
        )
    }
}
