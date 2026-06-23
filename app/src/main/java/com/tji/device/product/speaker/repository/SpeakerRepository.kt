package com.tji.device.product.speaker.repository

import android.util.Log
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import com.tji.device.product.speaker.core.SpeakerCommandJson
import com.tji.device.product.speaker.mqtt.SpeakerMqttTopics
import com.tji.network.MqttManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
                    limit = limit,
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
                val isDeleteGone = event.isDeleteAlreadyGone()
                val records = if (!event.recordId.isNullOrBlank()) {
                    when (event.type) {
                        "record_saved" -> if (event.ok && event.shouldAppearInRecordList()) {
                            it.records.upsertSavedRecord(event)
                        } else {
                            it.records
                        }
                        "record_deleted" -> if (event.ok || isDeleteGone) {
                            it.records.filterNot { record -> record.recordId == event.recordId }
                        } else {
                            it.records
                        }
                        else -> it.records
                    }
                } else {
                    it.records
                }
                val recordTotal = when {
                    event.type == "record_deleted" && (event.ok || isDeleteGone) && records.size < it.records.size ->
                        (it.recordTotal - 1).coerceAtLeast(records.size)
                    records.size > it.records.size -> maxOf(it.recordTotal, records.size)
                    else -> it.recordTotal.coerceAtLeast(records.size)
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
        if (incoming.code == SPEAKER_STORAGE_BUSY_CODE ||
            incoming.message.equals("record store active", ignoreCase = true)
        ) {
            return previous.copy(timestamp = incoming.timestamp ?: previous.timestamp)
        }
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
            createdMs = event.timestamp,
            path = event.path
        )
        return (listOf(savedRecord) + filterNot { it.recordId == recordId }).sortedForSpeakerRecordList()
    }

    private fun SpeakerRecordEvent.shouldAppearInRecordList(): Boolean =
        visible && path != "ram://temporary"

    private fun mergeRecordPage(
        existing: List<SpeakerRecord>,
        incoming: List<SpeakerRecord>,
        offset: Int,
        limit: Int,
        hasMore: Boolean
    ): List<SpeakerRecord> {
        if (offset == 0 && !hasMore) return incoming.sortedForSpeakerRecordList()
        val sortedExisting = existing.sortedForSpeakerRecordList()
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(incoming.size).coerceAtLeast(1)
        val prefix = sortedExisting.take(safeOffset)
        val suffixStart = (safeOffset + safeLimit).coerceAtMost(sortedExisting.size)
        val suffix = if (hasMore && suffixStart < sortedExisting.size) {
            sortedExisting.drop(suffixStart)
        } else {
            emptyList()
        }
        return (prefix + incoming + suffix)
            .distinctBy { it.recordId }
            .sortedForSpeakerRecordList()
    }

    private fun Collection<SpeakerRecord>.sortedForSpeakerRecordList(): List<SpeakerRecord> =
        sortedWith(
            compareByDescending<SpeakerRecord> { it.createdMs ?: extractTrailingTimestamp(it.recordId) }
                .thenByDescending { it.createdAt.orEmpty() }
        )

    private fun extractTrailingTimestamp(recordId: String): Long =
        recordId.substringAfterLast('_').toLongOrNull() ?: 0L

    private fun SpeakerRecordEvent.isDeleteAlreadyGone(): Boolean =
        type == "record_deleted" &&
            !recordId.isNullOrBlank() &&
            (code == 404 || message.contains("not found", ignoreCase = true))

    private companion object {
        const val SPEAKER_STORAGE_BUSY_CODE = 486
    }
}

interface SpeakerControlRepository {
    suspend fun sendCommand(serialNumber: String, command: SpeakerCommand)
}

class SpeakerControlRepo : SpeakerControlRepository {
    override suspend fun sendCommand(serialNumber: String, command: SpeakerCommand) {
        val topic = SpeakerMqttTopics.controlTopic(serialNumber)
        val message = SpeakerCommandJson.encode(command = command, deviceId = serialNumber).toString()
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

    private companion object {
        const val TAG = "SpeakerControlRepo"
    }
}
