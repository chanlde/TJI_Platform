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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tji.device.product.speaker.model.SpeakerServoState
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedback
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedbackStatus
import com.tji.device.product.speaker.viewmodel.SpeakerMcuMicrophonePhase
import com.tji.device.product.speaker.viewmodel.SpeakerMcuMicrophoneState
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_CYCLES
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_HOLD_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_INTERVAL_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_ANGLE
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MAX_SPEED_DPS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_CYCLES
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_HOLD_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_INTERVAL_MS
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_ANGLE
import com.tji.device.product.speaker.viewmodel.SPEAKER_SERVO_MIN_SPEED_DPS
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.components.TjiControlSlider
import com.tji.device.ui.components.TjiMiniSwitch
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import kotlin.math.abs
import kotlin.math.roundToInt

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
        state?.audio?.let { audio ->
            SpeakerSoftRow(
                label = "输出峰值",
                value = "${((audio.peakQ15.coerceAtLeast(0) / 32767f) * 100f).roundToInt().coerceIn(0, 100)}%"
            )
            SpeakerSoftRow(
                label = "平均电平",
                value = "${((audio.rmsQ15.coerceAtLeast(0) / 32767f) * 100f).roundToInt().coerceIn(0, 100)}%"
            )
            SpeakerSoftRow(
                label = "播放缓存",
                value = "${audio.bufferedBytes} / ${audio.bufferedMaxBytes}",
                valueColor = if (audio.bufferedBytes == 0 && audio.outUnderruns > 0L) SpeakerWarning else SpeakerFg
            )
            SpeakerSoftRow(
                label = "防破音",
                value = audio.limiterCount.toString(),
                valueColor = if (audio.limiterCount > 0L) SpeakerWarning else SpeakerFg
            )
            SpeakerSoftRow(
                label = "播放断点",
                value = audio.outUnderruns.toString(),
                valueColor = if (audio.outUnderruns > 0L) SpeakerWarning else SpeakerFg
            )
            SpeakerSoftRow(
                label = "输出异常",
                value = (audio.saiErrors + audio.saiOvr + audio.dmaErrors + audio.fillLate).toString(),
                valueColor = if (audio.saiErrors + audio.saiOvr + audio.dmaErrors + audio.fillLate > 0L) SpeakerDanger else SpeakerFg
            )
            SpeakerSoftRow(
                label = "平稳输出",
                value = "${audio.dmaHalf}/${audio.dmaFull}",
                valueColor = if (audio.dmaHalf == 0L && state.playing) SpeakerWarning else SpeakerFg
            )
        }
        FeedbackBadge(feedback)
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
            idleLabel = "按住录音",
            activeLabel = "录音中",
            footer = if (hasMicPermission) "松手后发送到设备播放" else "需要麦克风权限",
            requestPermission = requestPermission,
            onPress = onPress,
            onRelease = onRelease,
            onCancel = onCancel
        )
    }
}

@Composable
internal fun McuMicrophoneMonitorCard(
    state: SpeakerMcuMicrophoneState,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SpeakerCard(title = "设备麦克风监听") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (state.phase) {
                        SpeakerMcuMicrophonePhase.Idle -> "未监听"
                        SpeakerMcuMicrophonePhase.Connecting -> "正在连接设备"
                        SpeakerMcuMicrophonePhase.Listening -> "正在监听现场声音"
                        SpeakerMcuMicrophonePhase.Stopping -> "正在停止"
                        SpeakerMcuMicrophonePhase.Failed -> "监听失败"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.phase == SpeakerMcuMicrophonePhase.Failed) {
                        SpeakerDanger
                    } else {
                        SpeakerFg
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "声音来自 MCU 板载 PDM 麦克风，不会调用手机麦克风",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpeakerMuted
                )
            }
            TjiMiniSwitch(
                checked = state.enabled,
                enabled = enabled && state.phase != SpeakerMcuMicrophonePhase.Stopping,
                onCheckedChange = onEnabledChange
            )
        }
        if (state.phase == SpeakerMcuMicrophonePhase.Listening) {
            val impairedPackets = state.packetsConcealed + state.packetsRejected
            SpeakerSoftRow(
                label = "监听质量",
                value = if (impairedPackets == 0L) "良好" else "网络有波动",
                valueColor = if (impairedPackets == 0L) SpeakerSuccess else SpeakerWarning
            )
        }
        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerDanger
            )
        }
    }
}

