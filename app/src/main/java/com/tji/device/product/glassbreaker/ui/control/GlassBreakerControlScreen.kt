package com.tji.device.product.glassbreaker.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.glassbreaker.model.GLASS_BREAKER_CHANNEL_COUNT
import com.tji.device.product.glassbreaker.model.GlassBreakerAck
import com.tji.device.product.glassbreaker.model.GlassBreakerLockState
import com.tji.device.product.glassbreaker.model.GlassBreakerState
import com.tji.device.product.glassbreaker.viewmodel.GlassBreakerCommandFeedback
import com.tji.device.product.glassbreaker.viewmodel.GlassBreakerCommandFeedbackStatus
import com.tji.device.product.glassbreaker.viewmodel.GlassBreakerControlViewModel
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import kotlin.math.roundToInt

@Composable
fun GlassBreakerControlScreen(
    device: BoundAccountDevice,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val viewModel: GlassBreakerControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.glassBreakerControlViewModelFactory)
    }
    val devices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(emptyList()) }
    }
    val feedback by viewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(GlassBreakerCommandFeedback()) }
    }

    val state = devices.firstOrNull { it.serialNumber == device.serialNumber }
    val displayState = if (isPreview) previewGlassBreakerState(device.serialNumber, device.name) else state
    val enabled = viewModel != null && displayState?.isOnline == true
    val canSelectChannel = displayState?.let { enabled && it.isUnlocked } == true
    val canFire = displayState?.let { canSelectChannel && it.selectedChannel != null } == true

    LaunchedEffect(device.serialNumber) {
        viewModel?.requestDeviceInfo(device.serialNumber)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PayloadColors.Background),
        contentPadding = PaddingValues(PayloadDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item {
            GlassBreakerHeaderCard(
                device = device,
                state = displayState,
                feedback = feedback
            )
        }
        item {
            SafetyControlCard(
                state = displayState,
                enabled = enabled,
                onUnlock = { viewModel?.unlock(device.serialNumber) },
                onLock = { viewModel?.lock(device.serialNumber) }
            )
        }
        item {
            ChannelControlCard(
                selectedChannel = displayState?.selectedChannel,
                enabled = canSelectChannel,
                onSelect = { viewModel?.selectChannel(device.serialNumber, it) }
            )
        }
        item {
            LaserControlCard(
                laserEnabled = displayState?.laserEnabled == true,
                enabled = enabled,
                onToggle = { viewModel?.setLaser(device.serialNumber, !(displayState?.laserEnabled == true)) }
            )
        }
        item {
            FireControlCard(
                state = displayState,
                enabled = canFire,
                onConfirmFire = { viewModel?.fireSelectedChannel(device.serialNumber, displayState) }
            )
        }
    }
}

@Composable
private fun GlassBreakerHeaderCard(
    device: BoundAccountDevice,
    state: GlassBreakerState?,
    feedback: GlassBreakerCommandFeedback
) {
    val online = state?.isOnline == true
    TjiSectionCard(
        title = device.name,
        trailing = {
            TjiStatusText(
                text = if (online) "在线" else "离线",
                color = if (online) PayloadColors.Success else PayloadColors.Danger
            )
        }
    ) {
        Text(
            text = device.serialNumber,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TjiMetricTile(label = "锁定", value = if (state?.isUnlocked == true) "解锁" else "上锁")
            TjiMetricTile(label = "通道", value = state?.selectedChannel?.toString() ?: "--")
            TjiMetricTile(label = "倒计时", value = armRemainingText(state?.armRemainingMs))
        }
        FeedbackBadge(feedback)
        state?.lastAck?.takeIf { !it.ok }?.let { FailedAckLine(it) }
    }
}

