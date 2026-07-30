package com.tji.device.product.speaker.viewmodel

import android.util.Log
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerOpusFile
import com.tji.device.product.speaker.audio.SpeakerRecordUploadClient
import com.tji.device.product.speaker.audio.SpeakerRecordUploadResult
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates the asynchronous "upload -> device download -> record event" lifecycle.
 *
 * Keeping this state machine outside the screen ViewModel makes duplicate-event handling,
 * timeout fallback and paged record confirmation testable as one unit.
 */
internal class SpeakerRecordSaveCoordinator(
    private val scope: CoroutineScope,
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val uploadClient: SpeakerRecordUploadClient,
    private val commands: SpeakerCommandCoordinator,
    private val deviceActions: SpeakerDeviceActions,
    private val devices: StateFlow<List<SpeakerDeviceState>>,
    private val feedback: MutableStateFlow<SpeakerCommandFeedback>,
    private val talkState: MutableStateFlow<SpeakerTalkState>,
    private val clearFeedbackAfter: (String?) -> Unit
) {
    private val pendingSaves = SpeakerPendingRecordSaveTracker()
    private var lastSaveEventKey: String? = null
    private var lastMutationEventKey: String? = null

    suspend fun uploadAndRequestPlayback(request: SpeakerRecordUploadRequest) {
        talkState.value = talkState.value.copy(progress = 0.40f)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在上传${request.label}"
        )
        val upload = uploadClient.uploadTempRecord(
            deviceId = request.serialNumber,
            recordId = request.recordId,
            name = request.recordName,
            opusFile = request.opusFile
        )
        talkState.value = talkState.value.copy(progress = 0.80f)
        val downloadMsgId = id(request.downloadMsgPrefix)
        val pending = PendingRecordSave(
            serialNumber = request.serialNumber,
            recordId = upload.recordId,
            commandMsgId = downloadMsgId,
            record = upload.toSpeakerRecord(request.recordName, request.createdAt),
            startedAt = request.startedAt,
            autoPlayVolume = if (request.fallbackPlayAfterSave) {
                request.autoPlayVolume?.coerceIn(0, 100)
            } else {
                null
            },
            autoPlayLabel = request.autoPlayLabel,
            waitForPlayback = request.autoPlayInDownload,
            cacheRecord = !request.temporary,
            timeoutMs = if (request.autoPlayInDownload) {
                RECORD_SAVE_EVENT_TIMEOUT_MS + upload.durationMs + RECORD_PLAYBACK_TIMEOUT_MARGIN_MS
            } else {
                RECORD_SAVE_EVENT_TIMEOUT_MS
            }
        )
        replace(pending)
        try {
            val published = commands.publish(
                serialNumber = request.serialNumber,
                command = SpeakerCommand.RecordDownload(
                    msgId = downloadMsgId,
                    recordId = upload.recordId,
                    storeTaskId = request.storeTaskId,
                    createdAt = request.createdAt,
                    name = request.recordName,
                    recordType = request.recordType,
                    downloadUrl = upload.downloadUrl,
                    fileSize = upload.fileSize,
                    crc32 = upload.crc32,
                    durationMs = upload.durationMs,
                    container = upload.container,
                    codec = upload.codec,
                    sampleRate = upload.sampleRate,
                    channels = upload.channels,
                    packetMs = upload.packetMs,
                    bitrate = upload.bitrate,
                    temporary = request.temporary,
                    visible = request.visible,
                    autoPlay = request.autoPlayInDownload,
                    playbackVolume = request.autoPlayVolume
                ),
                label = request.label
            )
            if (!published) {
                discard(pending)
                talkState.value = SpeakerTalkState(
                    mode = SpeakerTalkMode.Idle,
                    error = "${request.label}指令发送失败"
                )
                return
            }
            talkState.value = talkState.value.copy(progress = 0.92f)
            feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Pending,
                text = "等待设备保存${request.label}"
            )
            waitForEvent(pending)
        } catch (throwable: Throwable) {
            discard(pending)
            throw throwable
        }
    }

    fun handleEvent(serialNumber: String, event: SpeakerRecordEvent) {
        handleSaveEvent(serialNumber, event)
        handleMutationEvent(serialNumber, event)
    }

    fun clear() {
        pendingSaves.clear()?.commandMsgId?.let(commands::removePending)
    }

    fun replace(pending: PendingRecordSave) {
        pendingSaves.replace(pending)?.commandMsgId?.let(commands::removePending)
    }

    fun discard(pending: PendingRecordSave): Boolean {
        val discarded = pendingSaves.takeIfCurrent(pending) ?: return false
        commands.removePending(discarded.commandMsgId)
        return true
    }

    fun fail(pending: PendingRecordSave, message: String) {
        val failed = pendingSaves.takeIfCurrent(pending) ?: return
        commands.removePending(failed.commandMsgId)
        talkState.value = SpeakerTalkState(mode = SpeakerTalkMode.Idle, error = message)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }

    fun waitForEvent(pending: PendingRecordSave) {
        scope.launch(Dispatchers.IO) {
            delay(pending.timeoutMs)
            if (!pendingSaves.isCurrent(pending)) return@launch
            if (!pending.waitForPlayback &&
                confirmFromPagedList(pending.serialNumber, pending.recordId)
            ) {
                complete(pending)
                return@launch
            }
            if (!discard(pending)) return@launch
            Log.w(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "record save timeout recordId=${pending.recordId} " +
                    "elapsedMs=${System.currentTimeMillis() - pending.startedAt}"
            )
            refreshDeviceRecords(pending.serialNumber)
            talkState.value = SpeakerTalkState(
                mode = SpeakerTalkMode.Idle,
                error = "等待设备保存反馈超时"
            )
            feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Timeout,
                text = "等待设备保存反馈超时"
            )
        }
    }

    private fun handleSaveEvent(serialNumber: String, event: SpeakerRecordEvent) {
        val pending = pendingSaves.current() ?: return
        if (pending.serialNumber != serialNumber || event.recordId != pending.recordId) return
        val eventKey = listOf(event.type, event.recordId, event.code, event.timestamp).joinToString("|")
        if (eventKey == lastSaveEventKey) return
        lastSaveEventKey = eventKey
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "record save device event type=${event.type} ok=${event.ok} code=${event.code} " +
                "recordId=${event.recordId} elapsedMs=${System.currentTimeMillis() - pending.startedAt} " +
                "msg=${event.message}"
        )
        when (event.type) {
            "record_playback" -> {
                if (event.ok) {
                    complete(pending, "临时音频播放完成")
                } else {
                    fail(pending, event.message.ifBlank { "临时音频播放失败" })
                }
            }

            "record_saved" -> {
                if (pending.waitForPlayback) {
                    talkState.value = talkState.value.copy(progress = 0.98f)
                    feedback.value = SpeakerCommandFeedback(
                        status = SpeakerCommandFeedbackStatus.Pending,
                        text = "设备已下载，正在播放"
                    )
                } else {
                    complete(pending)
                }
            }

            "record_failed" -> fail(pending, event.message.ifBlank { "设备保存录音失败" })
            "record_progress" -> {
                talkState.value = talkState.value.copy(
                    progress = (event.progress / 100f).coerceIn(0.92f, 0.98f)
                )
                if (event.progress >= 100 && !pending.waitForPlayback) {
                    confirmAfterProgress(serialNumber, pending)
                }
            }
        }
    }

    private fun handleMutationEvent(serialNumber: String, event: SpeakerRecordEvent) {
        val deleteAlreadyGone = event.type == "record_deleted" &&
            !event.recordId.isNullOrBlank() &&
            (event.code == 404 || event.message.contains("not found", ignoreCase = true))
        if (!event.ok && !deleteAlreadyGone) return
        val shouldRefresh = when (event.type) {
            "record_deleted", "record_updated" -> true
            "record_saved" -> pendingSaves.current()?.recordId != event.recordId
            else -> false
        }
        if (!shouldRefresh) return
        val eventKey = listOf(
            "mutation",
            event.type,
            event.recordId,
            event.code,
            event.timestamp
        ).joinToString("|")
        if (eventKey == lastMutationEventKey) return
        lastMutationEventKey = eventKey
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "record mutation refresh type=${event.type} recordId=${event.recordId} sn=$serialNumber"
        )
        refreshDeviceRecords(serialNumber)
    }

    private fun complete(pending: PendingRecordSave, successText: String? = null) {
        val completed = pendingSaves.takeIfCurrent(pending) ?: return
        commands.removePending(completed.commandMsgId)
        scope.launch(Dispatchers.IO) {
            talkState.value = talkState.value.copy(progress = 1f)
            feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Success,
                text = successText ?: if (completed.autoPlayVolume != null) {
                    "文件保存完成，正在播放"
                } else {
                    "录音保存完成"
                }
            )
            if (completed.cacheRecord) {
                stateRepository.upsertRecord(completed.serialNumber, completed.record)
            }
            refreshDeviceRecords(completed.serialNumber)
            completed.autoPlayVolume?.let { volume ->
                commands.send(
                    serialNumber = completed.serialNumber,
                    command = SpeakerCommand.PlayRecord(
                        msgId = id("tone-file-play"),
                        recordId = completed.recordId,
                        volume = volume
                    ),
                    label = completed.autoPlayLabel
                )
            }
            delay(SAVE_PROGRESS_DONE_VISIBLE_MS)
            if (pendingSaves.current() != null) return@launch
            if (talkState.value.mode == SpeakerTalkMode.SavingRecord ||
                talkState.value.mode == SpeakerTalkMode.Tts
            ) {
                talkState.value = talkState.value.copy(
                    mode = SpeakerTalkMode.Idle,
                    progress = 0f
                )
            }
            clearFeedbackAfter(null)
        }
    }

    private fun confirmAfterProgress(serialNumber: String, pending: PendingRecordSave) {
        val confirmationJob = scope.launch(
            context = Dispatchers.IO,
            start = CoroutineStart.LAZY
        ) {
            delay(RECORD_PROGRESS_DONE_CONFIRM_DELAY_MS)
            if (!pendingSaves.isCurrent(pending)) return@launch
            if (confirmFromPagedList(serialNumber, pending.recordId)) {
                complete(pending)
            }
        }
        if (pendingSaves.replaceProgressConfirmationJob(pending, confirmationJob)) {
            confirmationJob.start()
        } else {
            confirmationJob.cancel()
        }
    }

    private suspend fun confirmFromPagedList(serialNumber: String, recordId: String): Boolean {
        var offset = 0
        repeat(RECORD_CONFIRM_MAX_PAGES) {
            controlRepository.sendCommand(
                serialNumber,
                SpeakerCommand.ListRecords(
                    msgId = id("record-confirm-list"),
                    offset = offset,
                    limit = RECORD_LIST_PAGE_SIZE,
                    order = "desc"
                )
            )
            delay(RECORD_CONFIRM_PAGE_WAIT_MS)
            val state = devices.value.firstOrNull { it.serialNumber == serialNumber }
            if (state?.records.orEmpty().any { it.recordId == recordId }) {
                Log.d(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "record save confirmed by list recordId=$recordId offset=$offset"
                )
                return true
            }
            val total = state?.recordTotal?.takeIf { it > 0 } ?: RECORD_CONFIRM_MAX_RECORDS
            offset += RECORD_LIST_PAGE_SIZE
            if (offset >= minOf(total, RECORD_CONFIRM_MAX_RECORDS)) return false
        }
        return false
    }

    private fun refreshDeviceRecords(serialNumber: String) {
        deviceActions.refreshRecords(serialNumber, 0, RECORD_LIST_PAGE_SIZE, "desc")
        deviceActions.refreshStorageStatus(serialNumber)
    }

    private fun id(action: String): String = DeviceCommandId.next("speaker", action)

    private companion object {
        const val SAVE_PROGRESS_DONE_VISIBLE_MS = 500L
        const val RECORD_SAVE_EVENT_TIMEOUT_MS = 20_000L
        const val RECORD_PLAYBACK_TIMEOUT_MARGIN_MS = 5_000L
        const val RECORD_LIST_PAGE_SIZE = 4
        const val RECORD_PROGRESS_DONE_CONFIRM_DELAY_MS = 1_500L
        const val RECORD_CONFIRM_PAGE_WAIT_MS = 700L
        const val RECORD_CONFIRM_MAX_RECORDS = 32
        const val RECORD_CONFIRM_MAX_PAGES = RECORD_CONFIRM_MAX_RECORDS / RECORD_LIST_PAGE_SIZE
    }
}

internal data class SpeakerRecordUploadRequest(
    val serialNumber: String,
    val recordId: String,
    val storeTaskId: String,
    val createdAt: String,
    val recordName: String,
    val recordType: String = "record",
    val opusFile: SpeakerOpusFile,
    val label: String,
    val downloadMsgPrefix: String,
    val autoPlayVolume: Int?,
    val autoPlayLabel: String,
    val startedAt: Long,
    val temporary: Boolean = false,
    val visible: Boolean = true,
    val autoPlayInDownload: Boolean = false,
    val fallbackPlayAfterSave: Boolean = true
)

private fun SpeakerRecordUploadResult.toSpeakerRecord(
    name: String,
    createdAt: String
): SpeakerRecord =
    SpeakerRecord(
        recordId = recordId,
        name = name,
        fileSize = fileSize,
        durationMs = durationMs.toLong(),
        codec = codec,
        sampleRate = sampleRate,
        channels = channels,
        packetMs = packetMs,
        crc32 = crc32,
        createdAt = createdAt
    )
