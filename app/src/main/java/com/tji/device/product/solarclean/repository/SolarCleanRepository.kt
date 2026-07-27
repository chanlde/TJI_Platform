package com.tji.device.product.solarclean.repository

import com.tji.device.product.solarclean.model.SolarCleanAck
import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanEvent
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.product.common.isOlderDeviceTimestamp
import com.tji.device.product.common.mergeDeviceTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SolarCleanRepository {
    val devices: StateFlow<List<SolarCleanDeviceState>>
    val controlSettings: StateFlow<Map<String, SolarCleanControlSettings>>

    /**
     * 合并一帧设备状态。
     *
     * @return `true` 表示该帧未发生同一时钟域内的时间回退，调用方可据此刷新在线超时。
     */
    suspend fun updateDeviceState(state: SolarCleanDeviceState): Boolean

    /**
     * 应用生命周期状态。
     *
     * @return `false` 表示消息已过期，调用方不应取消或刷新当前在线超时。
     */
    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?): Boolean

    suspend fun updateDeviceInfo(serialNumber: String, info: SolarCleanDeviceInfo)

    suspend fun updateOtaStatus(serialNumber: String, status: SolarCleanOtaStatus)

    suspend fun updateAck(serialNumber: String, ack: SolarCleanAck)

    suspend fun updateEvent(serialNumber: String, event: SolarCleanEvent)

    fun updateControlSettings(
        serialNumber: String,
        transform: (SolarCleanControlSettings) -> SolarCleanControlSettings
    )

    fun clearDevices()
}

class SolarCleanRepo : SolarCleanRepository {
    private val _devices = MutableStateFlow<List<SolarCleanDeviceState>>(emptyList())
    override val devices: StateFlow<List<SolarCleanDeviceState>> = _devices.asStateFlow()
    private val _controlSettings = MutableStateFlow<Map<String, SolarCleanControlSettings>>(emptyMap())
    override val controlSettings: StateFlow<Map<String, SolarCleanControlSettings>> = _controlSettings.asStateFlow()

    override suspend fun updateDeviceState(state: SolarCleanDeviceState): Boolean =
        _devices.updateAndReport { current ->
            val old = current.firstOrNull { it.serialNumber == state.serialNumber }
            if (old != null && state.isOlderThan(old)) {
                current to false
            } else {
                val merged = if (old == null) {
                    state
                } else {
                    state.copy(
                        isOnline = state.isOnline || old.isOnline,
                        latitude = state.latitude ?: old.latitude,
                        longitude = state.longitude ?: old.longitude,
                        altitudeMeters = state.altitudeMeters ?: old.altitudeMeters,
                        speedMetersPerSecond = state.speedMetersPerSecond ?: old.speedMetersPerSecond,
                        yawDegrees = state.yawDegrees ?: old.yawDegrees,
                        pitchDegrees = state.pitchDegrees ?: old.pitchDegrees,
                        rollDegrees = state.rollDegrees ?: old.rollDegrees,
                        satelliteCount = state.satelliteCount ?: old.satelliteCount,
                        batteryPercent = state.batteryPercent ?: old.batteryPercent,
                        waypointIndex = state.waypointIndex ?: old.waypointIndex,
                        waterLevel = state.waterLevel ?: old.waterLevel,
                        mqttConnected = state.mqttConnected ?: old.mqttConnected,
                        mqttLastError = state.mqttLastError ?: old.mqttLastError,
                        deviceInfo = state.deviceInfo ?: old.deviceInfo,
                        otaStatus = state.otaStatus ?: old.otaStatus,
                        download = state.download ?: old.download,
                        lastAck = state.lastAck ?: old.lastAck,
                        lastEvent = state.lastEvent ?: old.lastEvent,
                        routeSlots = state.routeSlots.ifEmpty { old.routeSlots },
                        timestamp = state.timestamp ?: old.timestamp
                    )
                }
                current.upsert(merged, sameDevice = { it.serialNumber == state.serialNumber }) to true
            }
        }

    private fun SolarCleanDeviceState.isOlderThan(current: SolarCleanDeviceState): Boolean =
        isOlderDeviceTimestamp(timestamp, current.timestamp)

    override suspend fun updateOnlineStatus(
        serialNumber: String,
        isOnline: Boolean,
        timestamp: Long?
    ): Boolean = _devices.updateAndReport { current ->
        val state = current.firstOrNull { it.serialNumber == serialNumber }
        if (state != null && isOlderDeviceTimestamp(timestamp, state.timestamp)) {
            current to false
        } else {
            val next = state?.copy(
                isOnline = isOnline,
                timestamp = timestamp ?: state.timestamp
            ) ?: SolarCleanDeviceState(
                serialNumber = serialNumber,
                isOnline = isOnline,
                timestamp = timestamp
            )
            current.upsert(next, sameDevice = { it.serialNumber == serialNumber }) to true
        }
    }

