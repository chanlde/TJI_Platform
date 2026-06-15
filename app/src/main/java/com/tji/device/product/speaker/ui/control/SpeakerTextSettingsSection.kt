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
import com.tji.device.product.speaker.audio.SpeakerKokoroTtsSettings
import com.tji.device.product.speaker.audio.SpeakerKokoroVoice
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsEngine
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.audio.customerLabel
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState

@Composable
internal fun SpeakerOutputQualityCard(
    selected: SpeakerAudioQuality,
    enabled: Boolean,
    onSelect: (SpeakerAudioQuality) -> Unit
) {
    SpeakerCard(title = "输出音质") {
        AudioQualitySelector(
            selected = selected,
            enabled = enabled,
            onSelect = onSelect
        )
    }
}

@Composable
internal fun SpeakerToneSettingsCard(
    bassDb: Float,
    trebleDb: Float,
    enabled: Boolean,
    onToneChanged: (SpeakerToneSettings) -> Unit,
    onBassChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit
) {
    SpeakerCard(title = "音色调节") {
        ToneSlider(
            label = "低音",
            value = bassDb,
            onValueChange = {
                onBassChange(it)
                onToneChanged(SpeakerToneSettings(bassDb = it, trebleDb = trebleDb))
            }
        )
        ToneSlider(
            label = "高音",
            value = trebleDb,
            onValueChange = {
                onTrebleChange(it)
                onToneChanged(SpeakerToneSettings(bassDb = bassDb, trebleDb = it))
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("原始" to 0f, "清晰" to 3f, "厚重" to -2f).forEach { (label, treble) ->
                SpeakerActionButton(
                    text = label,
                    enabled = enabled,
                    color = SpeakerAccent,
                    soft = label != "清晰",
                    onClick = {
                        val preset = when (label) {
                            "清晰" -> SpeakerToneSettings(bassDb = 0f, trebleDb = treble)
                            "厚重" -> SpeakerToneSettings(bassDb = 3f, trebleDb = treble)
                            else -> SpeakerToneSettings()
                        }
                        onBassChange(preset.bassDb)
                        onTrebleChange(preset.trebleDb)
                        onToneChanged(preset)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
    ttsEngine: SpeakerTtsEngine,
    kokoroTtsSettings: SpeakerKokoroTtsSettings,
    ttsVoicePreset: SpeakerTtsVoicePreset,
    availableTtsVoicePresets: List<SpeakerTtsVoicePreset>,
    talkState: SpeakerTalkState,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onTtsEngineSelect: (SpeakerTtsEngine) -> Unit,
    onKokoroVoiceSelect: (SpeakerKokoroVoice) -> Unit,
    onKokoroSpeedChange: (Float) -> Unit,
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
        TtsEngineSelector(
            selected = ttsEngine,
            enabled = enabled && talkState.mode != SpeakerTalkMode.Tts,
            onSelect = onTtsEngineSelect
        )
        if (ttsEngine == SpeakerTtsEngine.LocalKokoro) {
            KokoroVoiceSelector(
                selected = kokoroTtsSettings.voice,
                enabled = enabled && talkState.mode != SpeakerTalkMode.Tts,
                onSelect = onKokoroVoiceSelect
            )
            KokoroSpeedSlider(
                value = kokoroTtsSettings.speed,
                onValueChange = onKokoroSpeedChange
            )
        } else {
            TtsVoicePresetSelector(
                selected = ttsVoicePreset,
                presets = availableTtsVoicePresets,
                enabled = enabled && talkState.mode != SpeakerTalkMode.Tts,
                onSelect = onTtsVoicePresetSelect
            )
        }
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
private fun TtsEngineSelector(
    selected: SpeakerTtsEngine,
    enabled: Boolean,
    onSelect: (SpeakerTtsEngine) -> Unit
) {
    Text(
        text = "TTS 引擎",
        style = MaterialTheme.typography.bodyMedium,
        color = SpeakerMuted
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SpeakerTtsEngine.entries.forEach { engine ->
            SpeakerActionButton(
                text = engine.label,
                enabled = enabled,
                color = if (engine == selected) SpeakerWarning else SpeakerAccent,
                soft = engine != selected,
                onClick = { onSelect(engine) },
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
    SpeakerSmoothSlider(
        value = value,
        onValueChange = { onValueChange(it.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB)) },
        valueRange = SpeakerAudioConfig.Equalizer.MIN_DB..SpeakerAudioConfig.Equalizer.MAX_DB,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun KokoroVoiceSelector(
    selected: SpeakerKokoroVoice,
    enabled: Boolean,
    onSelect: (SpeakerKokoroVoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("女声", "男声").forEach { gender ->
            Text(
                text = gender,
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SpeakerKokoroVoice.entries
                    .filter { it.gender == gender }
                    .forEach { voice ->
                        SpeakerActionButton(
                            text = voice.customerLabel(),
                            enabled = enabled,
                            color = if (voice == selected) SpeakerWarning else SpeakerAccent,
                            soft = voice != selected,
                            onClick = { onSelect(voice) },
                            modifier = Modifier.weight(1f)
                        )
                    }
            }
        }
    }
}

@Composable
private fun KokoroSpeedSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "语速",
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerFg
        )
        Text(
            text = "%.2f".format(value),
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerMuted
        )
    }
    SpeakerSmoothSlider(
        value = value,
        onValueChange = {
            onValueChange(
                it.coerceIn(
                    SpeakerAudioConfig.Tts.KOKORO_MIN_SPEED,
                    SpeakerAudioConfig.Tts.KOKORO_MAX_SPEED
                )
            )
        },
        valueRange = SpeakerAudioConfig.Tts.KOKORO_MIN_SPEED..SpeakerAudioConfig.Tts.KOKORO_MAX_SPEED,
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
        text = "TTS 音色",
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
