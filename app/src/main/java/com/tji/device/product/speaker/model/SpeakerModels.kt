package com.tji.device.product.speaker.model

import com.tji.device.product.runtime.ProductRuntimePayload

data class SpeakerDeviceState(
    val serialNumber: String,
    val name: String? = null,
    val isOnline: Boolean = false,
    val playing: Boolean = false,
    val talking: Boolean = false,
    val currentTalkId: String? = null,
    val currentFile: String? = null,
    val volume: Int = DEFAULT_SPEAKER_VOLUME,
    val servoAngle: Int? = null,
    val servo: SpeakerServoState? = null,
    val lastError: String? = null,
    val network: String? = null,
    val lastAck: SpeakerAck? = null,
    val records: List<SpeakerRecord> = emptyList(),
    val recordOffset: Int = 0,
    val recordLimit: Int = 8,
    val recordTotal: Int = 0,
    val recordHasMore: Boolean = false,
    val recordListTimestamp: Long? = null,
    val storageStatus: SpeakerStorageStatus? = null,
    val lastRecordEvent: SpeakerRecordEvent? = null,
    val outputQuality: String? = null,
    val audio: SpeakerAudioDiagnostics? = null,
    val mcuFeedback: SpeakerMcuFeedbackDiagnostics? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload

data class SpeakerServoState(
    val currentAngle: Int? = null,
    val targetAngle: Int? = null,
    val speedDps: Int? = null,
    val moving: Boolean = false,
    val sweepActive: Boolean = false,
    val stepMode: Boolean = false,
    val stepAngle: Int? = null,
    val minAngle: Int? = null,
    val maxAngle: Int? = null,
    val cyclesLeft: Int? = null,
    val cyclesDone: Int? = null,
    val infinite: Boolean = false
)

data class SpeakerAudioDiagnostics(
    val packets: Long = 0L,
    val lostPackets: Long = 0L,
    val badPackets: Long = 0L,
    val bufferedBytes: Int = 0,
    val bufferedMinBytes: Int = 0,
    val bufferedMaxBytes: Int = 0,
    val outUnderruns: Long = 0L,
    val saiErrors: Long = 0L,
    val saiOvr: Long = 0L,
    val dmaHalf: Long = 0L,
    val dmaFull: Long = 0L,
    val dmaErrors: Long = 0L,
    val fillLate: Long = 0L,
    val peakQ15: Int = 0,
    val rmsQ15: Int = 0,
    val clipCount: Long = 0L,
    val limiterCount: Long = 0L
)

data class SpeakerMcuFeedbackDiagnostics(
    val active: Boolean = false,
    val packetMs: Int = 0,
    val aecActive: Boolean = false,
    val aecFrames: Long = 0L,
    val aecReferenceMisses: Long = 0L,
    val aecReferenceBuilt: Long = 0L,
    val aecReferenceRead: Long = 0L,
    val aecReferenceDrops: Long = 0L,
    val aecReferenceQueued: Int = 0,
    val aecInputLevelQ15: Int = 0,
    val aecOutputLevelQ15: Int = 0,
    val aecReferenceLevelQ15: Int = 0,
    val aecResidualPermille: Int = 0,
    val aecLastUs: Long = 0L,
    val aecMaxUs: Long = 0L,
    val aecDeadlineMisses: Long = 0L,
    val taskStackFreeWords: Int = 0
)

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
        val angle: Int,
        val speedDps: Int = 60
    ) : SpeakerCommand(msgId, 107, "SET_SERVO_ANGLE")

    class ServoSweepTest(
        msgId: String,
        val minAngle: Int,
        val maxAngle: Int,
        val speedDps: Int = 60,
        val cycles: Int = 1,
        val durationMs: Int = 300
    ) : SpeakerCommand(msgId, 125, "SERVO_SWEEP_TEST")

    class ServoStepTest(
        msgId: String,
        val minAngle: Int,
        val maxAngle: Int,
        val stepAngle: Int = 10,
        val speedDps: Int = 30,
        val intervalMs: Int = 2_000
    ) : SpeakerCommand(msgId, 126, "SERVO_STEP_TEST")

    /**
     * 开关 MCU 板载麦克风回传。与手机麦克风录音/喊话无关。
     */
    class SetMcuMicrophoneFeedback(
        msgId: String,
        val enabled: Boolean,
        val sessionId: String = "",
        val talkId: String = "",
        val codec: String = "opus",
        val sampleRate: Int = 16_000,
        val channels: Int = 1,
        val packetMs: Int = 20,
        val ttlMs: Long = 30_000L
    ) : SpeakerCommand(msgId, 116, "SET_PLAYBACK_FEEDBACK")

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
        val recordType: String = "record",
        val downloadUrl: String,
        val fileSize: Long,
        val crc32: String,
        val durationMs: Int,
        val container: String = "ogg",
        val codec: String = "opus",
        val sampleRate: Int = 24_000,
        val channels: Int = 1,
        val packetMs: Int = 20,
        val bitrate: Int = 24_000,
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
    val codec: String = "opus",
    val sampleRate: Int = 24_000,
    val channels: Int = 1,
    val packetMs: Int = 20,
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
    val codec: String = "opus",
    val sampleRate: Int = 24_000,
    val channels: Int = 1,
    val packetMs: Int = 20,
    val crc32: String? = null,
    val createdAt: String? = null,
    val path: String? = null,
    val visible: Boolean = true,
    val storeTaskId: String? = null,
    val timestamp: Long? = null
)
