package com.tji.device.product.radiodetection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionDeviceState
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import kotlinx.coroutines.flow.StateFlow

class RadioDetectionControlViewModel(
    private val repository: RadioDetectionRepository,
    private val controlRepository: RadioDetectionControlRepository,
    private val replayStore: RadioDetectionReplayStore
) : ViewModel() {
    val devices: StateFlow<List<RadioDetectionDeviceState>> = repository.devices

    private val tasks = RadioDetectionTaskCoordinator(
        scope = viewModelScope,
        repository = repository,
        controlRepository = controlRepository,
        replayStore = replayStore
    )
    val rgbFeedback: StateFlow<RadioRgbCommandFeedback?> = tasks.feedback

    fun bindDevice(serialNumber: String) = tasks.bind(serialNumber)

    fun unbindDevice(serialNumber: String) = tasks.unbind(serialNumber)

    fun replayLatestRid(serialNumber: String): Boolean {
        return tasks.replayLatestRid(serialNumber)
    }

    fun sendRgbCommand(
        serialNumber: String,
        mode: RadioRgbMode,
        color: RadioRgbColor,
        brightness: Int,
        speed: Int?,
        save: Boolean
    ) {
        tasks.sendRgbCommand(
            serialNumber = serialNumber,
            mode = mode,
            color = color,
            brightness = brightness,
            speed = speed,
            save = save
        )
    }

    override fun onCleared() {
        tasks.unbind()
        super.onCleared()
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
