package com.tji.device.product.radiodetection.viewmodel

import com.tji.device.error.toUserVisibleMessage
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.protocol.RadioRidParser
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns every page-scoped RadioDetection task while preserving the ViewModel API as a facade. */
internal class RadioDetectionTaskCoordinator(
    private val scope: CoroutineScope,
    private val repository: RadioDetectionRepository,
    private val controlRepository: RadioDetectionControlRepository,
    private val replayStore: RadioDetectionReplayStore
) {
    private val mutableFeedback = MutableStateFlow<RadioRgbCommandFeedback?>(null)
    val feedback: StateFlow<RadioRgbCommandFeedback?> = mutableFeedback.asStateFlow()

    private val commandTracker = RadioRgbCommandTracker()
    private var activeSerialNumber: String? = null
    private var generation = 0L
    private var pruneJob: Job? = null
    private var ackJob: Job? = null
    private val commandJobs = mutableSetOf<Job>()

    fun bind(serialNumber: String) {
        if (activeSerialNumber == serialNumber && pruneJob?.isActive == true && ackJob?.isActive == true) return
        unbind()
        activeSerialNumber = serialNumber
        generation += 1
        val boundGeneration = generation
        pruneJob = scope.launch {
            while (isCurrent(serialNumber, boundGeneration)) {
                delay(TARGET_PRUNE_INTERVAL_MILLIS)
                if (isCurrent(serialNumber, boundGeneration)) repository.pruneExpiredTargets()
            }
        }
        ackJob = scope.launch {
            repository.devices.collect { states ->
                if (!isCurrent(serialNumber, boundGeneration)) return@collect
                states.firstOrNull { it.serialNumber == serialNumber }
                    ?.rgbAck
                    ?.let { ack ->
                        val result = commandTracker.complete(ack) ?: return@let
                        mutableFeedback.value = result
                        clearFeedbackAfter(serialNumber, boundGeneration, ack.msgId)
                    }
            }
        }
    }

    fun unbind(serialNumber: String? = null) {
        if (serialNumber != null && activeSerialNumber != serialNumber) return
        generation += 1
        activeSerialNumber = null
        pruneJob?.cancel()
        pruneJob = null
        ackJob?.cancel()
        ackJob = null
        commandJobs.forEach { it.cancel() }
        commandJobs.clear()
        commandTracker.reset()
        mutableFeedback.value = null
    }

    fun replayLatestRid(serialNumber: String): Boolean {
        ensureBound(serialNumber)
        val payload = replayStore.latestPayload(serialNumber) ?: return false
        val packet = RadioRidParser.parse(payload) ?: return false
        val boundGeneration = generation
        launchCommandJob {
            if (isCurrent(serialNumber, boundGeneration)) {
                repository.upsertRidPacket(serialNumber, packet)
            }
        }
        return true
    }

    fun sendRgbCommand(
        serialNumber: String,
        mode: RadioRgbMode,
        color: RadioRgbColor,
        brightness: Int,
        speed: Int?,
        save: Boolean
    ) {
        ensureBound(serialNumber)
        val boundGeneration = generation
        val msgId = DeviceCommandId.next("radio", "rgb")
        if (repository.devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline != true) {
            mutableFeedback.value = commandTracker.start(serialNumber, msgId, "设备离线，无法发送灯语指令")
            commandTracker.fail(msgId, "设备离线，无法发送灯语指令")?.let {
                mutableFeedback.value = it
            }
            clearFeedbackAfter(serialNumber, boundGeneration, msgId)
            return
        }
        val command = RadioRgbCommand(
            msgId = msgId,
            mode = mode,
            color = if (color.supportedBy(mode)) color else RadioRgbColor.Red,
            brightness = brightness,
            speed = speed,
            save = save
        )
        mutableFeedback.value = commandTracker.start(
            serialNumber = serialNumber,
            msgId = msgId,
            text = if (save) "正在保存默认灯语" else "正在预览灯语"
        )

        controlRepository.sendRgbCommand(
            serialNumber = serialNumber,
            command = command,
            onSuccess = {
                launchCommandJob {
                    if (!isCurrent(serialNumber, boundGeneration)) return@launchCommandJob
                    commandTracker.markPublished(msgId)?.let { mutableFeedback.value = it }
                }
            },
            onError = { throwable ->
                launchCommandJob {
                    if (!isCurrent(serialNumber, boundGeneration)) return@launchCommandJob
                    val result = commandTracker.fail(
                        msgId,
                        throwable.toUserVisibleMessage("灯语指令发送失败")
                    ) ?: return@launchCommandJob
                    mutableFeedback.value = result
                    clearFeedbackAfter(serialNumber, boundGeneration, msgId)
                }
            }
        )
        launchCommandJob {
            delay(RGB_ACK_TIMEOUT_MILLIS)
            if (!isCurrent(serialNumber, boundGeneration)) return@launchCommandJob
            val result = commandTracker.timeout(msgId) ?: return@launchCommandJob
            mutableFeedback.value = result
            clearFeedbackAfter(serialNumber, boundGeneration, msgId)
        }
    }

    private fun ensureBound(serialNumber: String) {
        if (activeSerialNumber != serialNumber) bind(serialNumber)
    }

    private fun clearFeedbackAfter(serialNumber: String, boundGeneration: Long, msgId: String) {
        launchCommandJob {
            delay(RGB_FEEDBACK_VISIBLE_MILLIS)
            if (isCurrent(serialNumber, boundGeneration) && commandTracker.clearIfCurrent(msgId)) {
                mutableFeedback.value = null
            }
        }
    }

    private fun launchCommandJob(block: suspend () -> Unit) {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                commandJobs.remove(job)
            }
        }
        commandJobs += job
        job.start()
    }

    private fun isCurrent(serialNumber: String, boundGeneration: Long): Boolean =
        activeSerialNumber == serialNumber && generation == boundGeneration

    private companion object {
        const val TARGET_PRUNE_INTERVAL_MILLIS = 5_000L
        const val RGB_ACK_TIMEOUT_MILLIS = 3_000L
        const val RGB_FEEDBACK_VISIBLE_MILLIS = 2_400L
    }
}
