package com.tji.device.product.droppersixstage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.common.publishThenWaitForDeviceAck
import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperControlLimits
import com.tji.device.product.droppersixstage.model.DROPPER_STAGE_COUNT
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepository
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DropperSixStageViewModel(
    private val stateRepository: DropperSixStageRepository,
    private val controlRepository: DropperSixStageControlRepository
) : ViewModel() {
    val devices: StateFlow<List<DropperSixStageState>> = stateRepository.devices

    private val _commandFeedback = MutableStateFlow(DropperCommandFeedback())
    val commandFeedback: StateFlow<DropperCommandFeedback> = _commandFeedback.asStateFlow()

    private val _armedDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val armedDeviceIds: StateFlow<Set<String>> = _armedDeviceIds.asStateFlow()

    private val pendingCommands = DropperPendingCommandTracker()

    init {
        viewModelScope.launch {
            devices.collect { states ->
                _armedDeviceIds.value = states.asSequence()
                    .filter { it.isOnline && it.isArmed == true }
                    .map { it.serialNumber }
                    .toSet()
                states.asSequence()
                    .mapNotNull { state -> state.lastAck?.let { state.serialNumber to it } }
                    .forEach { (serialNumber, ack) ->
                        val pending = pendingCommands.complete(ack.msgId, serialNumber) ?: return@forEach
                        if (_commandFeedback.value.msgId != ack.msgId) return@forEach
                        if (ack.ok) {
                            when (pending.safetyEffect) {
                                DropperSafetyEffect.Arm ->
                                    stateRepository.updateArmedStatus(pending.serialNumber, true)
                                DropperSafetyEffect.Disarm ->
                                    stateRepository.updateArmedStatus(pending.serialNumber, false)
                                DropperSafetyEffect.None -> Unit
                            }
                        }
                        val feedback = DropperCommandFeedback(
                            serialNumber = pending.serialNumber,
                            msgId = ack.msgId,
                            status = if (ack.ok) DropperCommandFeedbackStatus.Success else DropperCommandFeedbackStatus.Failed,
                            text = if (ack.ok) {
                                "${pending.label}成功"
                            } else {
                                ack.message?.takeIf { it.isNotBlank() }
                                    ?.let { "${pending.label}失败：$it" }
                                    ?: "${pending.label}失败"
                            }
                        )
                        _commandFeedback.value = feedback
                        clearFeedbackAfter(ack.msgId)
                    }
            }
        }
    }

    fun toggleStage(serialNumber: String, stage: Int, open: Boolean) {
        if (!DropperControlLimits.isValidStage(stage)) {
            rejectLocal(serialNumber, "通道编号无效")
            return
        }
        send(
            serialNumber = serialNumber,
            command = DropperSixStageCommand.StageSwitch(newMsgId("stage-$stage"), stage, open),
            label = if (open) "${stage}段开钩" else "${stage}段关钩"
        )
    }

    fun arm(serialNumber: String) {
        send(serialNumber, DropperSixStageCommand.Arm(newMsgId("arm")), "解锁")
    }

    fun disarm(serialNumber: String) {
        send(serialNumber, DropperSixStageCommand.Disarm(newMsgId("disarm")), "上锁")
    }

    fun timedOpenStage(serialNumber: String, stage: Int, durationMs: Int) {
        if (!DropperControlLimits.isValidStage(stage)) {
            rejectLocal(serialNumber, "通道编号无效")
            return
        }
        val normalizedDuration = DropperControlLimits.normalizeOpenDuration(durationMs)
        send(
            serialNumber = serialNumber,
            command = DropperSixStageCommand.StageSwitch(
                msgId = newMsgId("stage-$stage-timed"),
                stage = stage,
                open = true,
                durationMs = normalizedDuration
            ),
            label = "${stage}段自动开钩"
        )
    }

    fun toggleAll(serialNumber: String, open: Boolean, durationMs: Int? = null) {
        val normalizedDuration = durationMs
            ?.takeIf { open }
            ?.let(DropperControlLimits::normalizeOpenDuration)
        send(
            serialNumber = serialNumber,
            command = DropperSixStageCommand.AllStages(
                msgId = newMsgId("all"),
                open = open,
                durationMs = normalizedDuration
            ),
            label = if (open) "全部抛投" else "全部复位"
        )
    }

    fun ping(serialNumber: String) {
        send(serialNumber, DropperSixStageCommand.Ping(newMsgId("ping")), "连通测试")
    }

    private fun send(serialNumber: String, command: DropperSixStageCommand, label: String) {
        if (command.requiresOnlineDevice() && !isDeviceOnline(serialNumber)) {
            rejectOffline(serialNumber, label)
            return
        }
        if (command.requiresArmedDevice() && serialNumber !in _armedDeviceIds.value) {
            rejectLocal(serialNumber, "请先解锁设备")
            return
        }
        if (!pendingCommands.start(
                command.msgId,
                serialNumber,
                command.resourceKeys(serialNumber),
                label,
                command.safetyEffect()
            )
        ) {
            return
        }
        _commandFeedback.value = DropperCommandFeedback(
            serialNumber = serialNumber,
            msgId = command.msgId,
            status = DropperCommandFeedbackStatus.Pending
        )
        viewModelScope.launch {
            try {
                publishThenWaitForDeviceAck(COMMAND_ACK_TIMEOUT_MS) {
                    controlRepository.sendCommand(serialNumber, command)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (pendingCommands.complete(command.msgId) != null) {
                    if (_commandFeedback.value.msgId != command.msgId) return@launch
                    _commandFeedback.value = DropperCommandFeedback(
                        serialNumber = serialNumber,
                        msgId = command.msgId,
                        status = DropperCommandFeedbackStatus.Failed,
                        text = "${label}发送失败"
                    )
                    clearFeedbackAfter(command.msgId)
                }
                return@launch
            }

            if (pendingCommands.complete(command.msgId) != null) {
                if (_commandFeedback.value.msgId != command.msgId) return@launch
                _commandFeedback.value = DropperCommandFeedback(
                    serialNumber = serialNumber,
                    msgId = command.msgId,
                    status = DropperCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    private fun isDeviceOnline(serialNumber: String): Boolean =
        devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline == true

    private fun rejectOffline(serialNumber: String, label: String) {
        rejectLocal(serialNumber, "设备离线，无法$label")
    }

    private fun rejectLocal(serialNumber: String, message: String) {
        val msgId = newMsgId("local-rejection")
        _commandFeedback.value = DropperCommandFeedback(
            serialNumber = serialNumber,
            msgId = msgId,
            status = DropperCommandFeedbackStatus.Failed,
            text = message
        )
        clearFeedbackAfter(msgId)
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = DropperCommandFeedback()
            }
        }
    }

    private fun newMsgId(action: String): String = DeviceCommandId.next("dropper", action)

    private fun DropperSixStageCommand.resourceKeys(serialNumber: String): Set<String> = when (this) {
        is DropperSixStageCommand.StageSwitch -> setOf("$serialNumber:stage:$stage")
        is DropperSixStageCommand.AllStages ->
            (1..DROPPER_STAGE_COUNT).mapTo(mutableSetOf()) { stage -> "$serialNumber:stage:$stage" }
        is DropperSixStageCommand.Ping -> setOf("$serialNumber:ping")
        is DropperSixStageCommand.Arm,
        is DropperSixStageCommand.Disarm -> setOf("$serialNumber:safety")
    }

    private fun DropperSixStageCommand.safetyEffect(): DropperSafetyEffect = when (this) {
        is DropperSixStageCommand.Arm -> DropperSafetyEffect.Arm
        is DropperSixStageCommand.Disarm -> DropperSafetyEffect.Disarm
        else -> DropperSafetyEffect.None
    }

    private companion object {
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_000L
    }
}

class DropperSixStageViewModelFactory(
    private val stateRepository: DropperSixStageRepository,
    private val controlRepository: DropperSixStageControlRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DropperSixStageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DropperSixStageViewModel(stateRepository, controlRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class DropperCommandFeedback(
    val serialNumber: String? = null,
    val msgId: String? = null,
    val status: DropperCommandFeedbackStatus = DropperCommandFeedbackStatus.Idle,
    val text: String? = null
)

enum class DropperCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed,
    Timeout
}

internal fun DropperSixStageCommand.requiresOnlineDevice(): Boolean =
    this !is DropperSixStageCommand.Ping

internal fun DropperSixStageCommand.requiresArmedDevice(): Boolean =
    this is DropperSixStageCommand.StageSwitch ||
        (this is DropperSixStageCommand.AllStages && open)
