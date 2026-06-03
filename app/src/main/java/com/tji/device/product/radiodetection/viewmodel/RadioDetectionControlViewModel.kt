package com.tji.device.product.radiodetection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.protocol.RadioRidParser
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionDeviceState
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
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
        val msgId = "rgb-${System.currentTimeMillis()}"
        val command = RadioRgbCommand(
            msgId = msgId,
            mode = mode,
            color = if (color.supportedBy(mode)) color else RadioRgbColor.Red,
            brightness = brightness,
            speed = speed,
            save = save
        )

        _rgbFeedback.value = RadioRgbCommandFeedback(
            msgId = msgId,
            text = if (save) "正在保存默认灯语" else "正在预览灯语",
            pending = true,
            success = null
        )

        controlRepository.sendRgbCommand(
            serialNumber = serialNumber,
            command = command,
            onSuccess = {
                _rgbFeedback.value = RadioRgbCommandFeedback(
                    msgId = msgId,
                    text = "指令已发送，等待设备确认",
                    pending = true,
                    success = null
                )
            },
            onError = { throwable ->
                _rgbFeedback.value = RadioRgbCommandFeedback(
                    msgId = msgId,
                    text = throwable.message ?: "灯语指令发送失败",
                    pending = false,
                    success = false
                )
            }
        )
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
