package com.tji.device.product.glassbreaker.repository

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.glassbreaker.model.GLASS_BREAKER_CHANNEL_COUNT
import com.tji.device.product.glassbreaker.model.GlassBreakerAck
import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.glassbreaker.model.GlassBreakerCommandCode
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import com.tji.device.product.glassbreaker.mqtt.GlassBreakerMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

interface GlassBreakerRepository {
    val devices: StateFlow<List<GlassBreakerState>>
    suspend fun updateState(state: GlassBreakerState)
    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?)
    suspend fun updateAck(serialNumber: String, ack: GlassBreakerAck)
    suspend fun updateOtaAck(serialNumber: String, ack: GlassBreakerAck)
    fun clearDevices()
}

class GlassBreakerRepo : GlassBreakerRepository {
    private val _devices = MutableStateFlow<List<GlassBreakerState>>(emptyList())
    override val devices: StateFlow<List<GlassBreakerState>> = _devices.asStateFlow()

    override suspend fun updateState(state: GlassBreakerState) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = state.serialNumber,
                create = { state },
                update = { old ->
                    state.copy(
                        name = state.name ?: old.name,
                        isOnline = state.isOnline || old.isOnline,
                        lastAck = state.lastAck ?: old.lastAck,
                        lastOtaAck = state.lastOtaAck ?: old.lastOtaAck,
                        batteryPercent = state.batteryPercent ?: old.batteryPercent,
                        hardwareVersion = state.hardwareVersion ?: old.hardwareVersion,
                        firmwareVersion = state.firmwareVersion ?: old.firmwareVersion,
                        firmwareInnerVersion = state.firmwareInnerVersion ?: old.firmwareInnerVersion,
                        timestamp = state.timestamp ?: old.timestamp
                    )
                }
            )
        }
    }

    override suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = { GlassBreakerState(serialNumber = serialNumber, isOnline = isOnline, timestamp = timestamp) },
                update = { state ->
                    state.copy(
                        isOnline = isOnline,
                        timestamp = timestamp ?: state.timestamp
                    )
                }
            )
        }
    }

    override suspend fun updateAck(serialNumber: String, ack: GlassBreakerAck) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = { GlassBreakerState(serialNumber = serialNumber, lastAck = ack).withAckState(ack) },
                update = { state -> state.withAckState(ack).copy(lastAck = ack) }
            )
        }
    }

    override suspend fun updateOtaAck(serialNumber: String, ack: GlassBreakerAck) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = { GlassBreakerState(serialNumber = serialNumber, lastOtaAck = ack) },
                update = { state -> state.copy(lastOtaAck = ack, timestamp = ack.timestamp ?: state.timestamp) }
            )
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
    }

    private fun GlassBreakerState.withAckState(ack: GlassBreakerAck): GlassBreakerState =
        copy(
            lockState = ack.lockState ?: lockState,
            selectedChannel = ack.selectedChannel ?: selectedChannel,
            laserEnabled = ack.laserEnabled ?: laserEnabled,
            timestamp = ack.timestamp ?: timestamp
        )

    private fun List<GlassBreakerState>.updateOrCreate(
        serialNumber: String,
        create: () -> GlassBreakerState,
        update: (GlassBreakerState) -> GlassBreakerState
    ): List<GlassBreakerState> {
        var replaced = false
        val next = map { current ->
            if (current.serialNumber == serialNumber) {
                replaced = true
                update(current)
            } else {
                current
            }
        }
        return if (replaced) next else next + update(create())
    }
}

interface GlassBreakerControlRepository {
    suspend fun sendCommand(serialNumber: String, command: GlassBreakerCommand)
}

class GlassBreakerControlRepo : GlassBreakerControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: GlassBreakerCommand) {
        val topic = GlassBreakerMqttTopics.controlTopic(serialNumber)
        val payload = command.toGlassBreakerJson(deviceId = serialNumber)
        val message = payload.toString()
        val requestAt = System.currentTimeMillis()

        ProductMqttRouter.managerFor(ProductType.BreakWindowProjectile).publish(
            topic = topic,
            message = message,
            qos = 0,
            queueWhenDisconnected = false,
            onSuccess = {
                Log.d(TAG, "GlassBreaker command sent: topic=$topic msgId=${command.msgId} message=$message")
            },
            onError = { throwable ->
                Log.e(TAG, "GlassBreaker command failed: cost=${System.currentTimeMillis() - requestAt}ms", throwable)
            }
        )
    }

    private companion object {
        const val TAG = "GlassBreakerControl"
    }
}

internal fun GlassBreakerCommand.toGlassBreakerJson(deviceId: String): JSONObject =
    JSONObject().apply {
        put("v", 1)
        put("msgId", msgId)
        put("cmdId", msgId)
        put("deviceId", deviceId)
        put("ts", System.currentTimeMillis())
        put("cmd", commandCode())
        put("cmdName", commandName())
        put("params", JSONObject().apply {
            when (this@toGlassBreakerJson) {
                is GlassBreakerCommand.GetDeviceInfo,
                is GlassBreakerCommand.Unlock,
                is GlassBreakerCommand.Lock -> Unit
                is GlassBreakerCommand.SelectChannel -> put("channel", channel.coerceIn(1, GLASS_BREAKER_CHANNEL_COUNT))
                is GlassBreakerCommand.FireChannel -> put("channel", channel.coerceIn(1, GLASS_BREAKER_CHANNEL_COUNT))
                is GlassBreakerCommand.LaserSwitch -> put("on", on)
            }
        })
    }

private fun GlassBreakerCommand.commandCode(): Int = when (this) {
    is GlassBreakerCommand.GetDeviceInfo -> GlassBreakerCommandCode.GET_DEVICE_INFO
    is GlassBreakerCommand.Unlock -> GlassBreakerCommandCode.UNLOCK
    is GlassBreakerCommand.Lock -> GlassBreakerCommandCode.LOCK
    is GlassBreakerCommand.FireChannel -> GlassBreakerCommandCode.FIRE_CHANNEL
    is GlassBreakerCommand.LaserSwitch -> GlassBreakerCommandCode.LASER_SWITCH
    is GlassBreakerCommand.SelectChannel -> GlassBreakerCommandCode.SELECT_CHANNEL
}

private fun GlassBreakerCommand.commandName(): String = when (this) {
    is GlassBreakerCommand.GetDeviceInfo -> "GET_DEVICE_INFO"
    is GlassBreakerCommand.Unlock -> "UNLOCK"
    is GlassBreakerCommand.Lock -> "LOCK"
    is GlassBreakerCommand.FireChannel -> "FIRE_CHANNEL"
    is GlassBreakerCommand.LaserSwitch -> "LASER_SWITCH"
    is GlassBreakerCommand.SelectChannel -> "SELECT_CHANNEL"
}
