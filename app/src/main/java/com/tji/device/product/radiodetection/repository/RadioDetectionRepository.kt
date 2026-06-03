package com.tji.device.product.radiodetection.repository

import android.util.Log
import com.tji.device.product.radiodetection.map.RadioCoordinateTransform
import com.tji.device.product.radiodetection.map.RadioMapCoordinate
import com.tji.device.product.radiodetection.model.RadioCoordinate
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioSignalLevel
import com.tji.device.product.radiodetection.protocol.RadioRidPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RadioDetectionDeviceState(
    val serialNumber: String,
    val displayName: String,
    val isOnline: Boolean,
    val payloadStatus: String,
    val currentCoordinate: RadioCoordinate?,
    val targets: List<RadioDetectionTarget>,
    val lastUpdateMillis: Long?,
    val filteredMessageCount: Int = 0,
    val rgbAck: RadioRgbAck? = null
)

interface RadioDetectionRepository {
    val devices: StateFlow<List<RadioDetectionDeviceState>>

    suspend fun upsertRidPacket(serialNumber: String, packet: RadioRidPacket)

    suspend fun updatePayloadStatus(serialNumber: String, payloadStatus: String, filtered: Boolean = false)

    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean)

    suspend fun updateRgbAck(serialNumber: String, ack: RadioRgbAck)

    fun clearDevices()
}

class RadioDetectionRepo : RadioDetectionRepository {
    private val _devices = MutableStateFlow<List<RadioDetectionDeviceState>>(emptyList())
    override val devices: StateFlow<List<RadioDetectionDeviceState>> = _devices.asStateFlow()

    override suspend fun upsertRidPacket(serialNumber: String, packet: RadioRidPacket) {
        val now = System.currentTimeMillis()
        _devices.update { current ->
            val existingDevice = current.firstOrNull { it.serialNumber == serialNumber }
            val target = packet.toTarget(existingDevice?.targets?.firstOrNull { it.id == packet.targetId })
            val nextDevice = RadioDetectionDeviceState(
                serialNumber = serialNumber,
                displayName = existingDevice?.displayName ?: "频谱检测仪",
                isOnline = true,
                payloadStatus = "已收到 RID",
                currentCoordinate = packet.operatorCoordinateOrNull() ?: existingDevice?.currentCoordinate,
                targets = existingDevice
                    ?.targets
                    .orEmpty()
                    .upsert(target) { it.id == target.id },
                lastUpdateMillis = now,
                filteredMessageCount = existingDevice?.filteredMessageCount ?: 0,
                rgbAck = existingDevice?.rgbAck
            )
            current.upsert(nextDevice) { it.serialNumber == serialNumber }
        }
    }

    override suspend fun updatePayloadStatus(serialNumber: String, payloadStatus: String, filtered: Boolean) {
        _devices.update { current ->
            val existing = current.firstOrNull { it.serialNumber == serialNumber }
            val next = existing?.copy(
                payloadStatus = payloadStatus,
                filteredMessageCount = existing.filteredMessageCount + if (filtered) 1 else 0
            ) ?: RadioDetectionDeviceState(
                serialNumber = serialNumber,
                displayName = "频谱检测仪",
                isOnline = false,
                payloadStatus = payloadStatus,
                currentCoordinate = null,
                targets = emptyList(),
                lastUpdateMillis = null,
                filteredMessageCount = if (filtered) 1 else 0,
                rgbAck = null
            )
            current.upsert(next) { it.serialNumber == serialNumber }
        }
    }

    override suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean) {
        _devices.update { current ->
            val existing = current.firstOrNull { it.serialNumber == serialNumber }
            val next = existing?.copy(isOnline = isOnline)
                ?: RadioDetectionDeviceState(
                    serialNumber = serialNumber,
                    displayName = "频谱检测仪",
                    isOnline = isOnline,
                    payloadStatus = if (isOnline) "等待 RID" else "离线",
                    currentCoordinate = null,
                    targets = emptyList(),
                    lastUpdateMillis = null
                )
            current.upsert(next) { it.serialNumber == serialNumber }
        }
    }

    override suspend fun updateRgbAck(serialNumber: String, ack: RadioRgbAck) {
        val now = System.currentTimeMillis()
        _devices.update { current ->
            val existing = current.firstOrNull { it.serialNumber == serialNumber }
            val next = existing?.copy(
                isOnline = true,
                payloadStatus = if (ack.ok) "灯语已确认" else "灯语失败 ${ack.code}",
                lastUpdateMillis = now,
                rgbAck = ack
            ) ?: RadioDetectionDeviceState(
                serialNumber = serialNumber,
                displayName = "频谱检测仪",
                isOnline = true,
                payloadStatus = if (ack.ok) "灯语已确认" else "灯语失败 ${ack.code}",
                currentCoordinate = null,
                targets = emptyList(),
                lastUpdateMillis = now,
                rgbAck = ack
            )
            current.upsert(next) { it.serialNumber == serialNumber }
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
    }
}

