package com.tji.device.product.droppersixstage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepository
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import kotlinx.coroutines.delay
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

    private val pendingCommands = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach { ack ->
                        val label = pendingCommands.remove(ack.msgId) ?: return@forEach
                        val feedback = DropperCommandFeedback(
                            msgId = ack.msgId,
                            status = if (ack.ok) DropperCommandFeedbackStatus.Success else DropperCommandFeedbackStatus.Failed,
                            text = if (ack.ok) "${label}成功" else "${label}失败"
                        )
                        _commandFeedback.value = feedback
                        clearFeedbackAfter(ack.msgId)
                    }
            }
        }
    }

    fun toggleStage(serialNumber: String, stage: Int, open: Boolean) {
        send(
            serialNumber = serialNumber,
            command = DropperSixStageCommand.StageSwitch(newMsgId("stage-$stage"), stage, open),
            label = "${stage}段抛投"
        )
    }

    fun toggleAll(serialNumber: String, open: Boolean) {
        send(
            serialNumber = serialNumber,
            command = DropperSixStageCommand.AllStages(newMsgId("all"), open),
            label = if (open) "全部抛投" else "全部复位"
        )
    }

    fun ping(serialNumber: String) {
        send(serialNumber, DropperSixStageCommand.Ping(newMsgId("ping")), "连通测试")
    }

    private fun send(serialNumber: String, command: DropperSixStageCommand, label: String) {
        pendingCommands[command.msgId] = label
        _commandFeedback.value = DropperCommandFeedback(
            msgId = command.msgId,
            status = DropperCommandFeedbackStatus.Pending
        )
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
        }
        viewModelScope.launch {
            delay(COMMAND_ACK_TIMEOUT_MS)
            if (pendingCommands.remove(command.msgId) != null) {
                _commandFeedback.value = DropperCommandFeedback(
                    msgId = command.msgId,
                    status = DropperCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = DropperCommandFeedback()
            }
        }
    }

    private fun newMsgId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

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
