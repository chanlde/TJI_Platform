package com.tji.device.product.radiodetection.ui.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.radiodetection.model.RadioDetectionSampleData
import com.tji.device.product.radiodetection.model.RadioDetectionTab
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioDetectionUiState
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.model.RadioSignalLevel
import com.tji.device.product.radiodetection.repository.RadioDetectionDeviceState
import com.tji.device.product.radiodetection.viewmodel.RadioDetectionControlViewModel
import com.tji.device.ui.components.PayloadActionButton
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

internal val AppBlack = Color(0xFF1F1F1F)
internal val PageBg = PayloadColors.Background
internal val CardBg = PayloadColors.Surface
internal val MapBg = Color(0xFF242424)
internal val Border = PayloadColors.Border
internal val TextPrimary = PayloadColors.TextPrimary
internal val TextMuted = PayloadColors.TextMuted
internal val Blue = PayloadColors.Primary
internal val Green = PayloadColors.Success
internal val Red = PayloadColors.Danger
internal val Amber = PayloadColors.Warning

@Composable
fun RadioDetectionControlScreen(
    device: BoundAccountDevice,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fallbackState = remember(device.serialNumber, device.name) {
        RadioDetectionSampleData.uiState(
            deviceName = "频谱检测仪",
            deviceSerial = device.serialNumber.ifBlank { "T1640618D" }
        )
    }
    val isPreview = LocalInspectionMode.current
    val viewModel: RadioDetectionControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.radioDetectionControlViewModelFactory)
    }
    val liveDevices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    }
    val rgbFeedback by viewModel?.rgbFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf<RadioRgbCommandFeedback?>(null) }
    }
    val liveDevice = liveDevices.firstOrNull { it.serialNumber == device.serialNumber }
    val state = remember(fallbackState, liveDevice) {
        liveDevice?.toUiState(fallbackState) ?: fallbackState
    }
    var selectedTab by remember { mutableStateOf(RadioDetectionTab.Monitor) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var sheetHidden by remember { mutableStateOf(false) }
    var monitorMode by remember { mutableStateOf(RadioMonitorMode.Map) }
    var selectedTarget by remember { mutableStateOf<RadioDetectionTarget?>(null) }
    var focusedTargetId by remember { mutableStateOf<String?>(null) }
    var focusTargetSignal by remember { mutableStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var showRgbSettings by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<RadioListStatus?>(null) }
    var confirmText by remember { mutableStateOf<String?>(null) }
    val focusTargetOnMap: (RadioDetectionTarget) -> Unit = { target ->
        focusedTargetId = target.id
        focusTargetSignal += 1
        monitorMode = RadioMonitorMode.Map
        sheetExpanded = false
        sheetHidden = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == RadioDetectionTab.Monitor) {
                MonitorScreen(
                    state = state,
                    expanded = sheetExpanded,
                    hidden = sheetHidden,
                    mode = monitorMode,
                    focusedTargetId = focusedTargetId,
                    focusTargetSignal = focusTargetSignal,
                    statusFilter = statusFilter,
                    onHideSheet = {
                        sheetHidden = true
                        sheetExpanded = false
                        monitorMode = RadioMonitorMode.Map
                    },
                    onShowSheet = { sheetHidden = false },
                    onModeChange = { mode ->
                        monitorMode = mode
                        sheetHidden = false
                        sheetExpanded = mode == RadioMonitorMode.List
                    },
                    onOpenFilter = { showFilter = true },
                    onReplayLatestRid = {
                        val replayed = viewModel?.replayLatestRid(state.deviceSerial) == true
                        confirmText = if (replayed) {
                            "已回放最近一条真实 RID 数据"
                        } else {
                            "暂无可回放的真实 RID 数据"
                        }
                    },
                    onTargetLocate = focusTargetOnMap,
                    onTargetClick = {
                        focusTargetOnMap(it)
                        selectedTarget = it
                    },
                    onTargetAction = { confirmText = "确认将 ${it.name} 加入处置队列？" },
                    onOpenRgbSettings = { showRgbSettings = true },
                    onBack = onBack,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SecondaryScreen(
                    tab = selectedTab,
                    state = state,
                    onBack = onBack,
                    modifier = Modifier.weight(1f)
                )
            }
            RadioBottomNav(
                selectedTab = selectedTab,
                onTabSelected = {
                    selectedTab = it
                    sheetExpanded = false
                    sheetHidden = false
                    monitorMode = RadioMonitorMode.Map
                    if (it != RadioDetectionTab.Monitor) {
                        focusedTargetId = null
                        focusTargetSignal += 1
                    }
                }
            )
        }

        if (showFilter) {
            Scrim { showFilter = false }
            FilterSheet(
                selectedStatus = statusFilter,
                onStatusSelected = { statusFilter = it },
                onReset = { statusFilter = null },
                onDismiss = { showFilter = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showRgbSettings) {
            Scrim { showRgbSettings = false }
            RadioRgbControlSheet(
                deviceSerial = state.deviceSerial,
                latestAck = state.rgbAck,
                feedback = rgbFeedback,
                onPreview = { mode, color, brightness, speed ->
                    viewModel?.sendRgbCommand(
                        serialNumber = state.deviceSerial,
                        mode = mode,
                        color = color,
                        brightness = brightness,
                        speed = speed,
                        save = false
                    )
                },
                onSave = { mode, color, brightness, speed ->
                    viewModel?.sendRgbCommand(
                        serialNumber = state.deviceSerial,
                        mode = mode,
                        color = color,
                        brightness = brightness,
                        speed = speed,
                        save = true
                    )
                },
                onDismiss = { showRgbSettings = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        selectedTarget?.let { target ->
            Scrim { selectedTarget = null }
            TargetDetailSheet(
                target = target,
                onDismiss = { selectedTarget = null },
                onConfirm = { confirmText = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        confirmText?.let { message ->
            Scrim { confirmText = null }
            ConfirmDialog(
                message = message,
                onDismiss = { confirmText = null },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun RadioDetectionDeviceState.toUiState(fallback: RadioDetectionUiState): RadioDetectionUiState =
    fallback.copy(
        deviceName = displayName,
        deviceSerial = serialNumber,
        networkStatus = if (isOnline) "良好" else "离线",
        payloadStatus = payloadStatus,
        onlineDeviceCount = if (isOnline) 1 else 0,
        currentCoordinate = currentCoordinate ?: fallback.currentCoordinate,
        targets = targets,
        rgbAck = rgbAck
    )

@Composable
private fun MonitorScreen(
    state: RadioDetectionUiState,
    expanded: Boolean,
    hidden: Boolean,
    mode: RadioMonitorMode,
    focusedTargetId: String?,
    focusTargetSignal: Int,
    statusFilter: RadioListStatus?,
    onHideSheet: () -> Unit,
    onShowSheet: () -> Unit,
    onModeChange: (RadioMonitorMode) -> Unit,
    onOpenFilter: () -> Unit,
    onReplayLatestRid: () -> Unit,
    onTargetLocate: (RadioDetectionTarget) -> Unit,
    onTargetClick: (RadioDetectionTarget) -> Unit,
    onTargetAction: (RadioDetectionTarget) -> Unit,
    onOpenRgbSettings: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        BlackHeader(
            title = "无线电检测",
            subtitle = "实时监控 · ${state.deviceName} ${state.deviceSerial}",
            actionText = "",
            trailingText = "...",
            onBack = onBack,
            onTrailingClick = onOpenRgbSettings
        )
        MonitorStatusStrip(state)
        Box(modifier = Modifier.weight(1f)) {
            TacticalMap(
                state = state,
                mode = mode,
                focusedTargetId = focusedTargetId,
                focusTargetSignal = focusTargetSignal,
                onModeChange = onModeChange,
                onTargetClick = onTargetClick,
                modifier = Modifier.fillMaxSize()
            )
            if (hidden) {
                MiniTargetHandle(
                    count = state.discoveredTargetCount,
                    onClick = onShowSheet,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            } else {
                TargetSheet(
                    state = state,
                    expanded = expanded || mode == RadioMonitorMode.List,
                    statusFilter = statusFilter,
                    onHide = onHideSheet,
                    onOpenFilter = onOpenFilter,
                    onReplayLatestRid = onReplayLatestRid,
                    onTargetLocate = onTargetLocate,
                    onTargetClick = onTargetClick,
                    onTargetAction = onTargetAction,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
internal fun BlackHeader(
    title: String,
    subtitle: String,
    actionText: String,
    trailingText: String,
    onBack: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(AppBlack)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .noRippleClickable(onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (actionText.isNotBlank()) HeaderIconButton(actionText)
            if (trailingText.isNotBlank()) HeaderIconButton(trailingText, onTrailingClick)
        }
    }
}

@Composable
private fun HeaderIconButton(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
            .let { base -> if (onClick != null) base.noRippleClickable(onClick) else base },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonitorStatusStrip(state: RadioDetectionUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(CardBg)
            .border(1.dp, Border),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StripMetric("范围", state.detectionRange, Modifier.weight(1f))
        StripMetric("发现", "${state.discoveredTargetCount}", Modifier.weight(1f))
        StripMetric("在线", "${state.onlineDeviceCount}", Modifier.weight(1f))
        StripMetric("检测", state.runningDuration, Modifier.weight(1f))
    }
}

@Composable
private fun StripMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(0.5.dp, Border)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun StatusChip(text: String, active: Boolean, color: Color) {
    val bg by animateColorAsState(if (active) color.copy(alpha = 0.12f) else Color.Transparent, label = "radioChipBg")
    Text(
        text = text,
        color = if (active) color else TextMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, if (active) color.copy(alpha = 0.28f) else Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .defaultMinSize(minWidth = 48.dp)
    )
}

@Composable
internal fun OutlineAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PayloadActionButton(
        text = text,
        enabled = true,
        color = Blue,
        soft = true,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 68.dp)
    )
}

@Composable
internal fun SolidAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PayloadActionButton(
        text = text,
        enabled = true,
        color = Blue,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 68.dp)
    )
}

@Composable
private fun RadioBottomNav(
    selectedTab: RadioDetectionTab,
    onTabSelected: (RadioDetectionTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.White)
            .border(1.dp, Border),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDetectionTab.values().forEach { tab ->
            val selected = tab == selectedTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .noRippleClickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(tab.symbol(), color = if (selected) Blue else TextMuted, fontWeight = FontWeight.Bold)
                Text(tab.label, color = if (selected) Blue else TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadioRgbControlSheet(
    deviceSerial: String,
    latestAck: RadioRgbAck?,
    feedback: RadioRgbCommandFeedback?,
    onPreview: (RadioRgbMode, RadioRgbColor, Int, Int?) -> Unit,
    onSave: (RadioRgbMode, RadioRgbColor, Int, Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(RadioRgbMode.Strobe) }
    var color by remember { mutableStateOf(RadioRgbColor.RedBlue) }
    var brightness by remember { mutableStateOf(80) }
    var speed by remember { mutableStateOf(50) }
    val availableColors = RadioRgbColor.values().filter { it.supportedBy(mode) }
    fun preview(
        nextMode: RadioRgbMode = mode,
        nextColor: RadioRgbColor = color,
        nextBrightness: Int = brightness,
        nextSpeed: Int = speed
    ) {
        onPreview(
            nextMode,
            nextColor,
            nextBrightness,
            if (nextMode == RadioRgbMode.Breath) nextSpeed else null
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(CardBg)
            .noRippleClickable { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("灯语控制", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("设备 $deviceSerial", color = TextMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
            OutlineAction("关闭", onClick = onDismiss)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("模式", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioRgbMode.values().forEach { item ->
                    Box(
                        modifier = Modifier.noRippleClickable {
                            val nextColor = if (color.supportedBy(item)) color else RadioRgbColor.Red
                            mode = item
                            color = nextColor
                            preview(nextMode = item, nextColor = nextColor)
                        }
                    ) {
                        StatusChip(item.label, mode == item, Blue)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("颜色", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableColors.forEach { item ->
                    RgbColorChip(
                        color = item,
                        selected = color == item,
                        onClick = {
                            color = item
                            preview(nextColor = item)
                        }
                    )
                }
            }
        }

        RadioRgbValueStepper(
            title = "亮度",
            value = brightness,
            displayValue = if (brightness == 0) "0% · 关闭" else "$brightness%",
            onDecrease = {
                val next = (brightness - 10).coerceAtLeast(0)
                if (next != brightness) {
                    brightness = next
                    preview(nextBrightness = next)
                }
            },
            onIncrease = {
                val next = (brightness + 10).coerceAtMost(100)
                if (next != brightness) {
                    brightness = next
                    preview(nextBrightness = next)
                }
            }
        )

        if (mode == RadioRgbMode.Breath) {
            RadioRgbValueStepper(
                title = "呼吸速度",
                value = speed,
                displayValue = "$speed%",
                onDecrease = {
                    val next = (speed - 10).coerceAtLeast(0)
                    if (next != speed) {
                        speed = next
                        preview(nextSpeed = next)
                    }
                },
                onIncrease = {
                    val next = (speed + 10).coerceAtMost(100)
                    if (next != speed) {
                        speed = next
                        preview(nextSpeed = next)
                    }
                }
            )
        }

        val ackMatchesFeedback = latestAck != null && feedback != null && latestAck.msgId == feedback.msgId
        val statusText = when {
            ackMatchesFeedback -> latestAck!!.statusText
            feedback != null -> feedback.text
            latestAck != null -> "最近确认：${latestAck.statusText}"
            else -> "点击模式、颜色或数值会立即预览；保存默认才会写入设备配置。"
        }
        val statusColor = when {
            ackMatchesFeedback && latestAck!!.ok -> Green
            ackMatchesFeedback && !latestAck!!.ok -> Red
            feedback?.success == false -> Red
            feedback?.pending == true -> Blue
            else -> TextMuted
        }
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        SolidAction(
            text = "保存默认",
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSave(mode, color, brightness, if (mode == RadioRgbMode.Breath) speed else null) }
        )
    }
}

@Composable
private fun RgbColorChip(
    color: RadioRgbColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = color.rgbAccent()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) accent.copy(alpha = 0.35f) else Border, RoundedCornerShape(999.dp))
            .noRippleClickable(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = color.label,
            color = if (selected) accent else TextMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RadioRgbValueStepper(
    title: String,
    value: Int,
    displayValue: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PageBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Text(displayValue, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        OutlineAction("-", modifier = Modifier.width(48.dp), onClick = onDecrease)
        OutlineAction("+", modifier = Modifier.width(48.dp), onClick = onIncrease)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    selectedStatus: RadioListStatus?,
    onStatusSelected: (RadioListStatus?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(CardBg)
            .noRippleClickable { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("筛选目标", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlineAction("关闭") { onDismiss() }
        }
        FilterGroup("类型", listOf("全部", "无人机", "飞手", "民航", "船舶"))
        Text("名单状态", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("全部", selectedStatus == null, Blue)
            RadioListStatus.values().forEach { status ->
                Box(modifier = Modifier.noRippleClickable { onStatusSelected(status) }) {
                    StatusChip(status.label, selectedStatus == status, status.statusColor())
                }
            }
        }
        FilterGroup("信号状态", RadioSignalLevel.values().map { it.label })
        FilterGroup("在线状态", listOf("在线", "离线"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PayloadActionButton(
                text = "重置",
                enabled = true,
                color = Blue,
                soft = true,
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
            PayloadActionButton(
                text = "确认筛选",
                enabled = true,
                color = Blue,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, value ->
                StatusChip(value, index == 0, if (index == 0) Blue else TextMuted)
            }
        }
    }
}

@Composable
private fun TargetDetailSheet(
    target: RadioDetectionTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(CardBg)
            .noRippleClickable { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(target.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(target.serialNumber, color = TextMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
            StatusChip(target.listStatus.label, true, target.listStatus.statusColor())
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PageBg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("类型", target.type, Modifier.weight(1f))
                MiniMetric("频段", target.frequencyLabel, Modifier.weight(1f))
                MiniMetric("高度", "${target.altitudeMeters}m", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("速度", "${target.speedMetersPerSecond}m/s", Modifier.weight(1f))
                MiniMetric("航向", "${target.headingDegrees}°", Modifier.weight(1f))
                MiniMetric("信号", target.signalLevel.label, Modifier.weight(1f))
            }
            HorizontalDivider(color = Border)
            DetailLine("目标位置", "纬度 %.6f / 经度 %.6f".format(target.latitude, target.longitude))
            DetailLine("飞手位置", "纬度 %.6f / 经度 %.6f".format(target.pilotLatitude, target.pilotLongitude))
            DetailLine("目标配对", "${target.pilotName} · 距目标 ${target.pilotDistanceText}")
            DetailLine("原始数据摘要", "远程识别持续广播，最近更新时间 ${target.lastSeenText}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineAction("地图定位", Modifier.weight(1f)) { onConfirm("地图已定位到 ${target.name}") }
            OutlineAction("查看轨迹", Modifier.weight(1f)) { onConfirm("已打开 ${target.name} 的轨迹记录") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineAction("加入黑名单", Modifier.weight(1f)) { onConfirm("确认将 ${target.name} 加入黑名单？") }
            SolidAction("生成执法记录", Modifier.weight(1f)) { onConfirm("确认生成 ${target.name} 的执法记录？") }
        }
        OutlineAction("关闭详情", Modifier.fillMaxWidth(), onDismiss)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConfirmDialog(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        shape = RoundedCornerShape(PayloadDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("二次确认", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction("返回", Modifier.weight(1f), onDismiss)
                SolidAction("确认", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

@Composable
private fun Scrim(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .noRippleClickable(onClick)
    )
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

internal fun RadioListStatus.statusColor(): Color = when (this) {
    RadioListStatus.Blacklist -> Red
    RadioListStatus.Whitelist -> Green
    RadioListStatus.Unknown -> Amber
}

private fun RadioRgbColor.rgbAccent(): Color = when (this) {
    RadioRgbColor.Red -> Red
    RadioRgbColor.Green -> Green
    RadioRgbColor.Blue -> Blue
    RadioRgbColor.RedBlue -> Color(0xFF8B5CF6)
}

private fun RadioDetectionTab.symbol(): String = when (this) {
    RadioDetectionTab.Monitor -> "✧"
    RadioDetectionTab.Tracks -> "⌁"
    RadioDetectionTab.Zones -> "◎"
    RadioDetectionTab.Enforcement -> "▣"
    RadioDetectionTab.Lists -> "☷"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RadioDetectionControlScreenPreview() {
    BucketTheme {
        RadioDetectionControlScreen(
            device = BoundAccountDevice(
                serialNumber = "T1640618D",
                name = "频谱检测仪",
                productType = ProductType.RadioDetection
            )
        )
    }
}
