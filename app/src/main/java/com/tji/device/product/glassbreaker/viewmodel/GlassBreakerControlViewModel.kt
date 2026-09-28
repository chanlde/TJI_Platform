package com.tji.device.product.glassbreaker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.common.publishThenWaitForDeviceAck
import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import com.tji.device.product.glassbreaker.model.GLASS_BREAKER_CHANNEL_COUNT
import com.tji.device.product.glassbreaker.repository.GlassBreakerControlRepository
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
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

    private val pendingCommands = GlassBreakerPendingCommandTracker()

    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { state -> state.lastAck?.let { state.serialNumber to it } }
                    .forEach { (serialNumber, ack) ->
                        val pending = pendingCommands.complete(ack.msgId, serialNumber) ?: return@forEach
                        if (_commandFeedback.value.msgId != ack.msgId) return@forEach
                        _commandFeedback.value = GlassBreakerCommandFeedback(
                            serialNumber = pending.serialNumber,
                            msgId = ack.msgId,
                            status = if (ack.ok) GlassBreakerCommandFeedbackStatus.Success else GlassBreakerCommandFeedbackStatus.Failed,
                            text = if (ack.ok) {
                                "${pending.label}成功"
                            } else {
                                "${pending.label}失败：${ack.userFacingMessage()}"
                            }
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
        if (channel !in 1..GLASS_BREAKER_CHANNEL_COUNT) {
            rejectLocal(serialNumber, "通道编号无效")
            return
        }
        send(serialNumber, GlassBreakerCommand.SelectChannel(newMsgId("select-$channel"), channel), "选择 ${channel} 通道")
    }

    fun setLaser(serialNumber: String, on: Boolean) {
        send(serialNumber, GlassBreakerCommand.LaserSwitch(newMsgId("laser"), on), if (on) "开启激光" else "关闭激光")
    }

    fun fireSelectedChannel(serialNumber: String) {
        val state = devices.value.firstOrNull { it.serialNumber == serialNumber }
        val channel = state?.selectedChannel
        when {
            state?.isOnline != true -> rejectLocal(serialNumber, "设备离线，不能击发")
            !state.isUnlocked -> rejectLocal(serialNumber, "请先解锁设备")
            channel == null -> rejectLocal(serialNumber, "请先选择通道")
            channel !in 1..GLASS_BREAKER_CHANNEL_COUNT -> rejectLocal(serialNumber, "通道编号无效")
            else -> send(serialNumber, GlassBreakerCommand.FireChannel(newMsgId("fire-$channel"), channel), "击发 ${channel} 通道")
        }
    }

    private fun send(serialNumber: String, command: GlassBreakerCommand, label: String) {
        if (command.requiresOnlineDevice() && !isDeviceOnline(serialNumber)) {
            rejectLocal(serialNumber, "设备离线，无法$label")
            return
        }
        if (!pendingCommands.start(
                msgId = command.msgId,
                serialNumber = serialNumber,
                conflictKey = command.conflictKey(serialNumber),
                label = label
            )
        ) {
            return
        }
        _commandFeedback.value = GlassBreakerCommandFeedback(
            serialNumber = serialNumber,
            msgId = command.msgId,
            status = GlassBreakerCommandFeedbackStatus.Pending,
            text = "${label}中"
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
                    _commandFeedback.value = GlassBreakerCommandFeedback(
                        serialNumber = serialNumber,
                        msgId = command.msgId,
                        status = GlassBreakerCommandFeedbackStatus.Failed,
                        text = "${label}发送失败"
                    )
                    clearFeedbackAfter(command.msgId)
                }
                return@launch
            }

            if (pendingCommands.complete(command.msgId) != null) {
                if (_commandFeedback.value.msgId != command.msgId) return@launch
                _commandFeedback.value = GlassBreakerCommandFeedback(
                    serialNumber = serialNumber,
                    msgId = command.msgId,
                    status = GlassBreakerCommandFeedbackStatus.Timeout,
                    text = "${label}无响应"
                )
                clearFeedbackAfter(command.msgId)
            }
        }
    }

    private fun isDeviceOnline(serialNumber: String): Boolean =
        devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline == true

    private fun rejectLocal(serialNumber: String, message: String) {
        val msgId = newMsgId("local")
        _commandFeedback.value = GlassBreakerCommandFeedback(
            serialNumber = serialNumber,
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

    private fun newMsgId(action: String): String = DeviceCommandId.next("glass", action)

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
    val serialNumber: String? = null,
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

internal fun GlassBreakerCommand.requiresOnlineDevice(): Boolean =
    this !is GlassBreakerCommand.GetDeviceInfo