private fun RadioRidPacket.toTarget(previous: RadioDetectionTarget?): RadioDetectionTarget {
    val altitude = (heightMeters ?: altitudeGeoMeters ?: altitudeBaroMeters ?: 0.0).roundToInt()
    val hasDroneCoordinate = RadioCoordinateTransform.isUsable(droneLatitude, droneLongitude)
    val hasPilotCoordinate = RadioCoordinateTransform.isUsable(operatorLatitude, operatorLongitude)
    val parsedDroneCoordinate = RadioCoordinateTransform.wgs84ToGcj02(droneLatitude, droneLongitude)
    val parsedPilotCoordinate = RadioCoordinateTransform.wgs84ToGcj02(operatorLatitude, operatorLongitude)
    val droneCoordinate = if (hasDroneCoordinate) {
        parsedDroneCoordinate
    } else {
        previous?.takeIf { RadioCoordinateTransform.isUsable(it.latitude, it.longitude) }
            ?.let { RadioMapCoordinate(latitude = it.latitude, longitude = it.longitude) }
            ?: parsedDroneCoordinate
    }
    val pilotCoordinate = if (hasPilotCoordinate) {
        parsedPilotCoordinate
    } else {
        previous?.takeIf { RadioCoordinateTransform.isUsable(it.pilotLatitude, it.pilotLongitude) }
            ?.let { RadioMapCoordinate(latitude = it.pilotLatitude, longitude = it.pilotLongitude) }
            ?: parsedPilotCoordinate
    }
    val previousHasDroneCoordinate = previous?.let {
        RadioCoordinateTransform.isUsable(it.latitude, it.longitude)
    }
    val previousHasPilotCoordinate = previous?.let {
        RadioCoordinateTransform.isUsable(it.pilotLatitude, it.pilotLongitude)
    }
    if (
        previous == null ||
        previousHasDroneCoordinate != hasDroneCoordinate ||
        previousHasPilotCoordinate != hasPilotCoordinate
    ) {
        Log.d(
            "RadioDetectionRepo",
            "RID coordinate availability: target=$targetId hasDrone=$hasDroneCoordinate hasPilot=$hasPilotCoordinate " +
                "droneWgs=%.6f,%.6f droneGcj=%.6f,%.6f ".format(
                    droneLatitude,
                    droneLongitude,
                    parsedDroneCoordinate.latitude,
                    parsedDroneCoordinate.longitude
                ) +
                "pilotWgs=%.6f,%.6f pilotGcj=%.6f,%.6f".format(
                    operatorLatitude,
                    operatorLongitude,
                    parsedPilotCoordinate.latitude,
                    parsedPilotCoordinate.longitude
                )
        )
    }
    return RadioDetectionTarget(
        id = targetId,
        name = previous?.name ?: uaTypeLabel(uaType),
        type = "无人机",
        serialNumber = targetId,
        listStatus = previous?.listStatus ?: RadioListStatus.Unknown,
        latitude = droneCoordinate.latitude,
        longitude = droneCoordinate.longitude,
        altitudeMeters = altitude,
        speedMetersPerSecond = speedMetersPerSecond?.roundToInt() ?: 0,
        headingDegrees = headingDegrees?.floorMod360() ?: 0,
        frequencyLabel = frequencyLabel,
        signalLevel = signalLevelFromRssi(rssi),
        lastSeenText = "刚刚",
        pilotName = "飞手",
        pilotLatitude = pilotCoordinate.latitude,
        pilotLongitude = pilotCoordinate.longitude,
        pilotDistanceText = if (hasDroneCoordinate && hasPilotCoordinate) {
            distanceText(
                droneLatitude,
                droneLongitude,
                operatorLatitude,
                operatorLongitude
            )
        } else {
            previous?.pilotDistanceText ?: "-"
        },
        mapXPercent = previous?.mapXPercent ?: 0.5f,
        mapYPercent = previous?.mapYPercent ?: 0.5f
    )
}

private fun RadioRidPacket.operatorCoordinateOrNull(): RadioCoordinate? =
    if (operatorLatitude == 0.0 && operatorLongitude == 0.0) {
        null
    } else {
        val coordinate = RadioCoordinateTransform.wgs84ToGcj02(operatorLatitude, operatorLongitude)
        RadioCoordinate(
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            altitudeMeters = operatorAltitudeMeters ?: 0.0
        )
    }

private fun uaTypeLabel(uaType: Int?): String = when (uaType) {
    1 -> "直升机"
    2 -> "多旋翼无人机"
    3 -> "固定翼无人机"
    4 -> "垂直起降无人机"
    else -> "RID 目标"
}

private fun signalLevelFromRssi(rssi: Int): RadioSignalLevel = when {
    rssi >= -55 -> RadioSignalLevel.Strong
    rssi >= -75 -> RadioSignalLevel.Medium
    else -> RadioSignalLevel.Weak
}

private fun distanceText(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
    if ((lat1 == 0.0 && lon1 == 0.0) || (lat2 == 0.0 && lon2 == 0.0)) return "-"
    val meters = haversineMeters(lat1, lon1, lat2, lon2).roundToInt()
    return if (meters >= 1000) {
        "%.1fkm".format(meters / 1000.0)
    } else {
        "${meters}m"
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return radiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun Int.floorMod360(): Int = ((this % 360) + 360) % 360

private fun List<RadioDetectionTarget>.upsert(
    value: RadioDetectionTarget,
    sameItem: (RadioDetectionTarget) -> Boolean
): List<RadioDetectionTarget> {
    var replaced = false
    val next = map {
        if (sameItem(it)) {
            replaced = true
            value
        } else {
            it
        }
    }
    return if (replaced) next else next + value
}

private fun List<RadioDetectionDeviceState>.upsert(
    value: RadioDetectionDeviceState,
    sameItem: (RadioDetectionDeviceState) -> Boolean
): List<RadioDetectionDeviceState> {
    var replaced = false
    val next = map {
        if (sameItem(it)) {
            replaced = true
            value
        } else {
            it
        }
    }
    return if (replaced) next else next + value
}
