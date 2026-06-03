package com.tji.device.product.speaker.ui.control

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedback
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedbackStatus
import com.tji.device.product.speaker.viewmodel.SpeakerControlViewModel
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.ui.theme.TjiBackground
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiSurface
import com.tji.device.ui.theme.TjiTextMuted
import com.tji.device.ui.theme.TjiTextPrimary
import com.tji.device.ui.theme.TjiWarning

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TjiBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SpeakerHeaderCard(device = device, state = state, outputGain = outputGain, feedback = feedback)
        }
        item {
            TjiSectionCard(title = "安全控制") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TjiActionButton(
                        text = "立即停止",
                        enabled = viewModel != null,
                        color = TjiError,
                        onClick = { viewModel?.stop(device.serialNumber) },
                        modifier = Modifier.weight(1f)
                    )
                    TjiActionButton(
                        text = "查询状态",
                        enabled = viewModel != null,
                        color = TjiPrimary,
                        onClick = { viewModel?.getStatus(device.serialNumber) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            TjiSectionCard(title = "喊话控制") {
                TalkStatus(talkState)
                TjiActionButton(
                    text = "蜂鸣测试",
                    enabled = viewModel != null && talkState.mode != SpeakerTalkMode.Tone,
                    color = TjiWarning,
                    onClick = {
                        viewModel?.setOutputGain(volumeGain)
                        viewModel?.playToneTest()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                PushToTalkButton(
                    enabled = viewModel != null,
                    hasMicPermission = hasMicPermission,
                    mode = talkState.mode,
                    requestPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPress = { viewModel?.startPushToTalkRecord() },
                    onRelease = { viewModel?.finishPushToTalkRecord() },
                    onCancel = { viewModel?.cancelPushToTalkRecord() }
                )
            }
        }
        item {
            TjiSectionCard(title = "音量") {
                Text(
                    text = "当前输出 ${"%.2f".format(volumeGain)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TjiTextMuted
                )
                Slider(
                    value = volumeGain,
                    onValueChange = { volumeGain = it.coerceIn(0f, 1f) },
                    onValueChangeFinished = { viewModel?.setOutputGain(volumeGain) },
                    valueRange = 0f..1f,
                    steps = 19
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0f to "静音", 0.25f to "轻", 0.5f to "中", 1f to "最大").forEach { (level, label) ->
                        TjiActionButton(
                            text = label,
                            enabled = viewModel != null,
                            color = if (level >= 1f) TjiWarning else TjiPrimary,
                            onClick = {
                                volumeGain = level
                                viewModel?.setOutputGain(level)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            TjiSectionCard(title = "音色调节") {
                ToneSlider(
                    label = "低音",
                    value = bassDb,
                    onValueChange = {
                        bassDb = it
                        viewModel?.setToneSettings(SpeakerToneSettings(bassDb = it, trebleDb = trebleDb))
                    }
                )
                ToneSlider(
                    label = "高音",
                    value = trebleDb,
                    onValueChange = {
                        trebleDb = it
                        viewModel?.setToneSettings(SpeakerToneSettings(bassDb = bassDb, trebleDb = it))
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("原始" to 0f, "清晰" to 3f, "厚重" to -2f).forEach { (label, treble) ->
                        TjiActionButton(
                            text = label,
                            enabled = viewModel != null,
                            color = TjiPrimary,
                            onClick = {
                                val preset = when (label) {
                                    "清晰" -> SpeakerToneSettings(bassDb = 0f, trebleDb = treble)
                                    "厚重" -> SpeakerToneSettings(bassDb = 3f, trebleDb = treble)
                                    else -> SpeakerToneSettings()
                                }
                                bassDb = preset.bassDb
                                trebleDb = preset.trebleDb
                                viewModel?.setToneSettings(preset)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            TjiSectionCard(title = "文字喊话") {
                TtsVoicePresetSelector(
                    selected = ttsVoicePreset,
                    presets = availableTtsVoicePresets,
                    enabled = viewModel != null && talkState.mode != SpeakerTalkMode.Tts,
                    onSelect = { viewModel?.setTtsVoicePreset(it) }
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("TTS 文本") }
                )
                TjiActionButton(
                    text = "文字转语音并播放",
                    enabled = viewModel != null && talkState.mode != SpeakerTalkMode.Tts,
                    color = TjiPrimary,
                    onClick = { viewModel?.speakText(device.serialNumber, text, (volumeGain * 100f).toInt()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ToneSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TjiTextPrimary
        )
        Text(
            text = "${"%.1f".format(value)} dB",
            style = MaterialTheme.typography.bodyMedium,
            color = TjiTextMuted
        )
    }
    Slider(
        value = value,
        onValueChange = { onValueChange(it.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB)) },
        valueRange = SpeakerAudioConfig.Equalizer.MIN_DB..SpeakerAudioConfig.Equalizer.MAX_DB,
        steps = 23
    )
}

@Composable
private fun TtsVoicePresetSelector(
    selected: SpeakerTtsVoicePreset,
    presets: List<SpeakerTtsVoicePreset>,
    enabled: Boolean,
    onSelect: (SpeakerTtsVoicePreset) -> Unit
) {
    Text(
        text = "TTS 音色",
        style = MaterialTheme.typography.bodyMedium,
        color = TjiTextMuted
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        presets.ifEmpty { listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET) }.chunked(3).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPresets.forEach { preset ->
                    TjiActionButton(
                        text = preset.label,
                        enabled = enabled,
                        color = if (preset == selected) TjiWarning else TjiPrimary,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowPresets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpeakerHeaderCard(
    device: BoundAccountDevice,
    state: SpeakerDeviceState?,
    outputGain: Float,
    feedback: SpeakerCommandFeedback
) {
    val online = state?.isOnline == true
    TjiSectionCard(
        title = device.name,
        trailing = {
            TjiStatusText(
                text = if (online) "在线" else "离线",
                color = if (online) TjiOnline else TjiError
            )
        }
    ) {
        Text(
            text = device.serialNumber,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TjiTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TjiMetricTile(label = "状态", value = if (state?.playing == true) "播放中" else "空闲")
            TjiMetricTile(label = "音量", value = "%.2f".format(outputGain))
            TjiMetricTile(label = "网络", value = state?.network ?: "--")
        }
        FeedbackBadge(feedback)
        state?.lastError?.takeIf { it.isNotBlank() }?.let {
            TjiFeedbackBadge(text = it, color = TjiError)
        }
    }
}

@Composable
private fun TalkStatus(state: SpeakerTalkState) {
    val (text, color) = when (state.mode) {
        SpeakerTalkMode.Idle -> (state.error ?: "待命") to if (state.error == null) TjiTextMuted else TjiError
        SpeakerTalkMode.Live -> "实时喊话中 · 已发送 ${state.packetsSent} 包" to TjiPrimary
        SpeakerTalkMode.Recording -> "正在录音，松开发送" to TjiWarning
        SpeakerTalkMode.Sending -> "正在发送录音 · 已发送 ${state.packetsSent} 包" to TjiPrimary
        SpeakerTalkMode.Tts -> "正在发送文字语音 · 已发送 ${state.packetsSent} 包" to TjiPrimary
        SpeakerTalkMode.Tone -> "正在发送蜂鸣控制" to TjiWarning
    }
    TjiFeedbackBadge(text = text, color = color)
}

@Composable
private fun PushToTalkButton(
    enabled: Boolean,
    hasMicPermission: Boolean,
    mode: SpeakerTalkMode,
    requestPermission: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    val active = mode == SpeakerTalkMode.Recording
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(CircleShape)
            .background(if (active) TjiWarning else TjiSurface)
            .pointerInput(enabled, hasMicPermission) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        if (!hasMicPermission) {
                            requestPermission()
                            return@detectTapGestures
                        }
                        var releaseHandled = false
                        onPress()
                        try {
                            val released = tryAwaitRelease()
                            releaseHandled = true
                            if (released) onRelease() else onCancel()
                        } finally {
                            if (!releaseHandled) {
                                onCancel()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (active) "松开发送" else "按住说话",
                style = MaterialTheme.typography.titleLarge,
                color = if (active) TjiSurface else TjiTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasMicPermission) "8 kHz IMA ADPCM · UDP relay" else "需要麦克风权限",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) TjiSurface.copy(alpha = 0.84f) else TjiTextMuted
            )
        }
    }
}

@Composable
private fun FeedbackBadge(feedback: SpeakerCommandFeedback) {
    val color = when (feedback.status) {
        SpeakerCommandFeedbackStatus.Success -> TjiOnline
        SpeakerCommandFeedbackStatus.Failed,
        SpeakerCommandFeedbackStatus.Timeout -> TjiError
        SpeakerCommandFeedbackStatus.Pending -> TjiPrimary
        SpeakerCommandFeedbackStatus.Idle -> TjiTextMuted
    }
    TjiFeedbackBadge(text = feedback.text, color = color)
}

private fun previewSpeakerState(device: BoundAccountDevice) =
    SpeakerDeviceState(
        serialNumber = device.serialNumber,
        name = device.name,
        isOnline = true,
        playing = false,
        volume = DEFAULT_SPEAKER_VOLUME,
        network = "4G"
    )

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SpeakerControlScreenPreview() {
    BucketTheme {
        SpeakerControlScreen(
            device = BoundAccountDevice(
                serialNumber = "SPK-00420042",
                name = "喊话器 01",
                productType = ProductType.Speaker
            )
        )
    }
}
