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
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerKokoroTtsSettings
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedback
import com.tji.device.product.speaker.viewmodel.SpeakerControlViewModel
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.theme.PayloadDimens

@Composable
fun SpeakerControlScreen(
    device: BoundAccountDevice,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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
    val feedback by viewModel?.feedback?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerCommandFeedback()) }
    }
    val talkState by viewModel?.talkState?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerTalkState()) }
    }
    val outputGain by viewModel?.outputGain?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableFloatStateOf(1f) }
    }
    val toneSettings by viewModel?.toneSettings?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerToneSettings()) }
    }
    val ttsVoicePreset by viewModel?.ttsVoicePreset?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET) }
    }
    val availableTtsVoicePresets by viewModel?.availableTtsVoicePresets?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)) }
    }
    val ttsEngine by viewModel?.ttsEngine?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerAudioConfig.Tts.DEFAULT_ENGINE) }
    }
    val outputQuality by viewModel?.outputQuality?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY) }
    }
    val kokoroTtsSettings by viewModel?.kokoroTtsSettings?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SpeakerKokoroTtsSettings()) }
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
    var volumeGain by remember(outputGain) { mutableFloatStateOf(outputGain) }
    var bassDb by remember(toneSettings.bassDb) { mutableFloatStateOf(toneSettings.bassDb) }
    var trebleDb by remember(toneSettings.trebleDb) { mutableFloatStateOf(toneSettings.trebleDb) }
    var text by remember { mutableStateOf("前方危险，请立即撤离") }
    var recordName by remember { mutableStateOf("") }
    var recordQuery by remember { mutableStateOf("") }
    var selectedPanel by remember { mutableStateOf(SpeakerPanel.Talk) }
    val visibleRecords = state?.records.orEmpty().filter {
        recordQuery.isBlank() || it.name.contains(recordQuery, ignoreCase = true)
    }

    LaunchedEffect(selectedPanel, device.serialNumber, viewModel) {
        if (selectedPanel == SpeakerPanel.Records && viewModel != null) {
            viewModel.refreshRecords(device.serialNumber)
            viewModel.refreshStorageStatus(device.serialNumber)
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
                SpeakerHeaderCard(device = device, state = state, outputGain = outputGain, feedback = feedback)
            }
            if (selectedPanel == SpeakerPanel.Talk) item {
                PushToTalkCard(
                    talkState = talkState,
                    enabled = viewModel != null,
                    hasMicPermission = hasMicPermission,
                    requestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPress = { viewModel?.startPushToTalkRecord() },
                    onRelease = { viewModel?.finishPushToTalkRecord(device.serialNumber) },
                    onCancel = { viewModel?.cancelPushToTalkRecord() }
                )
            }
            if (selectedPanel == SpeakerPanel.Talk) item {
                OutputVolumeCard(
                    volumeGain = volumeGain,
                    enabled = viewModel != null,
                    onVolumeGainChange = { volumeGain = it },
                    onVolumeCommitted = { viewModel?.setVolume(device.serialNumber, it) },
                    onStop = { viewModel?.stop(device.serialNumber) },
                    onGetStatus = { viewModel?.getStatus(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                StorageCapacityCard(state?.storageStatus, state)
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                SpeakerRecordSaveCard(
                    recordName = recordName,
                    enabled = viewModel != null,
                    hasMicPermission = hasMicPermission,
                    mode = talkState.mode,
                    onRecordNameChange = { recordName = it },
                    requestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPress = { viewModel?.startPushToTalkSaveRecord(recordName) },
                    onRelease = { viewModel?.finishPushToTalkSaveRecord(device.serialNumber) },
                    onCancel = { viewModel?.cancelPushToTalkRecord() }
                )
            }
            if (selectedPanel == SpeakerPanel.Records) item {
                SpeakerRecordBrowser(
                    recordQuery = recordQuery,
                    visibleRecords = visibleRecords,
                    state = state,
                    enabled = viewModel != null,
                    currentVolume = (volumeGain * 100f).toInt(),
                    onRecordQueryChange = { recordQuery = it },
                    onRefresh = {
                        viewModel?.refreshRecords(device.serialNumber)
                        viewModel?.refreshStorageStatus(device.serialNumber)
                    },
                    onLoadMore = {
                        viewModel?.refreshRecords(
                            serialNumber = device.serialNumber,
                            offset = state?.records.orEmpty().size,
                            limit = 4
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
                    enabled = viewModel != null,
                    onSelect = { viewModel?.setOutputQuality(device.serialNumber, it) }
                )
            }
            if (selectedPanel == SpeakerPanel.Settings) item {
                SpeakerToneSettingsCard(
                    bassDb = bassDb,
                    trebleDb = trebleDb,
                    enabled = viewModel != null,
                    onToneChanged = { viewModel?.setToneSettings(it) },
                    onBassChange = { bassDb = it },
                    onTrebleChange = { trebleDb = it }
                )
            }
            if (selectedPanel == SpeakerPanel.Settings) item {
                SpeakerBuzzerCard(
                    talkState = talkState,
                    enabled = viewModel != null,
                    onPlayBuzzer = { viewModel?.playToneTest(device.serialNumber) }
                )
            }
            if (selectedPanel == SpeakerPanel.Text) item {
                SpeakerTextSpeechCard(
                    text = text,
                    ttsEngine = ttsEngine,
                    kokoroTtsSettings = kokoroTtsSettings,
                    ttsVoicePreset = ttsVoicePreset,
                    availableTtsVoicePresets = availableTtsVoicePresets,
                    talkState = talkState,
                    enabled = viewModel != null,
                    onTextChange = { text = it },
                    onTtsEngineSelect = { viewModel?.setTtsEngine(it) },
                    onKokoroVoiceSelect = { viewModel?.setKokoroVoice(it) },
                    onKokoroSpeedChange = { viewModel?.setKokoroSpeed(it) },
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