    override suspend fun updateDeviceInfo(serialNumber: String, info: SolarCleanDeviceInfo) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    SolarCleanDeviceState(
                        serialNumber = serialNumber,
                        deviceInfo = info,
                        timestamp = info.timestamp
                    )
                },
                update = { state ->
                    if (isOlderDeviceTimestamp(info.timestamp, state.deviceInfo?.timestamp)) {
                        return@updateOrCreate state
                    }
                    state.copy(
                        deviceInfo = info,
                        timestamp = mergeDeviceTimestamp(state.timestamp, info.timestamp)
                    )
                }
            )
        }
    }

    override suspend fun updateOtaStatus(serialNumber: String, status: SolarCleanOtaStatus) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    SolarCleanDeviceState(
                        serialNumber = serialNumber,
                        otaStatus = status,
                        timestamp = status.timestamp
                    )
                },
                update = { state ->
                    if (isOlderDeviceTimestamp(status.timestamp, state.otaStatus?.timestamp)) {
                        return@updateOrCreate state
                    }
                    state.copy(
                        otaStatus = status,
                        timestamp = mergeDeviceTimestamp(state.timestamp, status.timestamp)
                    )
                }
            )
        }
    }

    override suspend fun updateAck(serialNumber: String, ack: SolarCleanAck) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    SolarCleanDeviceState(
                        serialNumber = serialNumber,
                        lastAck = ack,
                        routeSlots = ack.routeSlots
                    )
                },
                update = { state ->
                    val routeSlots = when {
                        ack.isSuccessfulRouteList() -> ack.routeSlots
                        ack.routeSlots.isNotEmpty() -> ack.routeSlots
                        else -> state.routeSlots
                    }
                    state.copy(
                        lastAck = ack,
                        routeSlots = routeSlots
                    )
                }
            )
        }
    }

    override suspend fun updateEvent(serialNumber: String, event: SolarCleanEvent) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    SolarCleanDeviceState(
                        serialNumber = serialNumber,
                        lastEvent = event,
                        download = event.toDownloadState()
                    )
                },
                update = { state ->
                    state.copy(
                        lastEvent = event,
                        download = event.toDownloadState() ?: state.download
                    )
                }
            )
        }
    }

    override fun updateControlSettings(
        serialNumber: String,
        transform: (SolarCleanControlSettings) -> SolarCleanControlSettings
    ) {
        _controlSettings.update { current ->
            val currentSettings = current[serialNumber] ?: SolarCleanControlSettings()
            current + (serialNumber to transform(currentSettings).normalized())
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
        _controlSettings.value = emptyMap()
    }

    private fun SolarCleanControlSettings.normalized(): SolarCleanControlSettings =
        copy(
            pumpPressurePercent = pumpPressurePercent.coerceIn(0.0, 100.0),
            sprayAngleDegrees = sprayAngleDegrees.coerceIn(0.0, 40.0),
            swingSpeedPercent = swingSpeedPercent.coerceIn(0.0, 100.0)
        )

    private fun List<SolarCleanDeviceState>.updateOrCreate(
        serialNumber: String,
        create: () -> SolarCleanDeviceState,
        update: (SolarCleanDeviceState) -> SolarCleanDeviceState
    ): List<SolarCleanDeviceState> {
        var replaced = false
        val next = map { current ->
            if (current.serialNumber == serialNumber) {
                replaced = true
                update(current)
            } else {
                current
            }
        }
        return if (replaced) next else next + create()
    }

    private fun List<SolarCleanDeviceState>.upsert(
        value: SolarCleanDeviceState,
        sameDevice: (SolarCleanDeviceState) -> Boolean,
        merge: (SolarCleanDeviceState, SolarCleanDeviceState) -> SolarCleanDeviceState = { _, new -> new }
    ): List<SolarCleanDeviceState> {
        var replaced = false
        val next = map { current ->
            if (sameDevice(current)) {
                replaced = true
                merge(current, value)
            } else {
                current
            }
        }
        return if (replaced) next else next + value
    }

    private inline fun MutableStateFlow<List<SolarCleanDeviceState>>.updateAndReport(
        transform: (List<SolarCleanDeviceState>) -> Pair<List<SolarCleanDeviceState>, Boolean>
    ): Boolean {
        while (true) {
            val previous = value
            val (next, applied) = transform(previous)
            if (compareAndSet(previous, next)) return applied
        }
    }

    private fun SolarCleanEvent.toDownloadState() = when (this) {
        is SolarCleanEvent.DownloadProgress -> {
            com.tji.device.product.solarclean.model.SolarCleanDownloadState(
                slot = slot,
                active = true,
                percent = percent,
                bytes = bytes,
                total = total
            )
        }
        is SolarCleanEvent.DownloadDone -> {
            com.tji.device.product.solarclean.model.SolarCleanDownloadState(
                slot = slot,
                active = false,
                percent = 100.0,
                bytes = size,
                total = size
            )
        }
        is SolarCleanEvent.DownloadError -> {
            com.tji.device.product.solarclean.model.SolarCleanDownloadState(
                slot = slot,
                active = false,
                percent = 0.0,
                bytes = 0,
                total = 0
            )
        }
        is SolarCleanEvent.Online,
        is SolarCleanEvent.Offline,
        is SolarCleanEvent.RouteExecuteFinished,
        is SolarCleanEvent.RouteExecuteStarted -> null
    }

    private fun SolarCleanAck.isSuccessfulRouteList(): Boolean =
        ok && ofType
            .filter(Char::isLetterOrDigit)
            .equals("routeList", ignoreCase = true)
}
