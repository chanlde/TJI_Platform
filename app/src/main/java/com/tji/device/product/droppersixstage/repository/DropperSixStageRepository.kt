package com.tji.device.product.droppersixstage.repository

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.model.DropperSixStageAck
import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.droppersixstage.model.DropperSixStageCommandCode
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.common.isOlderDeviceTimestamp
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
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
                    if (state.isOlderThan(old)) return@updateOrCreate old
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

    private fun DropperSixStageState.isOlderThan(current: DropperSixStageState): Boolean =
        isOlderDeviceTimestamp(timestamp, current.timestamp)

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
                    if (isOlderDeviceTimestamp(timestamp, state.timestamp)) {
                        return@updateOrCreate state
                    }
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
                        stages = DropperStageState.defaults()
                    )
                },
                update = { state ->
                    state.copy(
                        lastAck = ack
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

}

interface DropperSixStageControlRepository {
    suspend fun sendCommand(serialNumber: String, command: DropperSixStageCommand)
}

class DropperSixStageControlRepo : DropperSixStageControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: DropperSixStageCommand) {
        val topic = DropperSixStageMqttTopics.controlTopic(serialNumber)
        val payload = command.toDropperControlJson()
        val message = payload.toString()
        val requestAt = System.currentTimeMillis()

        ProductMqttRouter.managerFor(ProductType.DropperSixStage).publishAwait(
            topic = topic,
            message = message,
            qos = 1,
            queueWhenDisconnected = false
        ).getOrThrow()
        Log.d(
            TAG,
            "六段抛投控制指令发送成功: topic=$topic cost=${System.currentTimeMillis() - requestAt}ms message=$message"
        )
    }

    private companion object {
        const val TAG = "DropperSixControlRepo"
    }
}

/**
 * 六段抛投控制协议。
 *
 * `cmd/cmdName/stage/open` 是当前产品协议；`module/action/hook/state` 暂时保留，
 * 兼容已经按早期联调字段实现的固件。
 */
internal fun DropperSixStageCommand.toDropperControlJson(
    timestampMillis: Long = System.currentTimeMillis()
): JSONObject = JSONObject().apply {
    put("v", 1)
    put("msgId", msgId)
    put("ts", timestampMillis)
    put("module", "firedrop")
    when (this@toDropperControlJson) {
        is DropperSixStageCommand.Ping -> {
            put("cmd", DropperSixStageCommandCode.PING)
            put("cmdName", "PING")
            put("action", "query")
        }
        is DropperSixStageCommand.StageSwitch -> {
            put("cmd", DropperSixStageCommandCode.SET_STAGE_SWITCH)
            put("cmdName", "SET_STAGE_SWITCH")
            put("stage", stage)
            put("open", open)
            put("action", "set_hook")
            put("hook", stage)
            put("state", if (open) "open" else "close")
            durationMs?.let {
                put("durationMs", it)
                put("duration", it)
            }
        }
        is DropperSixStageCommand.AllStages -> {
            put("cmd", DropperSixStageCommandCode.SET_ALL_STAGES)
            put("cmdName", "SET_ALL_STAGES")
            put("open", open)
            put("action", if (open) "open_all" else "close_all")
            durationMs?.let {
                put("durationMs", it)
                put("duration", it)
            }
        }
    }
}