@Composable
private fun SafetyControlCard(
    state: GlassBreakerState?,
    enabled: Boolean,
    onUnlock: () -> Unit,
    onLock: () -> Unit
) {
    TjiSectionCard(
        title = "安全锁",
        trailing = {
            TjiStatusText(
                text = if (state?.isUnlocked == true) "已解锁" else "已上锁",
                color = if (state?.isUnlocked == true) PayloadColors.Warning else PayloadColors.Success
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TjiActionButton(
                text = "解锁",
                enabled = enabled && state?.isUnlocked != true,
                color = PayloadColors.Warning,
                onClick = onUnlock,
                modifier = Modifier.weight(1f)
            )
            TjiActionButton(
                text = "上锁",
                enabled = enabled,
                color = PayloadColors.Primary,
                onClick = onLock,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "重新解锁或上锁后需要重新选择通道。",
            style = MaterialTheme.typography.labelMedium,
            color = PayloadColors.TextMuted
        )
    }
}

@Composable
private fun ChannelControlCard(
    selectedChannel: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    TjiSectionCard(title = "选择通道") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..GLASS_BREAKER_CHANNEL_COUNT).forEach { channel ->
                ChannelChip(
                    channel = channel,
                    selected = selectedChannel == channel,
                    enabled = enabled,
                    onClick = { onSelect(channel) }
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(
    channel: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        selected -> PayloadColors.Primary
        enabled -> PayloadColors.SurfaceSoft
        else -> PayloadColors.Border
    }
    val foreground = when {
        selected -> Color.White
        enabled -> PayloadColors.TextPrimary
        else -> PayloadColors.TextMuted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
            .background(background)
            .border(
                1.dp,
                if (selected) PayloadColors.Primary else PayloadColors.Border,
                RoundedCornerShape(PayloadDimens.ControlRadius)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${channel} 通道",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
    }
}

@Composable
private fun LaserControlCard(
    laserEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    TjiSectionCard(
        title = "激光",
        trailing = {
            TjiStatusText(
                text = if (laserEnabled) "已开启" else "已关闭",
                color = if (laserEnabled) PayloadColors.Warning else PayloadColors.TextMuted
            )
        }
    ) {
        TjiActionButton(
            text = if (laserEnabled) "关闭激光" else "开启激光",
            enabled = enabled,
            color = if (laserEnabled) PayloadColors.Warning else PayloadColors.Primary,
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FireControlCard(
    state: GlassBreakerState?,
    enabled: Boolean,
    onConfirmFire: () -> Unit
) {
    val channel = state?.selectedChannel
    val helper = when {
        state?.isOnline != true -> "设备离线"
        !state.isUnlocked -> "请先解锁"
        channel == null -> "请先选择通道"
        else -> "滑满后击发所选通道"
    }
    TjiSectionCard(
        title = "击发确认",
        trailing = {
            TjiStatusText(
                text = channel?.let { "${it} 通道" } ?: "未选择",
                color = if (enabled) PayloadColors.Danger else PayloadColors.TextMuted
            )
        }
    ) {
        SwipeFireConfirm(
            enabled = enabled,
            text = if (channel == null) "选择通道后滑动击发" else "滑动击发 ${channel} 通道",
            onConfirmed = onConfirmFire
        )
        Text(
            text = helper,
            style = MaterialTheme.typography.labelMedium,
            color = PayloadColors.TextMuted
        )
    }
}

@Composable
private fun SwipeFireConfirm(
    enabled: Boolean,
    text: String,
    onConfirmed: () -> Unit
) {
    var progress by remember(enabled, text) { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val latestOnConfirmed by rememberUpdatedState(onConfirmed)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(PayloadDimens.ControlRadius)
    val trackColor = if (enabled) PayloadColors.Danger.copy(alpha = 0.14f) else PayloadColors.Border
    val fillColor = if (enabled) PayloadColors.Danger else PayloadColors.TextMuted
    val thumbSizePx = with(density) { 44.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(trackColor)
            .border(1.dp, if (enabled) PayloadColors.Danger.copy(alpha = 0.36f) else PayloadColors.Border, shape)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, widthPx, text) {
                if (!enabled || widthPx <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (progress >= 0.92f) {
                            latestOnConfirmed()
                        }
                        progress = 0f
                    },
                    onDragCancel = { progress = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        progress = (progress + dragAmount / widthPx).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(fillColor.copy(alpha = if (enabled) 0.88f else 0.35f))
        )
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (progress > 0.55f) Color.White else PayloadColors.TextPrimary
        )
        Box(
            modifier = Modifier
                .offset {
                    val maxOffset = (widthPx - thumbSizePx).coerceAtLeast(0)
                    IntOffset((maxOffset * progress).roundToInt(), 0)
                }
                .padding(4.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else PayloadColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) PayloadColors.Danger else PayloadColors.TextMuted
            )
        }
    }
}

@Composable
private fun FailedAckLine(ack: GlassBreakerAck) {
    Text(
        text = ack.userFacingMessage(),
        style = MaterialTheme.typography.labelMedium,
        color = PayloadColors.Danger
    )
}

@Composable
private fun FeedbackBadge(feedback: GlassBreakerCommandFeedback) {
    val color = when (feedback.status) {
        GlassBreakerCommandFeedbackStatus.Success -> PayloadColors.Success
        GlassBreakerCommandFeedbackStatus.Failed,
        GlassBreakerCommandFeedbackStatus.Timeout -> PayloadColors.Danger
        GlassBreakerCommandFeedbackStatus.Pending -> PayloadColors.Primary
        GlassBreakerCommandFeedbackStatus.Idle -> PayloadColors.TextMuted
    }
    TjiFeedbackBadge(text = feedback.text, color = color)
}

private fun armRemainingText(value: Long?): String =
    value?.takeIf { it > 0L }?.let { "${(it + 999L) / 1000L}s" } ?: "--"

private fun previewGlassBreakerState(serialNumber: String, name: String): GlassBreakerState =
    GlassBreakerState(
        serialNumber = serialNumber,
        name = name,
        isOnline = true,
        lockState = GlassBreakerLockState.Unlocked,
        selectedChannel = 1,
        laserEnabled = false,
        armRemainingMs = 30_000,
        firmwareVersion = "0.0.9.0",
        firmwareInnerVersion = 9
    )
