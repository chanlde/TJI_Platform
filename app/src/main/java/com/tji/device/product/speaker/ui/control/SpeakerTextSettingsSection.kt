package com.tji.device.product.speaker.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerLimiterProtection
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTonePreset
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.components.TjiControlSlider
import kotlin.math.roundToInt

@Composable
internal fun SpeakerOutputQualityCard(
    selected: SpeakerAudioQuality,
    enabled: Boolean,
    onSelect: (SpeakerAudioQuality) -> Unit
) {
    SpeakerCard(title = "录音与语音音质") {
        AudioQualitySelector(
            selected = selected,
            enabled = enabled,
            onSelect = onSelect
        )
        Text(
            text = "当前 ${selected.sampleRate / 1_000} kHz；录音采集、处理和文件输出保持一致",
            style = MaterialTheme.typography.bodySmall,
            color = SpeakerMuted
        )
    }
}

@Composable
internal fun SpeakerToneSettingsCard(
    toneSettings: SpeakerToneSettings,
    enabled: Boolean,
    onToneChanged: (SpeakerToneSettings) -> Unit
) {
    val settings = toneSettings.normalized()
    SpeakerCard(title = "音效调节") {
        Text(
            text = "音效模式",
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerMuted
        )
        SpeakerTonePreset.entries.chunked(4).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPresets.forEach { preset ->
                    SpeakerActionButton(
                        text = preset.label,
                        enabled = enabled,
                        color = if (settings.preset == preset) SpeakerWarning else SpeakerAccent,
                        soft = settings.preset != preset,
                        onClick = { onToneChanged(SpeakerToneSettings.fromPreset(preset)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowPresets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        PercentToneSlider(
            label = "人声清晰",
            value = settings.clarity,
            onValueChange = { onToneChanged(settings.copy(clarity = it).asCustom()) }
        )
        PercentToneSlider(
            label = "降噪",
            value = settings.noiseReduction,
            onValueChange = { onToneChanged(settings.copy(noiseReduction = it).asCustom()) }
        )
        PercentToneSlider(
            label = "响度增强",
            value = settings.loudness,
            onValueChange = { onToneChanged(settings.copy(loudness = it).asCustom()) }
        )
        PercentToneSlider(
            label = "低频削减",
            value = settings.lowCut,
            onValueChange = { onToneChanged(settings.copy(lowCut = it).asCustom()) }
        )
        ToneSlider(
            label = "低音",
            value = settings.bassDb,
            onValueChange = {
                onToneChanged(settings.copy(bassDb = it).asCustom())
            }
        )
        ToneSlider(
            label = "高音",
            value = settings.trebleDb,
            onValueChange = {
                onToneChanged(settings.copy(trebleDb = it).asCustom())
            }
        )
        Text(
            text = "防破音保护",
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SpeakerLimiterProtection.entries.forEach { protection ->
                SpeakerActionButton(
                    text = protection.label,
                    enabled = enabled,
                    color = if (settings.protection == protection) SpeakerWarning else SpeakerAccent,
                    soft = settings.protection != protection,
                    onClick = { onToneChanged(settings.copy(protection = protection).asCustom()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PercentToneSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerFg
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerMuted
        )
    }
    TjiControlSlider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
        valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun SpeakerBuzzerCard(
    talkState: SpeakerTalkState,
    enabled: Boolean,
    onPlayBuzzer: () -> Unit
) {
    SpeakerCard(title = "蜂鸣器") {
        TalkStatus(talkState)
        SpeakerActionButton(
            text = "播放蜂鸣",
            enabled = enabled && talkState.mode != SpeakerTalkMode.Tone,
            color = SpeakerWarning,
            onClick = onPlayBuzzer,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SpeakerTextSpeechCard(
    text: String,
    ttsVoicePreset: SpeakerTtsVoicePreset,
    availableTtsVoicePresets: List<SpeakerTtsVoicePreset>,
    talkState: SpeakerTalkState,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onTtsVoicePresetSelect: (SpeakerTtsVoicePreset) -> Unit,
    onSpeak: () -> Unit
) {
    SpeakerCard(title = "文字喊话") {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            label = { Text("喊话内容") }
        )
        TtsVoicePresetSelector(
            selected = ttsVoicePreset,
            presets = availableTtsVoicePresets,
            enabled = enabled && talkState.mode != SpeakerTalkMode.Tts,
            onSelect = onTtsVoicePresetSelect
        )
        TalkStatus(talkState)
        SpeakerActionButton(
            text = "播放",
            enabled = enabled && talkState.mode != SpeakerTalkMode.Tts,
            color = SpeakerAccent,
            onClick = onSpeak,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AudioQualitySelector(
    selected: SpeakerAudioQuality,
    enabled: Boolean,
    onSelect: (SpeakerAudioQuality) -> Unit
) {
    Text(
        text = "音质",
        style = MaterialTheme.typography.bodyMedium,
        color = SpeakerMuted
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SpeakerAudioQuality.entries.forEach { quality ->
            SpeakerActionButton(
                text = quality.label,
                enabled = enabled,
                color = if (quality == selected) SpeakerWarning else SpeakerAccent,
                soft = quality != selected,
                onClick = { onSelect(quality) },
                modifier = Modifier.weight(1f)
            )
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
            color = SpeakerFg
        )
        Text(
            text = "${"%.1f".format(value)} dB",
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerMuted
        )
    }
    TjiControlSlider(
        value = value,
        onValueChange = { onValueChange(it.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB)) },
        valueRange = SpeakerAudioConfig.Equalizer.MIN_DB..SpeakerAudioConfig.Equalizer.MAX_DB,
        modifier = Modifier.fillMaxWidth()
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
        text = "语音音色",
        style = MaterialTheme.typography.bodyMedium,
        color = SpeakerMuted
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        presets.ifEmpty { listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET) }.chunked(3).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPresets.forEach { preset ->
                    SpeakerActionButton(
                        text = preset.label,
                        enabled = enabled,
                        color = if (preset == selected) SpeakerWarning else SpeakerAccent,
                        soft = preset != selected,
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
