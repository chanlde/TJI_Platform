package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.publishThenWaitForDeviceAck
import com.tji.device.product.speaker.error.toSpeakerUserVisibleMessage
import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns MQTT command publication, ACK correlation and command feedback.
 *
 * UI-specific audio/record state remains in [SpeakerControlViewModel]; this
 * coordinator only handles the common command lifecycle.
 */
internal class SpeakerCommandCoordinator(
    private val scope: CoroutineScope,
    private val repository: SpeakerControlRepository,
    private val feedback: MutableStateFlow<SpeakerCommandFeedback>,
    private val requireOnline: (serialNumber: String, action: String) -> Boolean,
    private val clearFeedbackAfter: (msgId: String?) -> Unit
) {
    private val pending = SpeakerPendingCommandTracker()

    fun handleAck(serialNumber: String, ack: SpeakerAck) {
        val label = pending.acknowledge(serialNumber, ack) ?: return
        if (feedback.value.msgId != ack.msgId) return
        feedback.value = SpeakerCommandFeedback(
            msgId = ack.msgId,
            status = if (ack.ok) {
                SpeakerCommandFeedbackStatus.Success
            } else {
                SpeakerCommandFeedbackStatus.Failed
            },
            text = if (ack.ok) "${label}已确认" else "${label}失败"
        )
        clearFeedbackAfter(ack.msgId)
    }

    fun send(
        serialNumber: String,
        command: SpeakerCommand,
        label: String,
        awaitAck: Boolean = true
    ) {
        if (!canSend(serialNumber, command, label)) return
        if (awaitAck) pending.track(command.msgId, serialNumber, label)
        feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "${label}已发送"
        )
        scope.launch {
            try {
                if (awaitAck) {
                    publishThenWaitForDeviceAck(COMMAND_ACK_TIMEOUT_MS) {
                        repository.sendCommand(serialNumber, command)
                    }
                } else {
                    repository.sendCommand(serialNumber, command)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                pending.remove(command.msgId)
                updateFailureIfCurrent(command, label, throwable)
                return@launch
            }
            if (awaitAck && pending.remove(command.msgId) && feedback.value.msgId == command.msgId) {
                feedback.value = SpeakerCommandFeedback(
                    msgId = command.msgId,
                    status = SpeakerCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    suspend fun publish(
        serialNumber: String,
        command: SpeakerCommand,
        label: String
    ): Boolean {
        if (!canSend(serialNumber, command, label)) return false
        feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "${label}发送中"
        )
        return try {
            repository.sendCommand(serialNumber, command)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            updateFailureIfCurrent(command, label, throwable)
            false
        }
    }

    suspend fun sendAndAwaitAck(
        serialNumber: String,
        command: SpeakerCommand,
        label: String
    ): SpeakerAck? {
        if (!canSend(serialNumber, command, label)) return null
        val waiter = CompletableDeferred<SpeakerAck>()
        pending.track(command.msgId, serialNumber, label, waiter)
        feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Pending,
            text = "${label}已发送"
        )
        try {
            repository.sendCommand(serialNumber, command)
        } catch (cancellation: CancellationException) {
            pending.remove(command.msgId)
            throw cancellation
        } catch (throwable: Throwable) {
            pending.remove(command.msgId)
            updateFailureIfCurrent(command, label, throwable, clearAfter = false)
            return null
        }
        val ack = withTimeoutOrNull(COMMAND_ACK_TIMEOUT_MS) { waiter.await() }
        if (ack == null) {
            pending.remove(command.msgId)
            if (feedback.value.msgId == command.msgId) {
                feedback.value = SpeakerCommandFeedback(
                    msgId = command.msgId,
                    status = SpeakerCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
            }
        }
        return ack
    }

    fun removePending(msgId: String) {
        pending.remove(msgId)
    }

    private fun canSend(
        serialNumber: String,
        command: SpeakerCommand,
        label: String
    ): Boolean = !command.requiresOnlineDevice() || requireOnline(serialNumber, label)

    private fun updateFailureIfCurrent(
        command: SpeakerCommand,
        label: String,
        throwable: Throwable,
        clearAfter: Boolean = true
    ) {
        if (feedback.value.msgId != command.msgId) return
        feedback.value = SpeakerCommandFeedback(
            msgId = command.msgId,
            status = SpeakerCommandFeedbackStatus.Failed,
            text = throwable.toSpeakerUserVisibleMessage("${label}发送失败")
        )
        if (clearAfter) clearFeedbackAfter(command.msgId)
    }

    private companion object {
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
    }
}
