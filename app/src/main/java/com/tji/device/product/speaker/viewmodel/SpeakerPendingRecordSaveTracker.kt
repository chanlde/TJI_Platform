package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerRecord
import kotlinx.coroutines.Job

/**
 * 管理当前等待设备确认的录音保存任务。
 *
 * 上传、MQTT 事件、分页确认和超时分别运行在不同协程。完成或失败任务时必须先原子地
 * 认领当前任务，避免重复事件执行两次完成逻辑，也避免旧任务清掉后来开始的新任务。
 */
internal class SpeakerPendingRecordSaveTracker {
    private var pending: PendingRecordSave? = null
    private var progressConfirmationJob: Job? = null

    @Synchronized
    fun replace(save: PendingRecordSave): PendingRecordSave? {
        progressConfirmationJob?.cancel()
        progressConfirmationJob = null
        return pending.also { pending = save }
    }

    @Synchronized
    fun current(): PendingRecordSave? = pending

    @Synchronized
    fun isCurrent(save: PendingRecordSave): Boolean = pending === save

    @Synchronized
    fun takeIfCurrent(save: PendingRecordSave): PendingRecordSave? {
        if (pending !== save) return null
        progressConfirmationJob?.cancel()
        progressConfirmationJob = null
        pending = null
        return save
    }

    @Synchronized
    fun clear(): PendingRecordSave? {
        progressConfirmationJob?.cancel()
        progressConfirmationJob = null
        return pending.also { pending = null }
    }

    @Synchronized
    fun replaceProgressConfirmationJob(save: PendingRecordSave, job: Job): Boolean {
        if (pending !== save) return false
        progressConfirmationJob?.cancel()
        progressConfirmationJob = job
        return true
    }
}

internal data class PendingRecordSave(
    val serialNumber: String,
    val recordId: String,
    val commandMsgId: String,
    val record: SpeakerRecord,
    val startedAt: Long,
    val autoPlayVolume: Int? = null,
    val waitForPlayback: Boolean = false,
    val cacheRecord: Boolean = true,
    val timeoutMs: Long = 20_000L,
    val autoPlayLabel: String = "播放文件"
)
