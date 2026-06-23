package com.tji.device.product.solarclean.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SolarCleanControlViewModel(
    private val stateRepository: SolarCleanRepository,
    private val controlRepository: SolarCleanControlRepository
) : ViewModel() {
    val devices: StateFlow<List<SolarCleanDeviceState>> = stateRepository.devices
    val controlSettings = stateRepository.controlSettings

    private val _commandFeedback = MutableStateFlow(SolarCleanCommandFeedback())
    val commandFeedback: StateFlow<SolarCleanCommandFeedback> = _commandFeedback.asStateFlow()

    private val pendingCommands = mutableMapOf<String, PendingCommandFeedback>()
    private val deviceInfoRequestedAfterReboot = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach { ack ->
                        val pending = pendingCommands.remove(ack.msgId) ?: return@forEach
                        if (_commandFeedback.value.msgId == ack.msgId) {
                            val feedback = if (ack.ok) {
                                SolarCleanCommandFeedback(
                                    msgId = ack.msgId,
                                    status = SolarCleanCommandFeedbackStatus.Success,
                                    text = pending.successText
                                )
                            } else {
                                SolarCleanCommandFeedback(
                                    msgId = ack.msgId,
                                    status = SolarCleanCommandFeedbackStatus.Failed,
                                    text = pending.failedText
                                )
                            }
                            _commandFeedback.value = feedback
                            clearFeedbackAfter(feedback.msgId)
                        }
                    }
                states.forEach { state ->
                    maybeRefreshDeviceInfoAfterOtaReboot(state)
                }
            }
        }
    }

    fun ping(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.Ping(newMsgId("ping")), "连通测试")
    }

    fun setPump(serialNumber: String, on: Boolean) {
        stateRepository.updateControlSettings(serialNumber) { it.copy(pumpOn = on) }
        send(serialNumber, SolarCleanCommand.PumpSwitch(newMsgId("pump"), on), "水泵设置")
    }

    fun setPumpPressure(serialNumber: String, percent: Double) {
        stateRepository.updateControlSettings(serialNumber) { it.copy(pumpPressurePercent = percent) }
        send(serialNumber, SolarCleanCommand.PumpPressure(newMsgId("pressure"), percent), "水泵压力设置")
    }

    fun setSprayAngle(serialNumber: String, angleDeg: Double) {
        stateRepository.updateControlSettings(serialNumber) { it.copy(sprayAngleDegrees = angleDeg) }
        send(serialNumber, SolarCleanCommand.SprayAngle(newMsgId("angle"), angleDeg), "喷洒角度设置")
    }

    fun setServoSwing(serialNumber: String, on: Boolean) {
        stateRepository.updateControlSettings(serialNumber) { it.copy(swingOn = on) }
        send(serialNumber, SolarCleanCommand.ServoSwing(newMsgId("swing"), on), "摆动设置")
    }

    fun setSwingSpeed(serialNumber: String, speedPercent: Double) {
        stateRepository.updateControlSettings(serialNumber) { it.copy(swingSpeedPercent = speedPercent) }
        send(serialNumber, SolarCleanCommand.SwingSpeed(newMsgId("swing-speed"), speedPercent), "摆动速度设置")
    }

    fun requestDeviceInfo(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.GetDeviceInfo(newMsgId("device-info")), "设备信息")
    }

    fun requestRouteList(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.RouteList(newMsgId("routes")), "航线列表")
    }

    fun executeSlot(serialNumber: String, slot: Int) {
        send(serialNumber, SolarCleanCommand.ExecuteSlot(newMsgId("execute"), slot), "执行航线")
    }

    fun deleteSlot(serialNumber: String, slot: Int) {
        send(serialNumber, SolarCleanCommand.RouteDelete(newMsgId("delete"), slot), "删除航线")
    }

    fun cancelDownload(serialNumber: String, slot: Int? = null) {
        send(serialNumber, SolarCleanCommand.RouteDownloadCancel(newMsgId("cancel"), slot), "取消下载")
    }

    private fun send(
        serialNumber: String,
        command: SolarCleanCommand,
        label: String,
        pendingText: String = "${label}处理中",
        successText: String = "${label}成功",
        failedText: String = "${label}失败",
        timeoutText: String = "${label}无响应",
        ackTimeoutMs: Long = COMMAND_ACK_TIMEOUT_MS
    ) {
        pendingCommands[command.msgId] = PendingCommandFeedback(
            successText = successText,
            failedText = failedText,
            timeoutText = timeoutText
        )
        _commandFeedback.value = SolarCleanCommandFeedback(
            msgId = command.msgId,
            status = SolarCleanCommandFeedbackStatus.Pending,
            text = pendingText
        )
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
        }
        viewModelScope.launch {
            delay(ackTimeoutMs)
            val pending = pendingCommands.remove(command.msgId)
            if (pending != null) {
                if (_commandFeedback.value.msgId == command.msgId) {
                    _commandFeedback.value = SolarCleanCommandFeedback(
                        msgId = command.msgId,
                        status = SolarCleanCommandFeedbackStatus.Timeout,
                        text = pending.timeoutText
                    )
                    clearFeedbackAfter(command.msgId)
                }
            }
        }
    }

    private fun maybeRefreshDeviceInfoAfterOtaReboot(state: SolarCleanDeviceState) {
        val status = state.otaStatus?.status?.normalizedOtaStatus() ?: return
        val waitingForReboot = status == "READY_TO_REBOOT" ||
                status == "PENDING_REBOOT" ||
                status == "REBOOTING"
        if (!waitingForReboot || !state.isOnline) return
        if (!deviceInfoRequestedAfterReboot.add(state.serialNumber)) return

        viewModelScope.launch {
            delay(POST_REBOOT_DEVICE_INFO_DELAY_MS)
            controlRepository.sendCommand(
                state.serialNumber,
                SolarCleanCommand.GetDeviceInfo(newMsgId("device-info-after-ota"))
            )
        }
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = SolarCleanCommandFeedback()
            }
        }
    }

    private fun newMsgId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

    private companion object {
        const val TAG = "SolarCleanControlVM"
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_000L
        const val POST_REBOOT_DEVICE_INFO_DELAY_MS = 1_500L
    }
}

class SolarCleanControlViewModelFactory(
    private val stateRepository: SolarCleanRepository,
    private val controlRepository: SolarCleanControlRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SolarCleanControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SolarCleanControlViewModel(
                stateRepository = stateRepository,
                controlRepository = controlRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class SolarCleanCommandFeedback(
    val msgId: String? = null,
    val status: SolarCleanCommandFeedbackStatus = SolarCleanCommandFeedbackStatus.Idle,
    val text: String? = null
)

private data class PendingCommandFeedback(
    val successText: String,
    val failedText: String,
    val timeoutText: String
)

enum class SolarCleanCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed,
    Timeout
}

private fun String.normalizedOtaStatus(): String {
    return trim()
        .uppercase()
        .removePrefix("OTA_")
}
