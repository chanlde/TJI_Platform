package com.tji.device.product.droppersixstage.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalInspectionMode
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedback
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedbackStatus
import com.tji.device.product.droppersixstage.viewmodel.DropperSixStageViewModel
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import kotlinx.coroutines.delay

@Composable
fun DropperSixStageControlScreen(
    device: BoundAccountDevice,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val viewModel: DropperSixStageViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.dropperSixStageViewModelFactory)
    }
    val devices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(emptyList()) }
    }
    val feedback by viewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(DropperCommandFeedback()) }
    }
    val state = devices.firstOrNull { it.serialNumber == device.serialNumber }
    val displayState = if (isPreview) previewDropperState(device.serialNumber, device.name) else state
    val enabled = viewModel != null && displayState?.isOnline == true
    val stages = displayState?.stages?.takeIf { it.isNotEmpty() } ?: DropperStageState.defaults()
    var openDurationMs by remember { mutableIntStateOf(DEFAULT_OPEN_DURATION_MS) }
    var selectedStageIndex by remember(device.serialNumber) { mutableIntStateOf(1) }
    var testingStage by remember { mutableStateOf<Int?>(null) }
    val selectedStage = stages.firstOrNull { it.index == selectedStageIndex } ?: stages.first()

    LaunchedEffect(stages) {
        if (stages.none { it.index == selectedStageIndex }) {
            selectedStageIndex = stages.firstOrNull()?.index ?: 1
        }
    }

    LaunchedEffect(testingStage, openDurationMs, enabled, device.serialNumber) {
        val stage = testingStage ?: return@LaunchedEffect
        if (!enabled) return@LaunchedEffect
        while (true) {
            viewModel?.timedOpenStage(device.serialNumber, stage, openDurationMs)
            delay(openDurationMs + TEST_LOOP_GAP_MS)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PayloadColors.Background),
        contentPadding = PaddingValues(PayloadDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item {
            DropperHeaderCard(
                device = device,
                state = displayState,
                feedback = feedback
            )
        }
        item {
            TjiSectionCard(
                title = "抛投控制",
                trailing = {
                    DurationControl(
                        durationMs = openDurationMs,
                        onDecrease = {
                            openDurationMs = (openDurationMs - DURATION_STEP_MS).coerceAtLeast(MIN_OPEN_DURATION_MS)
                        },
                        onIncrease = {
                            openDurationMs = (openDurationMs + DURATION_STEP_MS).coerceAtMost(MAX_OPEN_DURATION_MS)
                        }
                    )
                }
            ) {
                FeedbackBadge(feedback)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TjiActionButton(
                        text = "全部开钩",
                        enabled = enabled,
                        color = PayloadColors.Primary,
                        onClick = { viewModel?.toggleAll(device.serialNumber, true) },
                        modifier = Modifier.weight(1f)
                    )
                    TjiActionButton(
                        text = "全部关闭",
                        enabled = enabled,
                        color = PayloadColors.Warning,
                        onClick = { viewModel?.toggleAll(device.serialNumber, false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            StageSelectorCard(
                stages = stages,
                selectedStageIndex = selectedStageIndex,
                onSelectStage = { selectedStageIndex = it }
            )
        }
        item {
            SelectedStageControlCard(
                stage = selectedStage,
                enabled = enabled,
                durationMs = openDurationMs,
                isTesting = testingStage == selectedStage.index,
                testEnabled = enabled && (testingStage == null || testingStage == selectedStage.index),
                onOpen = { viewModel?.toggleStage(device.serialNumber, selectedStage.index, true) },
                onClose = { viewModel?.toggleStage(device.serialNumber, selectedStage.index, false) },
                onTimedOpen = { viewModel?.timedOpenStage(device.serialNumber, selectedStage.index, openDurationMs) },
                onToggleTest = {
                    testingStage = if (testingStage == selectedStage.index) null else selectedStage.index
                }
            )
        }
    }
}

@Composable
private fun DurationControl(
    durationMs: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TextButton(onClick = onDecrease) {
            Text("-")
        }
        Text(
            text = "${durationMs / 1000.0}s",
            style = MaterialTheme.typography.labelLarge,
            color = PayloadColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onIncrease) {
            Text("+")
        }
    }
}

@Composable
internal fun DropperHeaderCard(
    device: BoundAccountDevice,
    state: DropperSixStageState?,
    feedback: DropperCommandFeedback
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
            TjiMetricTile(label = "通道", value = "${state?.stages?.size ?: 6}路")
            TjiMetricTile(
                label = "已抛投",
                value = "${state?.stages?.count { it.isOpen } ?: 0}"
            )
            TjiMetricTile(
                label = "电量",
                value = state?.batteryPercent?.let { "$it%" } ?: "--"
            )
        }
        FeedbackBadge(feedback)
    }
}

@Composable
internal fun StageSelectorCard(
    stages: List<DropperStageState>,
    selectedStageIndex: Int,
    onSelectStage: (Int) -> Unit
) {
    TjiSectionCard(title = "选择通道") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stages.forEach { stage ->
                StageSelectorChip(
                    stage = stage,
                    selected = stage.index == selectedStageIndex,
                    onClick = { onSelectStage(stage.index) }
                )
            }
        }
    }
}

