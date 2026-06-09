package com.tji.device.product.speaker.repository

import android.util.Log
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
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
    suspend fun updateRecords(
        serialNumber: String,
        records: List<SpeakerRecord>,
        offset: Int,
        limit: Int,
        total: Int,
        hasMore: Boolean,
        timestamp: Long?
    )
    suspend fun updateStorageStatus(serialNumber: String, status: SpeakerStorageStatus)
    suspend fun updateRecordEvent(serialNumber: String, event: SpeakerRecordEvent)
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
                    records = old.records,
                    recordOffset = old.recordOffset,
                    recordLimit = old.recordLimit,
                    recordTotal = old.recordTotal,
                    recordHasMore = old.recordHasMore,
                    storageStatus = old.storageStatus,
                    lastRecordEvent = old.lastRecordEvent,
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

    override suspend fun updateRecords(
        serialNumber: String,
        records: List<SpeakerRecord>,
        offset: Int,
        limit: Int,
        total: Int,
        hasMore: Boolean,
        timestamp: Long?
    ) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber = serialNumber) }) {
                it.copy(
                    records = records,
                    recordOffset = offset,
                    recordLimit = limit,
                    recordTotal = total,
                    recordHasMore = hasMore,
                    timestamp = timestamp ?: it.timestamp
                )
            }
        }
    }

    override suspend fun updateStorageStatus(serialNumber: String, status: SpeakerStorageStatus) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber = serialNumber, storageStatus = status) }) {
                val mergedStatus = mergeStorageStatus(previous = it.storageStatus, incoming = status)
                it.copy(storageStatus = mergedStatus, timestamp = status.timestamp ?: it.timestamp)
            }
        }
    }

    override suspend fun updateRecordEvent(serialNumber: String, event: SpeakerRecordEvent) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber = serialNumber, lastRecordEvent = event) }) {
                it.copy(lastRecordEvent = event, timestamp = event.timestamp ?: it.timestamp)
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

    private fun mergeStorageStatus(
        previous: SpeakerStorageStatus?,
        incoming: SpeakerStorageStatus
    ): SpeakerStorageStatus {
        if (incoming.ok || previous == null) return incoming
        val incomingHasCapacity = incoming.totalBytes > 0L || incoming.freeBytes > 0L
        if (incomingHasCapacity) return incoming
        val previousHasCapacity = previous.totalBytes > 0L || previous.freeBytes > 0L
        if (!previousHasCapacity) return incoming

        return incoming.copy(
            backend = incoming.backend ?: previous.backend,
            totalBytes = previous.totalBytes,
            freeBytes = previous.freeBytes,
            recordCount = previous.recordCount,
            maxRecords = previous.maxRecords
        )
    }
}

interface SpeakerControlRepository {
    suspend fun sendCommand(serialNumber: String, command: SpeakerCommand)
}

class SpeakerControlRepo : SpeakerControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: SpeakerCommand) {
        val topic = SpeakerMqttTopics.controlTopic(serialNumber)
        val message = command.toJson(deviceId = serialNumber).toString()
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

    private fun SpeakerCommand.toJson(deviceId: String): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("deviceId", deviceId)
            put("cmdId", msgId)
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
                is SpeakerCommand.StartRecordStore -> {
                    put("recordId", recordId)
                    put("storeTaskId", storeTaskId)
                    put("createdAt", createdAt)
                    put("name", name)
                    put("codec", "ima_adpcm")
                    put("sampleRate", 8_000)
                    put("channels", 1)
                    put("packetMs", 40)
                    expectedDurationMs?.let { put("expectedDurationMs", it) }
                    expectedFileSize?.let { put("expectedFileSize", it) }
                    put("params", JSONObject().apply {
                        put("recordId", recordId)
                        put("storeTaskId", storeTaskId)
                        put("createdAt", createdAt)
                        put("name", name)
                        put("codec", "ima_adpcm")
                        put("sampleRate", 8_000)
                        put("channels", 1)
                        put("packetMs", 40)
                        expectedDurationMs?.let { put("expectedDurationMs", it) }
                        expectedFileSize?.let { put("expectedFileSize", it) }
                    })
                }
                is SpeakerCommand.RecordDownload -> {
                    put("recordId", recordId)
                    put("name", name)
                    put("downloadUrl", downloadUrl)
                    put("fileSize", fileSize)
                    put("crc32", crc32)
                    put("durationMs", durationMs)
                    put("codec", "ima_adpcm")
                    put("sampleRate", 8_000)
                    put("channels", 1)
                    put("packetMs", 40)
                    put("params", JSONObject().apply {
                        put("recordId", recordId)
                        put("name", name)
                        put("downloadUrl", downloadUrl)
                        put("fileSize", fileSize)
                        put("crc32", crc32)
                        put("durationMs", durationMs)
                        put("codec", "ima_adpcm")
                        put("sampleRate", 8_000)
                        put("channels", 1)
                        put("packetMs", 40)
                    })
                }
                is SpeakerCommand.PlayRecord -> {
                    put("recordId", recordId)
                    put("volume", volume.coerceIn(0, 100))
                    put("params", JSONObject().apply {
                        put("recordId", recordId)
                        put("volume", volume.coerceIn(0, 100))
                    })
                }
                is SpeakerCommand.ListRecords -> {
                    put("offset", offset.coerceAtLeast(0))
                    put("limit", limit.coerceIn(1, 8))
                    put("params", JSONObject().apply {
                        put("offset", offset.coerceAtLeast(0))
                        put("limit", limit.coerceIn(1, 8))
                    })
                }
                is SpeakerCommand.DeleteRecord -> {
                    put("recordId", recordId)
                    put("params", JSONObject().apply {
                        put("recordId", recordId)
                    })
                }
                is SpeakerCommand.UpdateRecord -> {
                    put("recordId", recordId)
                    put("name", name)
                    put("params", JSONObject().apply {
                        put("recordId", recordId)
                        put("name", name)
                    })
                }
                is SpeakerCommand.GetStorageStatus -> Unit
                is SpeakerCommand.GetStatus,
                is SpeakerCommand.Stop -> Unit
            }
        }

    private companion object {
        const val TAG = "SpeakerControlRepo"
    }
}
