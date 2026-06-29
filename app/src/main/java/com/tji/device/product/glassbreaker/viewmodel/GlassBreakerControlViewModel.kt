package com.tji.device.product.glassbreaker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import com.tji.device.product.glassbreaker.repository.GlassBreakerControlRepository
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlassBreakerControlViewModel(
    private val stateRepository: GlassBreakerRepository,
    private val controlRepository: GlassBreakerControlRepository
) : ViewModel() {
    val devices: StateFlow<List<GlassBreakerState>> = stateRepository.devices

    private val _commandFeedback = MutableStateFlow(GlassBreakerCommandFeedback())
    val commandFeedback: StateFlow<GlassBreakerCommandFeedback> = _commandFeedback.asStateFlow()

    private val pendingCommands = mutableMapOf<String, String>()
    private val pendingCommandKeys = mutableMapOf<String, String>()
    private val activeCommandKeys = mutableSetOf<String>()
    private var lastHandledAckId: String? = null

    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach { ack ->
                        if (ack.msgId == lastHandledAckId) return@forEach
                        val label = pendingCommands.remove(ack.msgId) ?: return@forEach
                        clearPendingCommandKey(ack.msgId)
                        lastHandledAckId = ack.msgId
                        _commandFeedback.value = GlassBreakerCommandFeedback(
                            msgId = ack.msgId,
                            status = if (ack.ok) GlassBreakerCommandFeedbackStatus.Success else GlassBreakerCommandFeedbackStatus.Failed,
                            text = if (ack.ok) "${label}成功" else "${label}失败：${ack.userFacingMessage()}"
                        )
                        clearFeedbackAfter(ack.msgId)
                    }
            }
        }
    }

    fun requestDeviceInfo(serialNumber: String) {
        send(serialNumber, GlassBreakerCommand.GetDeviceInfo(newMsgId("info")), "查询设备")
    }

    fun unlock(serialNumber: String) {
        send(serialNumber, GlassBreakerCommand.Unlock(newMsgId("unlock")), "解锁")
    }

    fun lock(serialNumber: String) {
        send(serialNumber, GlassBreakerCommand.Lock(newMsgId("lock")), "上锁")
    }

    fun selectChannel(serialNumber: String, channel: Int) {
        send(serialNumber, GlassBreakerCommand.SelectChannel(newMsgId("select-$channel"), channel), "选择 ${channel} 通道")
    }

    fun setLaser(serialNumber: String, on: Boolean) {
        send(serialNumber, GlassBreakerCommand.LaserSwitch(newMsgId("laser"), on), if (on) "开启激光" else "关闭激光")
    }

    fun fireSelectedChannel(serialNumber: String, state: GlassBreakerState?) {
        val channel = state?.selectedChannel
        when {
            state?.isOnline != true -> rejectLocal("设备离线，不能击发")
            !state.isUnlocked -> rejectLocal("请先解锁设备")
            channel == null -> rejectLocal("请先选择通道")
            else -> send(serialNumber, GlassBreakerCommand.FireChannel(newMsgId("fire-$channel"), channel), "击发 ${channel} 通道")
        }
    }

    private fun send(serialNumber: String, command: GlassBreakerCommand, label: String) {
        val commandKey = command.pendingKey(serialNumber)
        if (!activeCommandKeys.add(commandKey)) {
            return
        }
        pendingCommands[command.msgId] = label
        pendingCommandKeys[command.msgId] = commandKey
        _commandFeedback.value = GlassBreakerCommandFeedback(
            msgId = command.msgId,
            status = GlassBreakerCommandFeedbackStatus.Pending,
            text = "${label}中"
        )
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
        }
        viewModelScope.launch {
            delay(COMMAND_ACK_TIMEOUT_MS)
            if (pendingCommands.remove(command.msgId) != null) {
                clearPendingCommandKey(command.msgId)
                _commandFeedback.value = GlassBreakerCommandFeedback(
                    msgId = command.msgId,
                    status = GlassBreakerCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    private fun clearPendingCommandKey(msgId: String) {
        pendingCommandKeys.remove(msgId)?.let { activeCommandKeys.remove(it) }
    }

    private fun rejectLocal(message: String) {
        val msgId = newMsgId("local")
        _commandFeedback.value = GlassBreakerCommandFeedback(
            msgId = msgId,
            status = GlassBreakerCommandFeedbackStatus.Failed,
            text = message
        )
        clearFeedbackAfter(msgId)
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = GlassBreakerCommandFeedback()
            }
        }
    }

    private fun newMsgId(prefix: String): String = "gb-$prefix-${System.currentTimeMillis()}"

    private fun GlassBreakerCommand.pendingKey(serialNumber: String): String = when (this) {
        is GlassBreakerCommand.GetDeviceInfo -> "$serialNumber:info"
        is GlassBreakerCommand.Unlock -> "$serialNumber:unlock"
        is GlassBreakerCommand.Lock -> "$serialNumber:lock"
        is GlassBreakerCommand.SelectChannel -> "$serialNumber:select:$channel"
        is GlassBreakerCommand.FireChannel -> "$serialNumber:fire:$channel"
        is GlassBreakerCommand.LaserSwitch -> "$serialNumber:laser:$on"
    }

    private companion object {
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_400L
    }
}

class GlassBreakerControlViewModelFactory(
    private val stateRepository: GlassBreakerRepository,
    private val controlRepository: GlassBreakerControlRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GlassBreakerControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GlassBreakerControlViewModel(stateRepository, controlRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class GlassBreakerCommandFeedback(
    val msgId: String? = null,
    val status: GlassBreakerCommandFeedbackStatus = GlassBreakerCommandFeedbackStatus.Idle,
    val text: String? = null
)

enum class GlassBreakerCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed,
    Timeout
}
