package com.tji.device.product.speaker.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerAudioTransport
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerTtsEngine
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeakerControlViewModel(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioTransport,
    private val ttsSynthesizer: SpeakerTtsEngine,
    feedbackReceiver: SpeakerFeedbackReceiver
) : ViewModel() {
    val devices: StateFlow<List<SpeakerDeviceState>> = stateRepository.devices

    private val _feedback = MutableStateFlow(SpeakerCommandFeedback())
    val feedback: StateFlow<SpeakerCommandFeedback> = _feedback.asStateFlow()
    private val commandCoordinator = SpeakerCommandCoordinator(
        scope = viewModelScope,
        repository = controlRepository,
        feedback = _feedback,
        requireOnline = ::requireOnlineDevice,
        clearFeedbackAfter = ::clearFeedbackAfter
    )
    private val deviceActions = SpeakerDeviceActions(commandCoordinator) { message ->
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
    }
    private val mcuMicrophoneController = SpeakerMcuMicrophoneController(
        scope = viewModelScope,
        receiver = feedbackReceiver,
        sendCommand = controlRepository::sendCommand,
        sendCommandAndAwaitAck = commandCoordinator::sendAndAwaitAck
    )
    val mcuMicrophoneState: StateFlow<SpeakerMcuMicrophoneState> =
        mcuMicrophoneController.state

    private val _talkState = MutableStateFlow(SpeakerTalkState())
    val talkState: StateFlow<SpeakerTalkState> = _talkState.asStateFlow()
    private val recordSaveCoordinator = SpeakerRecordSaveCoordinator(
        scope = viewModelScope,
        stateRepository = stateRepository,
        controlRepository = controlRepository,
        audioRelay = audioRelay,
        commands = commandCoordinator,
        deviceActions = deviceActions,
        devices = devices,
        feedback = _feedback,
        talkState = _talkState,
        clearFeedbackAfter = ::clearFeedbackAfter
    )

    private val _outputGain = MutableStateFlow(SpeakerAudioConfig.Gain.DEFAULT_OUTPUT_GAIN)
    val outputGain: StateFlow<Float> = _outputGain.asStateFlow()
    private val _mcuMicrophoneGain =
        MutableStateFlow(SpeakerAudioConfig.Gain.MCU_MONITOR_OUTPUT_GAIN)
    val mcuMicrophoneGain: StateFlow<Float> = _mcuMicrophoneGain.asStateFlow()
    private val _mcuMicrophoneCaptureActive = MutableStateFlow(false)
    val mcuMicrophoneCaptureActive: StateFlow<Boolean> =
        _mcuMicrophoneCaptureActive.asStateFlow()

    private val ttsCoordinator = SpeakerTtsCoordinator(viewModelScope, ttsSynthesizer)
    val ttsVoicePreset: StateFlow<SpeakerTtsVoicePreset> = ttsCoordinator.voicePreset
    val availableTtsVoicePresets: StateFlow<List<SpeakerTtsVoicePreset>> =
        ttsCoordinator.availableVoicePresets

    private val _outputQuality = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY)
    val outputQuality: StateFlow<SpeakerAudioQuality> = _outputQuality.asStateFlow()

    private val generatedAudioCoordinator = SpeakerGeneratedAudioCoordinator(
        scope = viewModelScope,
        tts = ttsCoordinator,
        recordTransfer = recordSaveCoordinator,
        devices = devices,
        outputQuality = outputQuality,
        outputGain = outputGain,
        talkState = _talkState,
        feedback = _feedback,
        requireOnline = ::requireOnlineDevice,
        clearFeedbackAfter = ::clearFeedbackAfter
    )
    private var activeDeviceSerialNumber: String? = null
    private val pushToTalkCoordinator = SpeakerPushToTalkCoordinator(
        scope = viewModelScope,
        audioTransport = audioRelay,
        mcuMicrophoneController = mcuMicrophoneController,
        devices = devices,
        outputQuality = outputQuality,
        talkState = _talkState,
        requireOnline = ::requireOnlineDevice,
        cancelPlayback = ::cancelPlaybackOperation,
        transferRecord = recordSaveCoordinator::transferAndAwaitResult
    )
    init {
        viewModelScope.launch {
            devices.collect { states ->
                states.asSequence()
                    .mapNotNull { it.lastAck }
                    .forEach(commandCoordinator::handleAck)
            }
        }
        viewModelScope.launch {
            stateRepository.recordEvents.collect { envelope ->
                recordSaveCoordinator.handleEvent(envelope.serialNumber, envelope.event)
            }
        }
        viewModelScope.launch {
            mcuMicrophoneState.collect { state ->
                if (state.phase != SpeakerMcuMicrophonePhase.Listening) {
                    _mcuMicrophoneCaptureActive.value = false
                }
            }
        }
    }

    fun bindDevice(serialNumber: String) {
        if (activeDeviceSerialNumber == serialNumber) return
        activeDeviceSerialNumber?.let(::unbindDevice)
        activeDeviceSerialNumber = serialNumber
    }

    fun unbindDevice(serialNumber: String) {
        if (activeDeviceSerialNumber != serialNumber) return
        activeDeviceSerialNumber = null
        generatedAudioCoordinator.stop()
        pushToTalkCoordinator.unbind()
        mcuMicrophoneController.stop()
        _mcuMicrophoneCaptureActive.value = false
        _talkState.value = SpeakerTalkState()
        _feedback.value = SpeakerCommandFeedback()
    }

    // 基础控制
    fun stop(serialNumber: String) {
        stopAllAudioOperations()
        send(serialNumber, SpeakerCommand.Stop(newMsgId("stop")), "停止")
    }

    fun setVolume(serialNumber: String, volume: Int) {
        val normalizedVolume = volume.coerceIn(0, 100)
        _outputGain.value = percentToOutputGain(normalizedVolume)
        deviceActions.setVolume(serialNumber, normalizedVolume)
    }

    fun setTtsVoicePreset(preset: SpeakerTtsVoicePreset) {
        ttsCoordinator.selectVoice(preset)
    }

    fun setOutputQuality(quality: SpeakerAudioQuality) {
        _outputQuality.value = quality
    }

    /**
     * 开关设备板载 PDM 麦克风监听；不会停止 MCU 扬声器。
     */
    fun setMcuMicrophoneListening(serialNumber: String, enabled: Boolean) {
        if (enabled) {
            if (!requireOnlineDevice(serialNumber, "设备麦克风监听")) return
            mcuMicrophoneController.start(serialNumber)
        } else {
            stopMcuMicrophoneCapture()
            mcuMicrophoneController.stop()
        }
    }

    fun setMcuMicrophoneGain(gain: Float) {
        val normalized = gain.coerceIn(0f, 1f)
        _mcuMicrophoneGain.value = normalized
        mcuMicrophoneController.setPlaybackGain(normalized)
    }

    fun startMcuMicrophoneCapture() {
        _mcuMicrophoneCaptureActive.value =
            mcuMicrophoneController.startDebugCapture()
    }

    fun stopMcuMicrophoneCapture() {
        mcuMicrophoneController.stopDebugCapture()
        _mcuMicrophoneCaptureActive.value = false
    }

    fun setServoAngle(serialNumber: String, angle: Int, speedDps: Int = SPEAKER_SERVO_DEFAULT_SPEED_DPS) {
        deviceActions.setServoAngle(serialNumber, angle, speedDps)
    }

    fun getStatus(serialNumber: String) {
        deviceActions.getStatus(serialNumber)
    }

    fun refreshRecords(
        serialNumber: String,
        offset: Int = 0,
        limit: Int = RECORD_LIST_PAGE_SIZE,
        order: String = "desc"
    ) {
        deviceActions.refreshRecords(serialNumber, offset, limit, order)
    }

    fun refreshStorageStatus(serialNumber: String) {
        deviceActions.refreshStorageStatus(serialNumber)
    }

    fun playRecord(serialNumber: String, recordId: String, volumePercent: Int) {
        deviceActions.playRecord(serialNumber, recordId, volumePercent)
    }

    fun deleteRecord(serialNumber: String, recordId: String) {
        deviceActions.deleteRecord(serialNumber, recordId)
    }

    fun updateRecordName(serialNumber: String, recordId: String, name: String) {
        deviceActions.updateRecordName(serialNumber, recordId, name)
    }

    // TTS 合成、手机预览与文件播放
    fun speakText(serialNumber: String, text: String, volume: Int = DEFAULT_SPEAKER_VOLUME) {
        generatedAudioCoordinator.speakText(serialNumber, text, volume)
    }

    fun previewTtsOnPhone(text: String) {
        generatedAudioCoordinator.previewOnPhone(text)
    }

    fun playToneTest(serialNumber: String) {
        generatedAudioCoordinator.playTone(serialNumber)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    // 按住说话与录音保存
    fun startPushToTalkRecord(serialNumber: String) {
        pushToTalkCoordinator.startDirect(serialNumber)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startPushToTalkSaveRecord(serialNumber: String, defaultName: String? = null) {
        pushToTalkCoordinator.startSave(serialNumber, defaultName)
    }

    fun finishPushToTalkRecord() {
        pushToTalkCoordinator.finishDirect()
    }

    fun finishPushToTalkSaveRecord() {
        pushToTalkCoordinator.finishSave()
    }

    fun cancelPushToTalkRecord(serialNumber: String? = null) {
        pushToTalkCoordinator.cancel(serialNumber)
    }

    private fun stopAllAudioOperations() {
        cancelPlaybackOperation()
        pushToTalkCoordinator.stop()
    }

    private fun cancelPlaybackOperation() {
        generatedAudioCoordinator.stop()
    }

    private fun send(
        serialNumber: String,
        command: SpeakerCommand,
        label: String,
        awaitAck: Boolean = true
    ) = commandCoordinator.send(serialNumber, command, label, awaitAck)

    private fun requireOnlineDevice(serialNumber: String, action: String): Boolean {
        if (devices.value.firstOrNull { it.serialNumber == serialNumber }?.isOnline == true) {
            return true
        }
        val message = "设备离线，无法$action"
        _feedback.value = SpeakerCommandFeedback(
            status = SpeakerCommandFeedbackStatus.Failed,
            text = message
        )
        _talkState.value = SpeakerTalkState(
            mode = SpeakerTalkMode.Idle,
            error = message
        )
        clearFeedbackAfter(null)
        return false
    }

    private fun clearFeedbackAfter(msgId: String?) {
        val expectedFeedback = _feedback.value
        viewModelScope.launch {
            delay(COMMAND_FEEDBACK_VISIBLE_MS)
            // Local operations have no MQTT msgId. Object identity also prevents an
            // older command's timer from clearing feedback produced by newer work.
            if (_feedback.value === expectedFeedback && expectedFeedback.msgId == msgId) {
                _feedback.value = SpeakerCommandFeedback()
            }
        }
    }

    private fun newMsgId(action: String): String = DeviceCommandId.next("speaker", action)

    override fun onCleared() {
        activeDeviceSerialNumber = null
        generatedAudioCoordinator.stop()
        pushToTalkCoordinator.unbind()
        mcuMicrophoneController.stop()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_FEEDBACK_VISIBLE_MS = 2_500L
        const val RECORD_LIST_PAGE_SIZE = 4
        fun percentToOutputGain(volume: Int): Float =
            (volume.coerceIn(0, 100) / 100f) * SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN
    }
}
