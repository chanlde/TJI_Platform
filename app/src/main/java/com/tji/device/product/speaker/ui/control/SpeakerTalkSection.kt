package com.tji.device.product.speaker.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedback
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedbackStatus
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import kotlin.math.abs

@Composable
internal fun SpeakerHeaderCard(
    device: BoundAccountDevice,
    state: SpeakerDeviceState?,
    outputGain: Float,
    feedback: SpeakerCommandFeedback
) {
    val online = state?.isOnline == true
    SpeakerCard(title = "设备状态") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpeakerAccent.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = device.serialNumber.takeLast(3).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = SpeakerAccent,
                    fontWeight = FontWeight.Black
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SpeakerFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.serialNumber,
                    style = MaterialTheme.typography.labelMedium,
                    color = SpeakerMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SpeakerStatusBadge(
                text = if (online) "在线" else "离线",
                color = if (online) SpeakerSuccess else SpeakerDanger
            )
        }
        SpeakerSoftRow(
            label = "当前状态",
            value = if (state?.playing == true) "播放中" else "空闲",
            valueColor = if (state?.playing == true) SpeakerAccent else SpeakerFg
        )
        SpeakerSoftRow(
            label = "输出音量",
            value = "${(outputGain * 100f).toInt()}%"
        )
        FeedbackBadge(feedback)
        state?.lastError?.takeIf { it.isNotBlank() }?.let {
            SpeakerStatusBadge(text = "设备返回异常", color = SpeakerDanger)
        }
    }
}

@Composable
internal fun PushToTalkCard(
    talkState: SpeakerTalkState,
    enabled: Boolean,
    hasMicPermission: Boolean,
    requestPermission: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    SpeakerCard(title = "按住喊话") {
        TalkStatus(talkState)
        PushToTalkButton(
            enabled = enabled,
            hasMicPermission = hasMicPermission,
            mode = talkState.mode,
            idleLabel = "按住说话",
            activeLabel = "正在喊话",
            footer = if (hasMicPermission) "松开结束" else "需要麦克风权限",
            requestPermission = requestPermission,
            onPress = onPress,
            onRelease = onRelease,
            onCancel = onCancel
        )
    }
}

@Composable
internal fun OutputVolumeCard(
    volumeGain: Float,
    enabled: Boolean,
    onVolumeGainChange: (Float) -> Unit,
    onVolumeCommitted: (Int) -> Unit,
    onStop: () -> Unit,
    onGetStatus: () -> Unit
) {
    SpeakerCard(title = "输出音量") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "当前输出",
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerMuted
            )
            Text(
                text = "${(volumeGain * 100f).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = SpeakerFg,
                fontWeight = FontWeight.Bold
            )
        }
        SpeakerSmoothSlider(
            value = volumeGain,
            onValueChange = { onVolumeGainChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = {
                onVolumeCommitted((volumeGain * 100f).toInt())
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0f to "静音", 0.25f to "轻", 0.55f to "中", 1f to "最大").forEach { (level, label) ->
                SpeakerActionButton(
                    text = label,
                    enabled = enabled,
                    color = if (abs(volumeGain - level) < 0.03f) SpeakerWarning else SpeakerAccent,
                    soft = abs(volumeGain - level) >= 0.03f,
                    onClick = {
                        onVolumeGainChange(level)
                        onVolumeCommitted((level * 100f).toInt())
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SpeakerActionButton(
                text = "立即停止",
                enabled = enabled,
                color = SpeakerDanger,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
            SpeakerActionButton(
                text = "查询状态",
                enabled = enabled,
                color = SpeakerAccent,
                soft = true,
                onClick = onGetStatus,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun TalkStatus(state: SpeakerTalkState) {
    val text = speakerTalkStatusText(state)
    val color = when (state.mode) {
        SpeakerTalkMode.Idle -> if (state.error == null) SpeakerMuted else TjiError
        SpeakerTalkMode.Live,
        SpeakerTalkMode.Sending,
        SpeakerTalkMode.SavingRecord,
        SpeakerTalkMode.Tts -> SpeakerAccent
        SpeakerTalkMode.Recording,
        SpeakerTalkMode.RecordingToStore,
        SpeakerTalkMode.Tone -> SpeakerWarning
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SpeakerStatusBadge(text = text, color = color, showDot = state.mode != SpeakerTalkMode.Idle)
        if (state.mode == SpeakerTalkMode.SavingRecord) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = SpeakerAccent,
                trackColor = SpeakerBorder
            )
        }
    }
}

internal fun speakerTalkStatusText(state: SpeakerTalkState): String =
    when (state.mode) {
        SpeakerTalkMode.Idle -> state.error ?: "待命"
        SpeakerTalkMode.Live -> "正在实时喊话"
        SpeakerTalkMode.Recording -> "正在录音，松开发送"
        SpeakerTalkMode.Sending -> "正在发送到扬声器"
        SpeakerTalkMode.RecordingToStore -> "正在录音，松开保存"
        SpeakerTalkMode.SavingRecord -> "正在保存录音 ${(state.progress.coerceIn(0f, 1f) * 100).toInt()}%"
        SpeakerTalkMode.Tts -> "文字语音发送中"
        SpeakerTalkMode.Tone -> "正在播放蜂鸣"
    }

@Composable
internal fun PushToTalkButton(
    enabled: Boolean,
    hasMicPermission: Boolean,
    mode: SpeakerTalkMode,
    idleLabel: String,
    activeLabel: String,
    footer: String,
    compact: Boolean = false,
    requestPermission: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    val active = mode == SpeakerTalkMode.Recording || mode == SpeakerTalkMode.RecordingToStore
    val buttonSize = if (compact) 112.dp else 184.dp
    val stageHeight = if (compact) 176.dp else 275.dp
    val scale by animateFloatAsState(
        targetValue = if (active) 0.92f else 1f,
        label = "speakerTalkButtonScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(stageHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(SpeakerBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (active) SpeakerDanger else SpeakerAccent)
                .border(1.dp, if (active) SpeakerDanger else SpeakerAccent, CircleShape)
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
            Text(
                text = if (active) activeLabel else idleLabel,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = SpeakerSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SpeakerWaveMeter(active = active)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = footer,
            style = MaterialTheme.typography.labelMedium,
            color = SpeakerMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeedbackBadge(feedback: SpeakerCommandFeedback) {
    val color = when (feedback.status) {
        SpeakerCommandFeedbackStatus.Success -> TjiOnline
        SpeakerCommandFeedbackStatus.Failed,
        SpeakerCommandFeedbackStatus.Timeout -> TjiError
        SpeakerCommandFeedbackStatus.Pending -> SpeakerAccent
        SpeakerCommandFeedbackStatus.Idle -> SpeakerMuted
    }
    feedback.text?.takeIf { it.isNotBlank() }?.let {
        SpeakerStatusBadge(text = it, color = color, showDot = feedback.status != SpeakerCommandFeedbackStatus.Idle)
    }
}