@Composable
internal fun OutputVolumeCard(
    volumeGain: Float,
    enabled: Boolean,
    stopEnabled: Boolean = enabled,
    onVolumeGainChange: (Float) -> Unit,
    onVolumeCommitted: (Int) -> Unit,
    onStop: () -> Unit
) {
    var sliderVolumeGain by remember { mutableFloatStateOf(volumeGain.coerceIn(0f, 1f)) }
    LaunchedEffect(volumeGain) {
        sliderVolumeGain = volumeGain.coerceIn(0f, 1f)
    }
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
                text = "${(sliderVolumeGain * 100f).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = SpeakerFg,
                fontWeight = FontWeight.Bold
            )
        }
        TjiControlSlider(
            value = sliderVolumeGain,
            onValueChange = {
                val next = it.coerceIn(0f, 1f)
                sliderVolumeGain = next
                onVolumeGainChange(next)
            },
            onValueChangeFinished = {
                onVolumeCommitted((sliderVolumeGain * 100f).roundToInt())
            },
            valueRange = 0f..1f,
            enabled = enabled,
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
                enabled = stopEnabled,
                color = SpeakerDanger,
                onClick = onStop,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun SpeakerServoAngleCard(
    angle: Float,
    speedDps: Float,
    minAngle: Float,
    maxAngle: Float,
    cycles: Float,
    holdMs: Float,
    stepAngle: Float,
    intervalMs: Float,
    reportedAngle: Int?,
    servoState: SpeakerServoState?,
    enabled: Boolean,
    onAngleChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMinAngleChange: (Float) -> Unit,
    onMaxAngleChange: (Float) -> Unit,
    onCyclesChange: (Float) -> Unit,
    onHoldMsChange: (Float) -> Unit,
    onStepAngleChange: (Float) -> Unit,
    onIntervalMsChange: (Float) -> Unit,
    onApply: () -> Unit,
    onTest: () -> Unit,
    onSweepTest: () -> Unit,
    onStepTest: () -> Unit
) {
    var sliderAngle by remember { mutableFloatStateOf(angle.coerceIn(SPEAKER_SERVO_MIN_ANGLE.toFloat(), SPEAKER_SERVO_MAX_ANGLE.toFloat())) }
    var sliderSpeed by remember { mutableFloatStateOf(speedDps.coerceIn(SPEAKER_SERVO_MIN_SPEED_DPS.toFloat(), SPEAKER_SERVO_MAX_SPEED_DPS.toFloat())) }
    LaunchedEffect(angle) {
        sliderAngle = angle.coerceIn(SPEAKER_SERVO_MIN_ANGLE.toFloat(), SPEAKER_SERVO_MAX_ANGLE.toFloat())
    }
    LaunchedEffect(speedDps) {
        sliderSpeed = speedDps.coerceIn(SPEAKER_SERVO_MIN_SPEED_DPS.toFloat(), SPEAKER_SERVO_MAX_SPEED_DPS.toFloat())
    }
    SpeakerCard(title = "舵机设置") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "目标角度",
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerMuted
            )
            Text(
                text = "${sliderAngle.roundToInt()}°",
                style = MaterialTheme.typography.titleMedium,
                color = SpeakerFg,
                fontWeight = FontWeight.Bold
            )
        }
        TjiControlSlider(
            value = sliderAngle,
            onValueChange = {
                val next = it.coerceIn(SPEAKER_SERVO_MIN_ANGLE.toFloat(), SPEAKER_SERVO_MAX_ANGLE.toFloat())
                sliderAngle = next
                onAngleChange(next)
            },
            valueRange = SPEAKER_SERVO_MIN_ANGLE.toFloat()..SPEAKER_SERVO_MAX_ANGLE.toFloat(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0 to "0°", 90 to "90°", 180 to "180°").forEach { (preset, label) ->
                SpeakerActionButton(
                    text = label,
                    enabled = enabled,
                    color = if (sliderAngle.roundToInt() == preset) SpeakerWarning else SpeakerAccent,
                    soft = sliderAngle.roundToInt() != preset,
                    onClick = {
                        sliderAngle = preset.toFloat()
                        onAngleChange(sliderAngle)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "转动速度",
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerMuted
            )
            Text(
                text = "${sliderSpeed.roundToInt()}°/s",
                style = MaterialTheme.typography.titleMedium,
                color = SpeakerFg,
                fontWeight = FontWeight.Bold
            )
        }
        TjiControlSlider(
            value = sliderSpeed,
            onValueChange = {
                val next = it.coerceIn(SPEAKER_SERVO_MIN_SPEED_DPS.toFloat(), SPEAKER_SERVO_MAX_SPEED_DPS.toFloat())
                sliderSpeed = next
                onSpeedChange(next)
            },
            valueRange = SPEAKER_SERVO_MIN_SPEED_DPS.toFloat()..SPEAKER_SERVO_MAX_SPEED_DPS.toFloat(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(30 to "慢", 60 to "中", 120 to "快").forEach { (preset, label) ->
                SpeakerActionButton(
                    text = label,
                    enabled = enabled,
                    color = if (sliderSpeed.roundToInt() == preset) SpeakerWarning else SpeakerAccent,
                    soft = sliderSpeed.roundToInt() != preset,
                    onClick = {
                        sliderSpeed = preset.toFloat()
                        onSpeedChange(sliderSpeed)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SpeakerActionButton(
                text = "应用设置",
                enabled = enabled,
                color = SpeakerAccent,
                onClick = onApply,
                modifier = Modifier.weight(1f)
            )
            SpeakerActionButton(
                text = "测试舵机",
                enabled = enabled,
                color = SpeakerWarning,
                onClick = onTest,
                modifier = Modifier.weight(1f)
            )
        }
        SpeakerServoRangeControls(
            minAngle = minAngle,
            maxAngle = maxAngle,
            cycles = cycles,
            holdMs = holdMs,
            stepAngle = stepAngle,
            intervalMs = intervalMs,
            enabled = enabled,
            onMinAngleChange = onMinAngleChange,
            onMaxAngleChange = onMaxAngleChange,
            onCyclesChange = onCyclesChange,
            onHoldMsChange = onHoldMsChange,
            onStepAngleChange = onStepAngleChange,
            onIntervalMsChange = onIntervalMsChange
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SpeakerActionButton(
                text = "往返测试",
                enabled = enabled,
                color = SpeakerWarning,
                soft = true,
                onClick = onSweepTest,
                modifier = Modifier.weight(1f)
            )
            SpeakerActionButton(
                text = "分段测试",
                enabled = enabled,
                color = SpeakerWarning,
                soft = true,
                onClick = onStepTest,
                modifier = Modifier.weight(1f)
            )
        }
        SpeakerSoftRow(
            label = "设备上报",
            value = servoStatusText(reportedAngle, servoState)
        )
    }
}

@Composable
private fun SpeakerServoRangeControls(
    minAngle: Float,
    maxAngle: Float,
    cycles: Float,
    holdMs: Float,
    stepAngle: Float,
    intervalMs: Float,
    enabled: Boolean,
    onMinAngleChange: (Float) -> Unit,
    onMaxAngleChange: (Float) -> Unit,
    onCyclesChange: (Float) -> Unit,
    onHoldMsChange: (Float) -> Unit,
    onStepAngleChange: (Float) -> Unit,
    onIntervalMsChange: (Float) -> Unit
) {
    SpeakerServoMiniSlider("最小角度", "${minAngle.roundToInt()}°", minAngle, SPEAKER_SERVO_MIN_ANGLE.toFloat()..SPEAKER_SERVO_MAX_ANGLE.toFloat(), enabled, onMinAngleChange)
    SpeakerServoMiniSlider("最大角度", "${maxAngle.roundToInt()}°", maxAngle, SPEAKER_SERVO_MIN_ANGLE.toFloat()..SPEAKER_SERVO_MAX_ANGLE.toFloat(), enabled, onMaxAngleChange)
    SpeakerServoMiniSlider("往返次数", if (cycles.roundToInt() == 0) "连续" else "${cycles.roundToInt()}次", cycles, SPEAKER_SERVO_MIN_CYCLES.toFloat()..SPEAKER_SERVO_MAX_CYCLES.toFloat(), enabled, onCyclesChange)
    SpeakerServoMiniSlider("端点停留", "${holdMs.roundToInt()}ms", holdMs, SPEAKER_SERVO_MIN_HOLD_MS.toFloat()..SPEAKER_SERVO_MAX_HOLD_MS.toFloat(), enabled, onHoldMsChange)
    SpeakerServoMiniSlider("步进角度", "${stepAngle.roundToInt()}°", stepAngle, 1f..SPEAKER_SERVO_MAX_ANGLE.toFloat(), enabled, onStepAngleChange)
    SpeakerServoMiniSlider("步进间隔", "${intervalMs.roundToInt()}ms", intervalMs, SPEAKER_SERVO_MIN_INTERVAL_MS.toFloat()..SPEAKER_SERVO_MAX_INTERVAL_MS.toFloat(), enabled, onIntervalMsChange)
}

@Composable
private fun SpeakerServoMiniSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = SpeakerMuted)
        Text(text = valueText, style = MaterialTheme.typography.bodySmall, color = SpeakerFg, fontWeight = FontWeight.Bold)
    }
    TjiControlSlider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
        valueRange = valueRange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun servoStatusText(reportedAngle: Int?, servoState: SpeakerServoState?): String {
    if (servoState == null) {
        return reportedAngle?.let { "${it.coerceIn(SPEAKER_SERVO_MIN_ANGLE, SPEAKER_SERVO_MAX_ANGLE)}°" } ?: "未上报"
    }
    val current = servoState.currentAngle ?: reportedAngle
    val target = servoState.targetAngle
    val mode = when {
        servoState.stepMode -> "分段"
        servoState.sweepActive -> if (servoState.infinite) "连续往返" else "往返"
        servoState.moving -> "移动中"
        else -> "静止"
    }
    return listOfNotNull(
        current?.let { "当前 ${it}°" },
        target?.let { "目标 ${it}°" },
        servoState.speedDps?.let { "速度 ${it}°/s" },
        "状态 $mode",
        servoState.cyclesLeft?.let { if (servoState.infinite) null else "剩余 $it" }
    ).joinToString(" · ")
}

@Composable
internal fun TalkStatus(state: SpeakerTalkState) {
    val text = speakerTalkStatusText(state)
    val color = when (state.mode) {
        SpeakerTalkMode.Idle -> if (state.error == null) SpeakerMuted else TjiError
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
    activeModes: Set<SpeakerTalkMode> = setOf(SpeakerTalkMode.Recording, SpeakerTalkMode.RecordingToStore),
    requestPermission: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    val active = mode in activeModes
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
