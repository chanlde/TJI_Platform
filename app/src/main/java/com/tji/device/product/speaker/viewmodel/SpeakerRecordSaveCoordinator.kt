package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.core.SpeakerLogger
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerMediaTransferClient
import com.tji.device.product.speaker.audio.SpeakerMediaTransferMode
import com.tji.device.product.speaker.audio.SpeakerMediaTransferRequest
import com.tji.device.product.speaker.audio.SpeakerOpusFile
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
 * Coordinates the asynchronous "reliable Ogg UDP transfer -> record event" lifecycle.
 *
 * Keeping this state machine outside the screen ViewModel makes duplicate-event handling,
 * timeout fallback and paged record confirmation testable as one unit.
 */
internal class SpeakerRecordSaveCoordinator(
    private val scope: CoroutineScope,
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
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

    suspend fun transferAndAwaitResult(request: SpeakerRecordTransferRequest) {
        talkState.value = talkState.value.copy(progress = 0.40f)
        feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "正在传输${request.label}"
        )
        val transferId = id(request.downloadMsgPrefix)
        val pending = PendingRecordSave(
            serialNumber = request.serialNumber,
            recordId = request.recordId,
            commandMsgId = transferId,
            record = request.opusFile.toSpeakerRecord(
                request.recordId, request.recordName, request.createdAt
            ),
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
                RECORD_SAVE_EVENT_TIMEOUT_MS +
                    request.opusFile.durationMs + RECORD_PLAYBACK_TIMEOUT_MARGIN_MS
            } else {
                RECORD_SAVE_EVENT_TIMEOUT_MS
            }
        )
        replace(pending)
        try {
            audioRelay.sendMedia(
                SpeakerMediaTransferRequest(
                    deviceId = request.serialNumber,
                    sessionId = request.storeTaskId,
                    recordId = request.recordId,
                    name = request.recordName,
                    createdAt = request.createdAt,
                    opusFile = request.opusFile,
                    mode = if (request.temporary) {
                        SpeakerMediaTransferMode.PlayTemporary
                    } else {
                        SpeakerMediaTransferMode.Store
                    },
                    volume = request.autoPlayVolume ?: 100,
                    recordType = when (request.recordType) {
                        "tts" -> SpeakerMediaTransferClient.RECORD_TYPE_TTS
                        "test" -> SpeakerMediaTransferClient.RECORD_TYPE_TEST
                        else -> SpeakerMediaTransferClient.RECORD_TYPE_RECORD
                    },
                    visible = request.visible
                )
            )
            /* MCU 在最终 UDP ACK 前发布业务事件；MQTT 可能先到 App。 */
            if (!pendingSaves.isCurrent(pending)) {
                return
            }
            talkState.value = talkState.value.copy(progress = 0.92f)
            feedback.value = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Pending,
                text = if (request.temporary) {
                    "设备正在播放${request.label}"
                } else {
                    "等待设备保存${request.label}"
                }
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
            SpeakerLogger.warn(
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
        SpeakerLogger.debug(
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
        SpeakerLogger.debug(
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
                SpeakerLogger.debug(
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

internal data class SpeakerRecordTransferRequest(
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

private fun SpeakerOpusFile.toSpeakerRecord(
    recordId: String,
    name: String,
    createdAt: String
): SpeakerRecord =
    SpeakerRecord(
        recordId = recordId,
        name = name,
        fileSize = fileSize.toLong(),
        durationMs = durationMs.toLong(),
        codec = codec,
        sampleRate = sampleRate,
        channels = channels,
        packetMs = packetMs,
        crc32 = crc32,
        createdAt = createdAt
    )
