package com.tji.device.product.radiodetection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.protocol.RadioRidParser
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionDeviceState
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.error.toUserVisibleMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RadioDetectionControlViewModel(
    private val repository: RadioDetectionRepository,
    private val controlRepository: RadioDetectionControlRepository,
    private val replayStore: RadioDetectionReplayStore
) : ViewModel() {
    val devices: StateFlow<List<RadioDetectionDeviceState>> = repository.devices

    private val _rgbFeedback = MutableStateFlow<RadioRgbCommandFeedback?>(null)
    val rgbFeedback: StateFlow<RadioRgbCommandFeedback?> = _rgbFeedback.asStateFlow()
    private val rgbCommandTracker = RadioRgbCommandTracker()

    init {
        viewModelScope.launch {
            while (true) {
                delay(TARGET_PRUNE_INTERVAL_MILLIS)
                repository.pruneExpiredTargets()
            }
        }
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.rgbAck }
                    .forEach { ack ->
                        val feedback = rgbCommandTracker.complete(ack) ?: return@forEach
                        _rgbFeedback.value = feedback
                        clearRgbFeedbackAfter(ack.msgId)
                    }
            }
        }
    }

    fun replayLatestRid(serialNumber: String): Boolean {
        val payload = replayStore.latestPayload(serialNumber) ?: return false
        val packet = RadioRidParser.parse(payload) ?: return false
        viewModelScope.launch {
            repository.upsertRidPacket(serialNumber, packet)
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
        val msgId = DeviceCommandId.next("radio", "rgb")
        if (repository.devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline != true) {
            _rgbFeedback.value = rgbCommandTracker.start(serialNumber, msgId, "设备离线，无法发送灯语指令")
            rgbCommandTracker.fail(msgId, "设备离线，无法发送灯语指令")?.let {
                _rgbFeedback.value = it
            }
            clearRgbFeedbackAfter(msgId)
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

        _rgbFeedback.value = rgbCommandTracker.start(
            serialNumber = serialNumber,
            msgId = msgId,
            text = if (save) "正在保存默认灯语" else "正在预览灯语"
        )

        controlRepository.sendRgbCommand(
            serialNumber = serialNumber,
            command = command,
            onSuccess = {
                viewModelScope.launch {
                    rgbCommandTracker.markPublished(msgId)?.let { _rgbFeedback.value = it }
                }
            },
            onError = { throwable ->
                viewModelScope.launch {
                    val feedback = rgbCommandTracker.fail(
                        msgId,
                        throwable.toUserVisibleMessage("灯语指令发送失败")
                    ) ?: return@launch
                    _rgbFeedback.value = feedback
                    clearRgbFeedbackAfter(msgId)
                }
            }
        )
        viewModelScope.launch {
            delay(RGB_ACK_TIMEOUT_MILLIS)
            val feedback = rgbCommandTracker.timeout(msgId) ?: return@launch
            _rgbFeedback.value = feedback
            clearRgbFeedbackAfter(msgId)
        }
    }

    private fun clearRgbFeedbackAfter(msgId: String) {
        viewModelScope.launch {
            delay(RGB_FEEDBACK_VISIBLE_MILLIS)
            if (rgbCommandTracker.clearIfCurrent(msgId)) {
                _rgbFeedback.value = null
            }
        }
    }

    private companion object {
        const val TARGET_PRUNE_INTERVAL_MILLIS = 5_000L
        const val RGB_ACK_TIMEOUT_MILLIS = 3_000L
        const val RGB_FEEDBACK_VISIBLE_MILLIS = 2_400L
    }
}

class RadioDetectionControlViewModelFactory(
    private val stateRepository: RadioDetectionRepository,
    private val controlRepository: RadioDetectionControlRepository,
    private val replayStore: RadioDetectionReplayStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RadioDetectionControlViewModel::class.java)) {
            return RadioDetectionControlViewModel(stateRepository, controlRepository, replayStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
