package com.tji.device.product.solarclean.repository

import com.tji.device.product.solarclean.model.SolarCleanAck
import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanEvent
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SolarCleanRepository {
    val devices: StateFlow<List<SolarCleanDeviceState>>
    val controlSettings: StateFlow<Map<String, SolarCleanControlSettings>>

    suspend fun updateDeviceState(state: SolarCleanDeviceState)

    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?)

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

    override suspend fun updateDeviceState(state: SolarCleanDeviceState) {
        _devices.update { current ->
            current.upsert(state, sameDevice = { it.serialNumber == state.serialNumber }) { old, new ->
                new.copy(
                    deviceInfo = new.deviceInfo ?: old.deviceInfo,
                    otaStatus = new.otaStatus ?: old.otaStatus,
                    download = new.download ?: old.download,
                    lastAck = new.lastAck ?: old.lastAck,
                    lastEvent = new.lastEvent ?: old.lastEvent,
                    routeSlots = new.routeSlots.ifEmpty { old.routeSlots }
                )
            }
        }
    }

    override suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    SolarCleanDeviceState(
                        serialNumber = serialNumber,
                        isOnline = isOnline,
                        timestamp = timestamp
                    )
                },
                update = { state ->
                    state.copy(
                        isOnline = isOnline,
                        timestamp = timestamp ?: state.timestamp
                    )
                }
            )
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
                    state.copy(
                        deviceInfo = info,
                        timestamp = info.timestamp ?: state.timestamp
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
                    state.copy(
                        otaStatus = status,
                        timestamp = status.timestamp ?: state.timestamp
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
                    state.copy(
                        lastAck = ack,
                        routeSlots = ack.routeSlots.ifEmpty { state.routeSlots }
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
}
