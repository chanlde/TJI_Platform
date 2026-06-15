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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.json.JSONArray

interface SpeakerRepository {
    val devices: StateFlow<List<SpeakerDeviceState>>
    val recordEvents: SharedFlow<SpeakerRecordEventEnvelope>
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
    suspend fun upsertRecord(serialNumber: String, record: SpeakerRecord)
    fun clearDevices()
}

data class SpeakerRecordEventEnvelope(
    val serialNumber: String,
    val event: SpeakerRecordEvent
)

class SpeakerRepo : SpeakerRepository {
    private val _devices = MutableStateFlow<List<SpeakerDeviceState>>(emptyList())
    override val devices: StateFlow<List<SpeakerDeviceState>> = _devices.asStateFlow()
    private val _recordEvents = MutableSharedFlow<SpeakerRecordEventEnvelope>(extraBufferCapacity = 64)
    override val recordEvents: SharedFlow<SpeakerRecordEventEnvelope> = _recordEvents.asSharedFlow()

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
                    outputQuality = state.outputQuality ?: old.outputQuality,
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
                val mergedRecords = mergeRecordPage(
                    existing = it.records,
                    incoming = records,
                    offset = offset,
                    hasMore = hasMore
                )
                it.copy(
                    records = mergedRecords,
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
                val records = if (event.ok && !event.recordId.isNullOrBlank()) {
                    when (event.type) {
                        "record_saved" -> it.records.upsertSavedRecord(event)
                        "record_deleted" -> it.records.filterNot { record -> record.recordId == event.recordId }
                        else -> it.records
                    }
                } else {
                    it.records
                }
                val recordTotal = if (records.size > it.records.size) {
                    maxOf(it.recordTotal, records.size)
                } else {
                    it.recordTotal
                }
                it.copy(
                    records = records,
                    recordTotal = recordTotal,
                    lastRecordEvent = event,
                    timestamp = event.timestamp ?: it.timestamp
                )
            }
        }
        _recordEvents.tryEmit(SpeakerRecordEventEnvelope(serialNumber, event))
    }

    override suspend fun upsertRecord(serialNumber: String, record: SpeakerRecord) {
        _devices.update { current ->
            current.updateOrCreate(serialNumber, { SpeakerDeviceState(serialNumber = serialNumber, records = listOf(record), recordTotal = 1) }) {
                val records = listOf(record) + it.records.filterNot { existing -> existing.recordId == record.recordId }
                it.copy(
                    records = records,
                    recordTotal = maxOf(it.recordTotal, records.size)
                )
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
        return if (replaced) next else next + update(create())
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

    private fun List<SpeakerRecord>.upsertSavedRecord(event: SpeakerRecordEvent): List<SpeakerRecord> {
        val recordId = event.recordId ?: return this
        val savedRecord = SpeakerRecord(
            recordId = recordId,
            name = event.name ?: firstOrNull { it.recordId == recordId }?.name ?: recordId,
            fileSize = event.fileSize,
            durationMs = event.durationMs,
            codec = event.codec,
            sampleRate = event.sampleRate,
            channels = event.channels,
            packetMs = event.packetMs,
            crc32 = event.crc32,
            createdAt = event.createdAt,
            createdMs = event.timestamp
        )
        return (listOf(savedRecord) + filterNot { it.recordId == recordId }).sortedForSpeakerRecordList()
    }

    private fun mergeRecordPage(
        existing: List<SpeakerRecord>,
        incoming: List<SpeakerRecord>,
        offset: Int,
        hasMore: Boolean
    ): List<SpeakerRecord> {
        if (offset == 0 && !hasMore) return incoming.sortedForSpeakerRecordList()
        val existingById = existing.associateBy { it.recordId }.toMutableMap()
        incoming.forEach { existingById[it.recordId] = it }
        return existingById.values.sortedForSpeakerRecordList()
    }

    private fun Collection<SpeakerRecord>.sortedForSpeakerRecordList(): List<SpeakerRecord> =
        sortedWith(
            compareByDescending<SpeakerRecord> { it.createdMs ?: extractTrailingTimestamp(it.recordId) }
                .thenByDescending { it.createdAt.orEmpty() }
        )

    private fun extractTrailingTimestamp(recordId: String): Long =
        recordId.substringAfterLast('_').toLongOrNull() ?: 0L
}

interface SpeakerControlRepository {
    suspend fun sendCommand(serialNumber: String, command: SpeakerCommand)
}

class SpeakerControlRepo : SpeakerControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: SpeakerCommand) {
        val topic = SpeakerMqttTopics.controlTopic(serialNumber)
        val message = command.toJson(deviceId = serialNumber).toString()
        val messageBytes = message.toByteArray(Charsets.UTF_8).size
        val requestAt = System.currentTimeMillis()
        MqttManager.getInstance().publish(
            topic = topic,
            message = message,
            qos = 1,
            queueWhenDisconnected = false,
            onSuccess = {
                Log.d(
                    TAG,
                    "Speaker command sent: topic=$topic cmd=${command.commandName} " +
                        "msgId=${command.msgId} bytes=$messageBytes"
                )
            },
            onError = { throwable ->
                Log.e(TAG, "Speaker command failed: cost=${System.currentTimeMillis() - requestAt}ms", throwable)
            }
        )
    }

    private fun SpeakerCommand.toJson(deviceId: String): JSONObject =
        when (this) {
            is SpeakerCommand.RecordDownload -> toRecordDownloadJson(deviceId)
            else -> toStandardJson(deviceId)
        }

    private fun SpeakerCommand.RecordDownload.toRecordDownloadJson(deviceId: String): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("deviceId", deviceId)
            put("cmdId", msgId)
            put("cmdName", commandName)
            put("recordId", recordId)
            put("storeTaskId", storeTaskId)
            put("createdAt", createdAt)
            put("name", name)
            put("downloadUrl", downloadUrl)
            put("fileSize", fileSize)
            put("crc32", crc32)
            put("durationMs", durationMs)
            put("codec", codec)
            put("sampleRate", sampleRate)
            put("channels", channels)
            put("packetMs", packetMs)
            put("frameBytes", frameBytes)
            put("samplesPerFrame", samplesPerFrame)
            if (temporary) {
                put("temporary", true)
                put("visible", visible)
                put("autoPlay", autoPlay)
                playbackVolume?.let { put("playbackVolume", it.coerceIn(0, 100)) }
            }
            if (verifyOnly) {
                put("verifyOnly", true)
                verifyKind?.let { put("verifyKind", it) }
                expectedAudioCrc32?.let { put("expectedAudioCrc32", it) }
                put("expectedFirstSamples", JSONArray(expectedFirstSamples))
            }
        }

    private fun SpeakerCommand.toStandardJson(deviceId: String): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("deviceId", deviceId)
            put("cmdId", msgId)
            put("msgId", msgId)
            put("ts", System.currentTimeMillis())
            put("cmd", code)
            put("cmdName", commandName)
            when (this@toStandardJson) {
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
                is SpeakerCommand.SetAudioQuality -> {
                    put("quality", quality)
                    put("audioQuality", quality)
                    put("sampleRate", sampleRate)
                    put("packetMs", packetMs)
                    put("frameBytes", frameBytes)
                    put("samplesPerFrame", samplesPerFrame)
                    put("params", JSONObject().apply {
                        put("quality", quality)
                        put("audioQuality", quality)
                        put("sampleRate", sampleRate)
                        put("packetMs", packetMs)
                        put("frameBytes", frameBytes)
                        put("samplesPerFrame", samplesPerFrame)
                    })
                }
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
                    put("limit", limit.coerceIn(1, 4))
                    put("order", order)
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
                is SpeakerCommand.RecordDownload,
                is SpeakerCommand.Stop -> Unit
            }
        }

    private companion object {
        const val TAG = "SpeakerControlRepo"
    }
}
