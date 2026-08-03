package com.tji.device.product.speaker.ui.control

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.BuildConfig
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.viewmodel.SpeakerControlViewModel
import com.tji.device.product.speaker.viewmodel.SpeakerMcuMicrophonePhase
import com.tji.device.product.speaker.viewmodel.SpeakerMcuMicrophoneState
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_DEFAULT_CYCLES
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_DEFAULT_HOLD_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_DEFAULT_INTERVAL_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_DEFAULT_SPEED_DPS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_DEFAULT_STEP_ANGLE
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_ANGLE
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_ANGLE
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.theme.PayloadDimens

@Composable
fun SpeakerControlScreen(
    device: BoundAccountDevice,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val viewModel: SpeakerControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.speakerControlViewModelFactory)
    }
    val devices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(emptyList()) }
    }
    val talkState by viewModel?.talkState?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerTalkState()) }
    }
    val mcuMicrophoneState by viewModel?.mcuMicrophoneState?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerMcuMicrophoneState()) }
    }
    val mcuMicrophoneGain by viewModel?.mcuMicrophoneGain?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableFloatStateOf(SpeakerAudioConfig.Gain.MCU_MONITOR_OUTPUT_GAIN) }
    }
    val mcuMicrophoneCaptureActive by
        viewModel?.mcuMicrophoneCaptureActive?.collectAsStateWithLifecycle().let {
            it ?: remember { mutableStateOf(false) }
        }
    val outputGain by viewModel?.outputGain?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableFloatStateOf(1f) }
    }
    val ttsVoicePreset by viewModel?.ttsVoicePreset?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET) }
    }
    val availableTtsVoicePresets by viewModel?.availableTtsVoicePresets?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)) }
    }
    val outputQuality by viewModel?.outputQuality?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY) }
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }
    val state = devices.firstOrNull { it.serialNumber == device.serialNumber }
        ?: if (isPreview) previewSpeakerState(device) else null
    val deviceControlsEnabled = viewModel != null && state?.isOnline == true
    var volumeGain by remember(outputGain) { mutableFloatStateOf(outputGain) }
    var text by remember { mutableStateOf("前方危险，请立即撤离") }
    var recordName by remember { mutableStateOf("") }
    var recordQuery by remember { mutableStateOf("") }
    var recordSortOrder by remember { mutableStateOf(SpeakerRecordSortOrder.NewestFirst) }
    var selectedPanel by remember { mutableStateOf(SpeakerPanel.Talk) }
    var servoAngle by remember { mutableFloatStateOf(90f) }
    var servoAngleEdited by remember { mutableStateOf(false) }
    var servoSpeedDps by remember { mutableFloatStateOf(SPEAKER_SERVO_DEFAULT_SPEED_DPS.toFloat()) }
    var servoMinAngle by remember { mutableFloatStateOf(30f) }
    var servoMaxAngle by remember { mutableFloatStateOf(120f) }
    var servoCycles by remember { mutableFloatStateOf(SPEAKER_SERVO_DEFAULT_CYCLES.toFloat()) }
    var servoHoldMs by remember { mutableFloatStateOf(SPEAKER_SERVO_DEFAULT_HOLD_MS.toFloat()) }
    var servoStepAngle by remember { mutableFloatStateOf(SPEAKER_SERVO_DEFAULT_STEP_ANGLE.toFloat()) }
    var servoIntervalMs by remember { mutableFloatStateOf(SPEAKER_SERVO_DEFAULT_INTERVAL_MS.toFloat()) }
    val records = state?.records.orEmpty()
    val visibleRecords = remember(records, recordQuery, recordSortOrder) {
        records.filter {
            recordQuery.isBlank() || it.name.contains(recordQuery, ignoreCase = true)
        }.sortedForDisplay(recordSortOrder)
    }

    LaunchedEffect(device.serialNumber) {
        servoAngleEdited = false
    }

    LaunchedEffect(
        device.serialNumber,
        state?.servo?.targetAngle,
        state?.servo?.currentAngle,
        state?.servoAngle,
        servoAngleEdited
    ) {
        if (!servoAngleEdited) {
            val reported = state?.servo?.targetAngle ?: state?.servo?.currentAngle ?: state?.servoAngle
            servoAngle = (reported ?: 90).coerceIn(SPEAKER_SERVO_MIN_ANGLE, SPEAKER_SERVO_MAX_ANGLE).toFloat()
        }
    }

    LaunchedEffect(selectedPanel, device.serialNumber, viewModel) {
        if (selectedPanel == SpeakerPanel.Records && viewModel != null) {
            viewModel.refreshRecords(device.serialNumber, order = recordSortOrder.wireName)
            viewModel.refreshStorageStatus(device.serialNumber)
        }
    }
    DisposableEffect(device.serialNumber, viewModel) {
        onDispose {
            viewModel?.cancelPushToTalkRecord(device.serialNumber)
            viewModel?.setMcuMicrophoneListening(device.serialNumber, enabled = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpeakerBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PayloadDimens.ScreenPadding,
                top = 20.dp,
                end = PayloadDimens.ScreenPadding,
                bottom = 220.dp
            ),
            verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
        ) {
            item {
                SpeakerScreenTopBar(panel = selectedPanel, device = device)
            }
            if (selectedPanel == SpeakerPanel.Talk) item {
                PushToTalkCard(
                    talkState = talkState,
                    enabled = deviceControlsEnabled && talkState.mode == SpeakerTalkMode.Idle,
                    hasMicPermission = hasMicPermission,
                    requestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPress = { viewModel?.startPushToTalkRecord(device.serialNumber) },
                    onRelease = { viewModel?.finishPushToTalkRecord() },
                    onCancel = { viewModel?.cancelPushToTalkRecord(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Talk) item {
                McuMicrophoneMonitorCard(
                    state = mcuMicrophoneState,
                    volumeGain = mcuMicrophoneGain,
                    enabled = deviceControlsEnabled,
                    onEnabledChange = {
                        viewModel?.setMcuMicrophoneListening(device.serialNumber, it)
                    },
                    onVolumeGainChange = { viewModel?.setMcuMicrophoneGain(it) }
                )
            }
            if (BuildConfig.DEBUG && selectedPanel == SpeakerPanel.Talk) item {
                McuMicrophoneCaptureCard(
                    isCapturing = mcuMicrophoneCaptureActive,
                    enabled = mcuMicrophoneState.phase == SpeakerMcuMicrophonePhase.Listening,
                    onStart = { viewModel?.startMcuMicrophoneCapture() },
                    onStop = { viewModel?.stopMcuMicrophoneCapture() }
                )
            }
            if (selectedPanel == SpeakerPanel.Talk) item {
                OutputVolumeCard(
                    volumeGain = volumeGain,
                    enabled = deviceControlsEnabled,
                    stopEnabled = viewModel != null,
                    onVolumeGainChange = { volumeGain = it },
                    onVolumeCommitted = { viewModel?.setVolume(device.serialNumber, it) },
                    onStop = { viewModel?.stop(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                StorageCapacityCard(state?.storageStatus, state)
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                SpeakerRecordSaveCard(
                    recordName = recordName,
                    enabled = deviceControlsEnabled && talkState.mode == SpeakerTalkMode.Idle,
                    hasMicPermission = hasMicPermission,
                    mode = talkState.mode,
                    onRecordNameChange = { recordName = it },
                    requestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPress = { viewModel?.startPushToTalkSaveRecord(device.serialNumber, recordName) },
                    onRelease = { viewModel?.finishPushToTalkSaveRecord() },
                    onCancel = { viewModel?.cancelPushToTalkRecord(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                SpeakerRecordBrowser(
                    recordQuery = recordQuery,
                    visibleRecords = visibleRecords,
                    state = state,
                    enabled = deviceControlsEnabled,
                    currentVolume = (volumeGain * 100f).toInt(),
                    sortOrderLabel = recordSortOrder.label,
                    onRecordQueryChange = { recordQuery = it },
                    onSortOrderChange = {
                        val nextOrder = recordSortOrder.toggle()
                        recordSortOrder = nextOrder
                        viewModel?.refreshRecords(device.serialNumber, order = nextOrder.wireName)
                    },
                    onRefresh = {
                        viewModel?.refreshRecords(device.serialNumber, order = recordSortOrder.wireName)
                        viewModel?.refreshStorageStatus(device.serialNumber)
                    },
                    onLoadMore = {
                        viewModel?.refreshRecords(
                            serialNumber = device.serialNumber,
                            offset = state?.recordNextOffset ?: 0,
                            limit = 4,
                            order = recordSortOrder.wireName
                        )
                    },
                    onPlay = { viewModel?.playRecord(device.serialNumber, it, (volumeGain * 100f).toInt()) },
                    onRename = { recordId, name -> viewModel?.updateRecordName(device.serialNumber, recordId, name) },
                    onDelete = { viewModel?.deleteRecord(device.serialNumber, it) }
                )
            }
            if (selectedPanel == SpeakerPanel.Settings) item {
                SpeakerOutputQualityCard(
                    selected = outputQuality,
                    enabled = deviceControlsEnabled,
                    onSelect = { viewModel?.setOutputQuality(it) }
                )
            }
            if (selectedPanel == SpeakerPanel.Settings) item {
                SpeakerServoAngleCard(
                    angle = servoAngle,
                    speedDps = servoSpeedDps,
                    minAngle = servoMinAngle,
                    maxAngle = servoMaxAngle,
                    cycles = servoCycles,
                    holdMs = servoHoldMs,
                    stepAngle = servoStepAngle,
                    intervalMs = servoIntervalMs,
                    reportedAngle = state?.servoAngle,
                    servoState = state?.servo,
                    enabled = deviceControlsEnabled,
                    onAngleChange = {
                        servoAngleEdited = true
                        servoAngle = it
                    },
                    onSpeedChange = { servoSpeedDps = it },
                    onMinAngleChange = { servoMinAngle = it },
                    onMaxAngleChange = { servoMaxAngle = it },
                    onCyclesChange = { servoCycles = it },
                    onHoldMsChange = { servoHoldMs = it },
                    onStepAngleChange = { servoStepAngle = it },
                    onIntervalMsChange = { servoIntervalMs = it },
                    onApply = {
                        viewModel?.setServoAngle(
                            serialNumber = device.serialNumber,
                            angle = servoAngle.toInt(),
                            speedDps = servoSpeedDps.toInt()
                        )
                    },
                    onTest = {
                        viewModel?.testServo(
                            serialNumber = device.serialNumber,
                            speedDps = servoSpeedDps.toInt()
                        )
                    },
                    onSweepTest = {
                        viewModel?.sweepServo(
                            serialNumber = device.serialNumber,
                            minAngle = servoMinAngle.toInt(),
                            maxAngle = servoMaxAngle.toInt(),
                            speedDps = servoSpeedDps.toInt(),
                            cycles = servoCycles.toInt(),
                            durationMs = servoHoldMs.toInt()
                        )
                    },
                    onStepTest = {
                        viewModel?.stepServo(
                            serialNumber = device.serialNumber,
                            minAngle = servoMinAngle.toInt(),
                            maxAngle = servoMaxAngle.toInt(),
                            stepAngle = servoStepAngle.toInt(),
                            speedDps = servoSpeedDps.toInt(),
                            intervalMs = servoIntervalMs.toInt()
                        )
                    }
                )
            }
            if (selectedPanel == SpeakerPanel.Settings) item {
                SpeakerBuzzerCard(
                    talkState = talkState,
                    enabled = deviceControlsEnabled,
                    onPlayBuzzer = { viewModel?.playToneTest(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Text) item {
                SpeakerTextSpeechCard(
                    text = text,
                    ttsVoicePreset = ttsVoicePreset,
                    availableTtsVoicePresets = availableTtsVoicePresets,
                    talkState = talkState,
                    enabled = deviceControlsEnabled,
                    onTextChange = { text = it },
                    onTtsVoicePresetSelect = { viewModel?.setTtsVoicePreset(it) },
                    onSpeak = { viewModel?.speakText(device.serialNumber, text, (volumeGain * 100f).toInt()) }
                )
            }
        }
        SpeakerBottomNavigation(
            selected = selectedPanel,
            onSelect = { selectedPanel = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private enum class SpeakerRecordSortOrder(
    val wireName: String,
    val label: String
) {
    NewestFirst("desc", "最新优先"),
    OldestFirst("asc", "最早优先");

    fun toggle(): SpeakerRecordSortOrder =
        if (this == NewestFirst) OldestFirst else NewestFirst
}

private fun List<SpeakerRecord>.sortedForDisplay(order: SpeakerRecordSortOrder): List<SpeakerRecord> =
    when (order) {
        SpeakerRecordSortOrder.NewestFirst -> sortedWith(
            compareByDescending<SpeakerRecord> { it.sortTimestamp() }
                .thenByDescending { it.createdAt.orEmpty() }
        )
        SpeakerRecordSortOrder.OldestFirst -> sortedWith(
            compareBy<SpeakerRecord> { it.sortTimestamp() }
                .thenBy { it.createdAt.orEmpty() }
        )
    }

private fun SpeakerRecord.sortTimestamp(): Long =
    createdMs ?: recordId.substringAfterLast('_').toLongOrNull() ?: 0L
