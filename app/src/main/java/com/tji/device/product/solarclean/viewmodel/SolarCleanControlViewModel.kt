package com.tji.device.product.solarclean.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.common.publishThenWaitForDeviceAck
import com.tji.device.product.ota.OtaRebootRefreshGate
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.model.SolarCleanControlLimits
import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
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
    private val rebootRefreshGate = OtaRebootRefreshGate()
    private val controlSettingsTracker = SolarControlSettingsTracker()

    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach { ack ->
                        val pending = pendingCommands.remove(ack.msgId) ?: return@forEach
                        resolveControlSetting(ack.msgId, successful = ack.ok)
                        if (_commandFeedback.value.msgId == ack.msgId) {
                            val feedback = if (ack.ok) {
                                SolarCleanCommandFeedback(
                                    serialNumber = pending.serialNumber,
                                    msgId = ack.msgId,
                                    status = SolarCleanCommandFeedbackStatus.Success,
                                    text = pending.successText
                                )
                            } else {
                                SolarCleanCommandFeedback(
                                    serialNumber = pending.serialNumber,
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
        val command = SolarCleanCommand.PumpSwitch(newMsgId("pump"), on)
        applyOptimisticControl(
            serialNumber,
            command.msgId,
            SolarControlField.Pump,
            SolarControlValue.Toggle(on)
        )
        send(serialNumber, command, "水泵设置")
    }

    fun setPumpPressure(serialNumber: String, percent: Double) {
        val normalizedPercent = SolarCleanControlLimits.normalizePressure(percent)
            ?: return rejectInvalidParameter(serialNumber, "水泵压力")
        val command = SolarCleanCommand.PumpPressure(newMsgId("pressure"), normalizedPercent)
        applyOptimisticControl(
            serialNumber,
            command.msgId,
            SolarControlField.PumpPressure,
            SolarControlValue.Level(normalizedPercent)
        )
        send(serialNumber, command, "水泵压力设置")
    }

    fun setSprayAngle(serialNumber: String, angleDeg: Double) {
        val normalizedAngle = SolarCleanControlLimits.normalizeSprayAngle(angleDeg)
            ?: return rejectInvalidParameter(serialNumber, "喷洒角度")
        val command = SolarCleanCommand.SprayAngle(newMsgId("angle"), normalizedAngle)
        applyOptimisticControl(
            serialNumber,
            command.msgId,
            SolarControlField.SprayAngle,
            SolarControlValue.Level(normalizedAngle)
        )
        send(serialNumber, command, "喷洒角度设置")
    }

    fun setServoSwing(serialNumber: String, on: Boolean) {
        val command = SolarCleanCommand.ServoSwing(newMsgId("swing"), on)
        applyOptimisticControl(
            serialNumber,
            command.msgId,
            SolarControlField.Swing,
            SolarControlValue.Toggle(on)
        )
        send(serialNumber, command, "摆动设置")
    }

    fun setSwingSpeed(serialNumber: String, speedPercent: Double) {
        val normalizedSpeed = SolarCleanControlLimits.normalizeSwingSpeed(speedPercent)
            ?: return rejectInvalidParameter(serialNumber, "摆动速度")
        val command = SolarCleanCommand.SwingSpeed(newMsgId("swing-speed"), normalizedSpeed)
        applyOptimisticControl(
            serialNumber,
            command.msgId,
            SolarControlField.SwingSpeed,
            SolarControlValue.Level(normalizedSpeed)
        )
        send(serialNumber, command, "摆动速度设置")
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
        if (command.requiresOnlineDevice() && !isDeviceOnline(serialNumber)) {
            resolveControlSetting(command.msgId, successful = false)
            rejectOffline(serialNumber, command.msgId, label)
            return
        }
        pendingCommands[command.msgId] = PendingCommandFeedback(
            serialNumber = serialNumber,
            successText = successText,
            failedText = failedText,
            timeoutText = timeoutText
        )
        _commandFeedback.value = SolarCleanCommandFeedback(
            serialNumber = serialNumber,
            msgId = command.msgId,
            status = SolarCleanCommandFeedbackStatus.Pending,
            text = pendingText
        )
        viewModelScope.launch {
            try {
                publishThenWaitForDeviceAck(ackTimeoutMs) {
                    controlRepository.sendCommand(serialNumber, command)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                val pending = pendingCommands.remove(command.msgId) ?: return@launch
                resolveControlSetting(command.msgId, successful = false)
                if (_commandFeedback.value.msgId == command.msgId) {
                    _commandFeedback.value = SolarCleanCommandFeedback(
                        serialNumber = serialNumber,
                        msgId = command.msgId,
                        status = SolarCleanCommandFeedbackStatus.Failed,
                        text = pending.failedText
                    )
                    clearFeedbackAfter(command.msgId)
                }
                return@launch
            }

            val pending = pendingCommands.remove(command.msgId)
            if (pending != null) {
                resolveControlSetting(command.msgId, successful = false)
                if (_commandFeedback.value.msgId == command.msgId) {
                    _commandFeedback.value = SolarCleanCommandFeedback(
                        serialNumber = serialNumber,
                        msgId = command.msgId,
                        status = SolarCleanCommandFeedbackStatus.Timeout,
                        text = pending.timeoutText
                    )
                    clearFeedbackAfter(command.msgId)
                }
            }
        }
    }

    private fun isDeviceOnline(serialNumber: String): Boolean =
        devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline == true

    private fun rejectOffline(serialNumber: String, msgId: String, label: String) {
        _commandFeedback.value = SolarCleanCommandFeedback(
            serialNumber = serialNumber,
            msgId = msgId,
            status = SolarCleanCommandFeedbackStatus.Failed,
            text = "设备离线，无法$label"
        )
        clearFeedbackAfter(msgId)
    }

    private fun rejectInvalidParameter(serialNumber: String, label: String) {
        val msgId = newMsgId("invalid-parameter")
        _commandFeedback.value = SolarCleanCommandFeedback(
            serialNumber = serialNumber,
            msgId = msgId,
            status = SolarCleanCommandFeedbackStatus.Failed,
            text = "${label}参数无效"
        )
        clearFeedbackAfter(msgId)
    }

    private fun maybeRefreshDeviceInfoAfterOtaReboot(state: SolarCleanDeviceState) {
        val status = state.otaStatus?.status?.normalizedOtaStatus() ?: return
        val waitingForReboot = status == "READY_TO_REBOOT" ||
                status == "PENDING_REBOOT" ||
                status == "REBOOTING"
        if (!rebootRefreshGate.shouldRefresh(state.serialNumber, waitingForReboot, state.isOnline)) return

        viewModelScope.launch {
            runPostRebootDeviceInfoRefresh(
                maxAttempts = POST_REBOOT_DEVICE_INFO_MAX_ATTEMPTS,
                waitBeforeFirstAttempt = { delay(POST_REBOOT_DEVICE_INFO_DELAY_MS) },
                waitBeforeRetry = { delay(POST_REBOOT_DEVICE_INFO_RETRY_DELAY_MS) },
                sendRefresh = { attempt ->
                    controlRepository.sendCommand(
                        state.serialNumber,
                        SolarCleanCommand.GetDeviceInfo(
                            newMsgId("device-info-after-ota-$attempt")
                        )
                    )
                }
            ).onFailure { throwable ->
                Log.e(
                    TAG,
                    "OTA 重启后刷新设备信息失败: serial=${state.serialNumber}",
                    throwable
                )
            }
        }
    }

    private fun applyOptimisticControl(
        serialNumber: String,
        msgId: String,
        field: SolarControlField,
        value: SolarControlValue
    ) {
        val current = stateRepository.controlSettings.value[serialNumber]
            ?: SolarCleanControlSettings()
        val next = controlSettingsTracker.begin(serialNumber, msgId, field, value, current)
        stateRepository.updateControlSettings(serialNumber) { next }
    }

    private fun resolveControlSetting(msgId: String, successful: Boolean) {
        val serialNumber = controlSettingsTracker.serialNumberFor(msgId) ?: return
        val current = stateRepository.controlSettings.value[serialNumber]
            ?: SolarCleanControlSettings()
        val resolution = controlSettingsTracker.resolve(msgId, successful, current) ?: return
        stateRepository.updateControlSettings(resolution.serialNumber) { resolution.settings }
    }

    private fun clearFeedbackAfter(msgId: String?) {
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            if (_commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = SolarCleanCommandFeedback()
            }
        }
    }

    private fun newMsgId(action: String): String = DeviceCommandId.next("solar", action)

    private companion object {
        const val TAG = "SolarCleanControlVM"
        const val COMMAND_ACK_TIMEOUT_MS = 3_000L
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_000L
        const val POST_REBOOT_DEVICE_INFO_DELAY_MS = 1_500L
        const val POST_REBOOT_DEVICE_INFO_RETRY_DELAY_MS = 1_000L
        const val POST_REBOOT_DEVICE_INFO_MAX_ATTEMPTS = 2
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
    val serialNumber: String? = null,
    val msgId: String? = null,
    val status: SolarCleanCommandFeedbackStatus = SolarCleanCommandFeedbackStatus.Idle,
    val text: String? = null
)

private data class PendingCommandFeedback(
    val serialNumber: String,
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

internal fun SolarCleanCommand.requiresOnlineDevice(): Boolean =
    this !is SolarCleanCommand.Ping && this !is SolarCleanCommand.GetDeviceInfo