@Composable
private fun StageSelectorChip(
    stage: DropperStageState,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        selected -> PayloadColors.Primary
        stage.isOpen -> PayloadColors.PrimarySoft
        else -> PayloadColors.SurfaceSoft
    }
    val foreground = when {
        selected -> Color.White
        stage.isOpen -> PayloadColors.Primary
        else -> PayloadColors.TextPrimary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) PayloadColors.Primary else PayloadColors.Border,
                shape = RoundedCornerShape(PayloadDimens.ControlRadius)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${stage.index}",
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (stage.isOpen) "已触发" else "待命",
            style = MaterialTheme.typography.labelMedium,
            color = foreground.copy(alpha = if (selected) 0.92f else 0.74f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun SelectedStageControlCard(
    stage: DropperStageState,
    enabled: Boolean,
    durationMs: Int,
    isTesting: Boolean,
    testEnabled: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTimedOpen: () -> Unit,
    onToggleTest: () -> Unit
) {
    TjiSectionCard(title = "${stage.displayName}控制") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (stage.isOpen) PayloadColors.Primary else PayloadColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stage.index.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (stage.isOpen) Color.White else PayloadColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = if (stage.isOpen) "开钩中 / 已触发" else "待命",
                        style = MaterialTheme.typography.titleSmall,
                        color = PayloadColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stage.payloadLoaded?.let { if (it) "载荷就绪" else "载荷未检测" } ?: "载荷状态未上报",
                        style = MaterialTheme.typography.labelMedium,
                        color = PayloadColors.TextMuted
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TjiActionButton(
                    text = "开钩",
                    enabled = enabled,
                    color = PayloadColors.Primary,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                )
                TjiActionButton(
                    text = "关钩",
                    enabled = enabled,
                    color = PayloadColors.Warning,
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TjiActionButton(
                    text = "自动 ${durationMs / 1000.0}s",
                    enabled = enabled,
                    color = PayloadColors.Primary,
                    onClick = onTimedOpen,
                    modifier = Modifier.weight(1f)
                )
                TjiActionButton(
                    text = if (isTesting) "停止循环" else "循环动作",
                    enabled = testEnabled,
                    color = if (isTesting) PayloadColors.Warning else PayloadColors.Primary,
                    onClick = onToggleTest,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FeedbackBadge(feedback: DropperCommandFeedback) {
    val color = when (feedback.status) {
        DropperCommandFeedbackStatus.Success -> PayloadColors.Success
        DropperCommandFeedbackStatus.Failed,
        DropperCommandFeedbackStatus.Timeout -> PayloadColors.Danger
        DropperCommandFeedbackStatus.Pending -> PayloadColors.Primary
        DropperCommandFeedbackStatus.Idle -> PayloadColors.TextMuted
    }
    TjiFeedbackBadge(text = feedback.text, color = color)
}

private const val DEFAULT_OPEN_DURATION_MS = 1_000
private const val MIN_OPEN_DURATION_MS = 100
private const val MAX_OPEN_DURATION_MS = 30_000
private const val DURATION_STEP_MS = 500
private const val TEST_LOOP_GAP_MS = 1_000L
