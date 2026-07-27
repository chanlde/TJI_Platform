package com.tji.device.product.radiodetection.repository

import android.util.Log
import com.tji.device.BuildConfig
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

    suspend fun pruneExpiredTargets()

    fun clearDevices()
}

class RadioDetectionRepo(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val monotonicMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
    private val targetTtlMillis: Long = DEFAULT_TARGET_TTL_MILLIS,
    private val maxTargetsPerDevice: Int = DEFAULT_MAX_TARGETS
) : RadioDetectionRepository {
    private val _devices = MutableStateFlow<List<RadioDetectionDeviceState>>(emptyList())
    override val devices: StateFlow<List<RadioDetectionDeviceState>> = _devices.asStateFlow()

    override suspend fun upsertRidPacket(serialNumber: String, packet: RadioRidPacket) {
        val now = nowMillis()
        val nowElapsed = monotonicMillis()
        _devices.update { current ->
            val existingDevice = current.firstOrNull { it.serialNumber == serialNumber }
            val freshTargets = existingDevice
                ?.targets
                .orEmpty()
                .filter { it.isFreshAt(nowElapsed) }
            // TTL 过期意味着旧目标已离开当前会话。设备重启后 RID 时间戳可能从零开始，
            // 不能再用过期目标的时间戳拒绝新会话首包。
            val previousTarget = freshTargets.firstOrNull { it.id == packet.targetId }
            val isOutOfOrder = packet.timestampMillis != null &&
                previousTarget?.sourceTimestampMillis != null &&
                packet.timestampMillis < previousTarget.sourceTimestampMillis
            val targets = if (isOutOfOrder) {
                freshTargets
            } else {
                val target = packet.toTarget(
                    previous = previousTarget,
                    nowMillis = now,
                    nowElapsedMillis = nowElapsed
                )
                // 最近目标放在首位，单次更新保持 O(n)，避免高频 RID 下每包都执行 O(n log n) 排序。
                (listOf(target) + freshTargets.filterNot { it.id == target.id })
                    .take(maxTargetsPerDevice)
            }
            val nextDevice = RadioDetectionDeviceState(
                serialNumber = serialNumber,
                displayName = existingDevice?.displayName ?: "频谱检测仪",
                isOnline = true,
                payloadStatus = if (isOutOfOrder) "已忽略过期 RID" else "已收到 RID",
                currentCoordinate = if (isOutOfOrder) {
                    existingDevice?.currentCoordinate
                } else {
                    packet.operatorCoordinateOrNull() ?: existingDevice?.currentCoordinate
                },
                targets = targets,
                lastUpdateMillis = now,
                filteredMessageCount = (existingDevice?.filteredMessageCount ?: 0) +
                    if (isOutOfOrder) 1 else 0,
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
        val now = nowMillis()
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

    override suspend fun pruneExpiredTargets() {
        val nowElapsed = monotonicMillis()
        _devices.update { current ->
            var changed = false
            val next = current.map { device ->
                val freshTargets = device.targets.filter {
                    it.isFreshAt(nowElapsed)
                }
                if (freshTargets.size != device.targets.size) {
                    changed = true
                    device.copy(targets = freshTargets)
                } else {
                    device
                }
            }
            if (changed) next else current
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
    }

    private fun RadioDetectionTarget.isFreshAt(nowElapsedMillis: Long): Boolean {
        val ageMillis = nowElapsedMillis - lastSeenElapsedMillis
        return ageMillis in 0..targetTtlMillis
    }

    private companion object {
        const val DEFAULT_TARGET_TTL_MILLIS = 30_000L
        const val DEFAULT_MAX_TARGETS = 200
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

private fun RadioRidPacket.toTarget(
    previous: RadioDetectionTarget?,
    nowMillis: Long,
    nowElapsedMillis: Long
): RadioDetectionTarget {
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
        BuildConfig.DEBUG && (
            previous == null ||
                previousHasDroneCoordinate != hasDroneCoordinate ||
                previousHasPilotCoordinate != hasPilotCoordinate
            )
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
        lastSeenAtMillis = nowMillis,
        lastSeenElapsedMillis = nowElapsedMillis,
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
        mapYPercent = previous?.mapYPercent ?: 0.5f,
        sourceTimestampMillis = timestampMillis ?: previous?.sourceTimestampMillis
    )
}

private fun RadioRidPacket.operatorCoordinateOrNull(): RadioCoordinate? =
    if (!RadioCoordinateTransform.isUsable(operatorLatitude, operatorLongitude)) {
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
