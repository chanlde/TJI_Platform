package com.tji.device.product.speaker.model

import com.tji.device.product.runtime.ProductRuntimePayload

data class SpeakerDeviceState(
    val serialNumber: String,
    val name: String? = null,
    val isOnline: Boolean = false,
    val playing: Boolean = false,
    val currentFile: String? = null,
    val volume: Int = DEFAULT_SPEAKER_VOLUME,
    val servoAngle: Int? = null,
    val lastError: String? = null,
    val network: String? = null,
    val lastAck: SpeakerAck? = null,
    val records: List<SpeakerRecord> = emptyList(),
    val recordOffset: Int = 0,
    val recordLimit: Int = 8,
    val recordTotal: Int = 0,
    val recordHasMore: Boolean = false,
    val storageStatus: SpeakerStorageStatus? = null,
    val lastRecordEvent: SpeakerRecordEvent? = null,
    val outputQuality: String? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload

data class SpeakerAck(
    val msgId: String,
    val ofType: String,
    val ofCmd: Int,
    val ok: Boolean,
    val code: Int,
    val message: String,
    val timestamp: Long?
)

sealed class SpeakerCommand(
    val msgId: String,
    val code: Int,
    val commandName: String
) {
    class SpeakText(
        msgId: String,
        val text: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 101, "SPEAK_TEXT")

    class PrepareText(
        msgId: String,
        val text: String
    ) : SpeakerCommand(msgId, 102, "PREPARE_TEXT")

    class PlayFile(
        msgId: String,
        val file: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 103, "PLAY_FILE")

    class Stop(msgId: String) : SpeakerCommand(msgId, 104, "STOP")

    class SetVolume(
        msgId: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 105, "SET_VOLUME")

    class SetAudioQuality(
        msgId: String,
        val quality: String,
        val sampleRate: Int,
        val packetMs: Int,
        val frameBytes: Int,
        val samplesPerFrame: Int
    ) : SpeakerCommand(msgId, 123, "SET_AUDIO_QUALITY")

    class GetStatus(msgId: String) : SpeakerCommand(msgId, 106, "GET_STATUS")

    class SetServoAngle(
        msgId: String,
        val angle: Int
    ) : SpeakerCommand(msgId, 107, "SET_SERVO_ANGLE")

    class StartRecordStore(
        msgId: String,
        val recordId: String,
        val storeTaskId: String,
        val createdAt: String,
        val name: String,
        val expectedDurationMs: Int? = null,
        val expectedFileSize: Int? = null
    ) : SpeakerCommand(msgId, 114, "START_RECORD_STORE")

    class RecordDownload(
        msgId: String,
        val recordId: String,
        val storeTaskId: String,
        val createdAt: String,
        val name: String,
        val downloadUrl: String,
        val fileSize: Long,
        val crc32: String,
        val durationMs: Int,
        val codec: String = "pcm16",
        val sampleRate: Int = 8_000,
        val channels: Int = 1,
        val packetMs: Int = 40,
        val frameBytes: Int = 640,
        val samplesPerFrame: Int = 320,
        val verifyOnly: Boolean = false,
        val verifyKind: String? = null,
        val expectedAudioCrc32: String? = null,
        val expectedFirstSamples: List<Int> = emptyList(),
        val temporary: Boolean = false,
        val visible: Boolean = true,
        val autoPlay: Boolean = false,
        val playbackVolume: Int? = null
    ) : SpeakerCommand(msgId, 114, "RECORD_DOWNLOAD")

    class PlayRecord(
        msgId: String,
        val recordId: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 118, "PLAY_RECORD")

    class ListRecords(
        msgId: String,
        val offset: Int = 0,
        val limit: Int = 4,
        val order: String = "desc"
    ) : SpeakerCommand(msgId, 119, "LIST_RECORDS")

    class DeleteRecord(
        msgId: String,
        val recordId: String
    ) : SpeakerCommand(msgId, 120, "DELETE_RECORD")

    class UpdateRecord(
        msgId: String,
        val recordId: String,
        val name: String
    ) : SpeakerCommand(msgId, 121, "UPDATE_RECORD")

    class GetStorageStatus(msgId: String) : SpeakerCommand(msgId, 122, "GET_STORAGE_STATUS")
}

const val DEFAULT_SPEAKER_VOLUME = 35

data class SpeakerRecord(
    val recordId: String,
    val name: String,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val codec: String = "ima_adpcm",
    val sampleRate: Int = 8_000,
    val channels: Int = 1,
    val packetMs: Int = 40,
    val crc32: String? = null,
    val createdAt: String? = null,
    val createdMs: Long? = null,
    val path: String? = null
)

data class SpeakerStorageStatus(
    val ok: Boolean,
    val backend: String? = null,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val recordCount: Int = 0,
    val maxRecords: Int = 0,
    val code: Int = 0,
    val message: String = "",
    val timestamp: Long? = null
)

data class SpeakerRecordEvent(
    val type: String,
    val recordId: String? = null,
    val ok: Boolean = true,
    val code: Int = 0,
    val message: String = "",
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val headerSize: Int = 0,
    val frameBytes: Int = 0,
    val samplesPerFrame: Int = 0,
    val frameCount: Int = 0,
    val audioBytes: Long = 0L,
    val audioCrc32: String? = null,
    val fileCrc32: String? = null,
    val firstSamples: List<Int> = emptyList(),
    val name: String? = null,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val codec: String = "pcm16",
    val sampleRate: Int = 8_000,
    val channels: Int = 1,
    val packetMs: Int = 40,
    val crc32: String? = null,
    val createdAt: String? = null,
    val storeTaskId: String? = null,
    val timestamp: Long? = null
)
