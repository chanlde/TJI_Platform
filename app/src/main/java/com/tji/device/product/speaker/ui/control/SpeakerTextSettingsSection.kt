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
import androidx.compose.ui.unit.dp
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerAudioQuality
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState

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
