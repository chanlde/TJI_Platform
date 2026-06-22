package com.tji.device.product.solarclean.model

import com.tji.device.product.runtime.ProductRuntimePayload

/**
 * 光伏清洗运行时状态。不复用消防吊桶 Link/Switch 模型。
 */
data class SolarCleanDeviceState(
    val serialNumber: String,
    val isOnline: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val yawDegrees: Double? = null,
    val pitchDegrees: Double? = null,
    val rollDegrees: Double? = null,
    val satelliteCount: Int? = null,
    val batteryPercent: Double? = null,
    val waypointIndex: Int? = null,
    val waterLevel: Int? = null,
    val mqttConnected: Boolean? = null,
    val mqttLastError: Int? = null,
    val download: SolarCleanDownloadState? = null,
    val deviceInfo: SolarCleanDeviceInfo? = null,
    val otaStatus: SolarCleanOtaStatus? = null,
    val routeSlots: List<SolarCleanRouteSlot> = emptyList(),
    val lastEvent: SolarCleanEvent? = null,
    val lastAck: SolarCleanAck? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload

data class SolarCleanDownloadState(
    val slot: Int,
    val active: Boolean,
    val percent: Double,
    val bytes: Long,
    val total: Long
)

data class SolarCleanDeviceInfo(
    val hardwareVersion: String?,
    val firmwareVersion: String?,
    val firmwareInnerVersion: Int? = null,
    val slot: String? = null,
    val otaStatus: String? = null,
    val lastOtaResult: String? = null,
    val lastFailReason: String? = null,
    val batteryPercent: Int? = null,
    val network: String? = null,
    val timestamp: Long? = null
)

data class SolarCleanOtaStatus(
    val status: String,
    val progress: Int? = null,
    val targetVersion: String? = null,
    val targetInnerVersion: Int? = null,
    val firmwareVersion: String? = null,
    val currentVersion: String? = null,
    val failedVersion: String? = null,
    val reason: String? = null,
    val message: String? = null,
    val downloaded: Long? = null,
    val total: Long? = null,
    val timestamp: Long? = null
)

data class SolarCleanAck(
    val msgId: String,
    val ofType: String,
    val ok: Boolean,
    val code: Int,
    val message: String?,
    val routeSlots: List<SolarCleanRouteSlot> = emptyList()
)

data class SolarCleanRouteSlot(
    val index: Int,
    val bytes: Long,
    val valid: Boolean
)

object SolarCleanCommandCode {
    const val PING = 0
    const val GET_DEVICE_INFO = 1
    const val SET_PUMP = 2
    const val SET_PUMP_PRESSURE = 3
    const val SET_SPRAY_ANGLE = 4
    const val SET_SWING_SPEED = 5
    const val SET_SERVO_SWING = 6
    const val ROUTE_LIST = 30
    const val ROUTE_DELETE = 31
    const val ROUTE_DOWNLOAD = 32
    const val ROUTE_DOWNLOAD_CANCEL = 33
    const val EXECUTE_SLOT = 34
}

sealed interface SolarCleanEvent {
    data class Online(val timestamp: Long?) : SolarCleanEvent

    data class Offline(val timestamp: Long?) : SolarCleanEvent

    data class DownloadProgress(
        val slot: Int,
        val bytes: Long,
        val total: Long,
        val percent: Double
    ) : SolarCleanEvent

    data class DownloadDone(
        val slot: Int,
        val size: Long,
        val checksumOk: Boolean?,
        val storedInFlash: Boolean?
    ) : SolarCleanEvent

    data class DownloadError(
        val slot: Int,
        val code: Int,
        val message: String?,
        val retries: Int?
    ) : SolarCleanEvent

    data class RouteExecuteStarted(val slot: Int?) : SolarCleanEvent

    data class RouteExecuteFinished(val slot: Int?, val ok: Boolean?) : SolarCleanEvent
}

sealed interface SolarCleanCommand {
    val msgId: String

    data class Ping(override val msgId: String) : SolarCleanCommand

    data class PumpSwitch(
        override val msgId: String,
        val on: Boolean
    ) : SolarCleanCommand

    data class PumpPressure(
        override val msgId: String,
        val percent: Double
    ) : SolarCleanCommand

    data class SprayAngle(
        override val msgId: String,
        val amplitudeDeg: Double
    ) : SolarCleanCommand

    data class ServoSwing(
        override val msgId: String,
        val on: Boolean,
        val speedPercent: Double? = null,
        val amplitude: Int? = null
    ) : SolarCleanCommand

    data class SwingSpeed(
        override val msgId: String,
        val speedPercent: Double
    ) : SolarCleanCommand

    data class GetDeviceInfo(override val msgId: String) : SolarCleanCommand

    data class RouteList(override val msgId: String) : SolarCleanCommand

    data class RouteDelete(
        override val msgId: String,
        val slot: Int
    ) : SolarCleanCommand

    data class RouteDownload(
        override val msgId: String,
        val slot: Int,
        val url: String,
        val size: Long,
        val checksum: String? = null
    ) : SolarCleanCommand

    data class RouteDownloadCancel(
        override val msgId: String,
        val slot: Int? = null
    ) : SolarCleanCommand

    data class ExecuteSlot(
        override val msgId: String,
        val slot: Int
    ) : SolarCleanCommand
}
