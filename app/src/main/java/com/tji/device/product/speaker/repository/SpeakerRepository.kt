package com.tji.device.product.speaker.repository

import android.util.Log
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.mqtt.SpeakerMqttTopics
import com.tji.network.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

interface SpeakerRepository {
    val devices: StateFlow<List<SpeakerDeviceState>>
    suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?)
    suspend fun updateState(state: SpeakerDeviceState)
    suspend fun updateAck(serialNumber: String, ack: SpeakerAck)
    fun clearDevices()
}

class SpeakerRepo : SpeakerRepository {
    private val _devices = MutableStateFlow<List<SpeakerDeviceState>>(emptyList())
    override val devices: StateFlow<List<SpeakerDeviceState>> = _devices.asStateFlow()

    override suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean, timestamp: Long?) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber, isOnline = isOnline, timestamp = timestamp) }) {
                it.copy(isOnline = isOnline, timestamp = timestamp ?: it.timestamp)
            }
        }
    }

    override suspend fun updateState(state: SpeakerDeviceState) {
        _devices.update { current ->
            current.updateOrCreate(state.serialNumber, { state }) { old ->
                state.copy(
                    name = state.name ?: old.name,
                    isOnline = state.isOnline || old.isOnline,
                    lastAck = state.lastAck ?: old.lastAck,
                    timestamp = state.timestamp ?: old.timestamp
                )
            }
        }
    }

    override suspend fun updateAck(serialNumber: String, ack: SpeakerAck) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber = serialNumber, lastAck = ack) }) {
                it.copy(lastAck = ack)
            }
        }
    }

    override fun clearDevices() {
        _devices.value = emptyList()
    }

    private fun List<SpeakerDeviceState>.updateOrCreate(
        serialNumber: String,
        create: () -> SpeakerDeviceState,
        update: (SpeakerDeviceState) -> SpeakerDeviceState
    ): List<SpeakerDeviceState> {
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

interface SpeakerControlRepository {
    suspend fun sendCommand(serialNumber: String, command: SpeakerCommand)
}

class SpeakerControlRepo : SpeakerControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: SpeakerCommand) {
        val topic = SpeakerMqttTopics.controlTopic(serialNumber)
        val message = command.toJson().toString()
        val requestAt = System.currentTimeMillis()
        MqttManager.getInstance().publish(
            topic = topic,
            message = message,
            qos = 1,
            queueWhenDisconnected = false,
            onSuccess = {
                Log.d(TAG, "Speaker command sent: topic=$topic cmd=${command.commandName} msgId=${command.msgId}")
            },
            onError = { throwable ->
                Log.e(TAG, "Speaker command failed: cost=${System.currentTimeMillis() - requestAt}ms", throwable)
            }
        )
    }

    private fun SpeakerCommand.toJson(): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("msgId", msgId)
            put("ts", System.currentTimeMillis())
            put("cmd", code)
            put("cmdName", commandName)
            when (this@toJson) {
                is SpeakerCommand.SpeakText -> put("params", JSONObject().apply {
                    put("text", text)
                    put("volume", volume.coerceIn(0, 100))
                })
                is SpeakerCommand.PrepareText -> put("params", JSONObject().apply {
                    put("text", text)
                })
                is SpeakerCommand.PlayFile -> put("params", JSONObject().apply {
                    put("file", file)
                    put("volume", volume.coerceIn(0, 100))
                })
                is SpeakerCommand.SetVolume -> put("params", JSONObject().apply {
                    put("volume", volume.coerceIn(0, 100))
                })
                is SpeakerCommand.SetServoAngle -> put("params", JSONObject().apply {
                    put("angle", angle.coerceIn(-90, 90))
                })
                is SpeakerCommand.GetStatus,
                is SpeakerCommand.Stop -> Unit
            }
        }

    private companion object {
        const val TAG = "SpeakerControlRepo"
    }
}
