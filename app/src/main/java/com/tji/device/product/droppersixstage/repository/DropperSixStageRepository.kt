package com.tji.device.product.droppersixstage.repository

import android.util.Log
import com.tji.device.product.droppersixstage.model.DROPPER_STAGE_COUNT
import com.tji.device.product.droppersixstage.model.DropperSixStageAck
import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttTopics
import com.tji.network.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

interface DropperSixStageRepository {
    val devices: StateFlow<List<DropperSixStageState>>
    suspend fun updateState(state: DropperSixStageState)
    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?)
    suspend fun updateAck(serialNumber: String, ack: DropperSixStageAck)
    fun clearDevices()
}

class DropperSixStageRepo : DropperSixStageRepository {
    private val _devices = MutableStateFlow<List<DropperSixStageState>>(emptyList())
    override val devices: StateFlow<List<DropperSixStageState>> = _devices.asStateFlow()

    override suspend fun updateState(state: DropperSixStageState) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = state.serialNumber,
                create = { state },
                update = { old ->
                    state.copy(
                        name = state.name ?: old.name,
                        isOnline = state.isOnline || old.isOnline,
                        lastAck = state.lastAck ?: old.lastAck,
                        batteryPercent = state.batteryPercent ?: old.batteryPercent,
                        firmwareVersion = state.firmwareVersion ?: old.firmwareVersion,
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
                create = {
                    DropperSixStageState(
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

    override suspend fun updateAck(serialNumber: String, ack: DropperSixStageAck) {
        _devices.update { current ->
            current.updateOrCreate(
                serialNumber = serialNumber,
                create = {
                    DropperSixStageState(
                        serialNumber = serialNumber,
                        lastAck = ack,
                        stages = DropperStageState.defaults().applyAck(ack)
                    )
                },
                update = { state ->
                    state.copy(
                        lastAck = ack,
                        stages = state.stages.applyAck(ack)
                    )
                }
            )
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
    }

    private fun List<DropperSixStageState>.updateOrCreate(
        serialNumber: String,
        create: () -> DropperSixStageState,
        update: (DropperSixStageState) -> DropperSixStageState
    ): List<DropperSixStageState> {
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

    private fun List<DropperStageState>.applyAck(ack: DropperSixStageAck): List<DropperStageState> {
        val stage = ack.stage ?: return this
        if (!ack.ok) return this
        return map {
            if (it.index == stage) it.copy(isOpen = true) else it
        }
    }
}

interface DropperSixStageControlRepository {
    suspend fun sendCommand(serialNumber: String, command: DropperSixStageCommand)
}

class DropperSixStageControlRepo : DropperSixStageControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: DropperSixStageCommand) {
        val topic = DropperSixStageMqttTopics.controlTopic(serialNumber)
        val payload = command.toJson()
        val message = payload.toString()
        val requestAt = System.currentTimeMillis()

        MqttManager.getInstance().publish(
            topic = topic,
            message = message,
            qos = 0,
            queueWhenDisconnected = false,
            onSuccess = {
                Log.d(TAG, "六段抛投控制指令发送成功: topic=$topic message=$message")
            },
            onError = { throwable ->
                Log.e(TAG, "六段抛投控制指令发送失败: cost=${System.currentTimeMillis() - requestAt}ms", throwable)
            }
        )
    }

    private fun DropperSixStageCommand.toJson(): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("msgId", msgId)
            put("module", "firedrop")
            when (this@toJson) {
                is DropperSixStageCommand.Ping -> {
                    put("action", "query")
                }
                is DropperSixStageCommand.StageSwitch -> {
                    put("action", "set_hook")
                    put("hook", stage.coerceIn(1, DROPPER_STAGE_COUNT))
                    put("state", if (open) "open" else "close")
                    if (open && durationMs != null) {
                        put("duration", durationMs)
                    }
                }
                is DropperSixStageCommand.AllStages -> {
                    put("action", if (open) "open_all" else "close_all")
                    if (open && durationMs != null) {
                        put("duration", durationMs)
                    }
                }
            }
        }

    private companion object {
        const val TAG = "DropperSixControlRepo"
    }
}
