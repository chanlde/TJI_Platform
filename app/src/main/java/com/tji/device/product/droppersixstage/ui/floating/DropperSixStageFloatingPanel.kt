package com.tji.device.product.droppersixstage.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.di.AppContainer
import com.tji.device.product.droppersixstage.model.DROPPER_DEFAULT_OPEN_DURATION_MS
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedback
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedbackStatus
import com.tji.device.product.droppersixstage.viewmodel.DropperSixStageViewModel
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMiniSwitch
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@Suppress("LongMethod") // The floating control shares one device and safety-state scope.
@Composable
fun DropperSixStageFloatingPanel(
    link: FloatingLinkSummary?,
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
    val armedDeviceIds by viewModel?.armedDeviceIds?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(emptySet()) }
    }
    val feedback by viewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(DropperCommandFeedback()) }
    }
    val serialNumber = link?.serialNumber
    val state = devices.firstOrNull { it.serialNumber == serialNumber }
    val stages = state?.stages ?: DropperStageState.defaults()
    val online = !serialNumber.isNullOrBlank() && link?.isOnline == true
    val armed = serialNumber in armedDeviceIds
    val visibleFeedback = feedback.takeIf { it.serialNumber == null || it.serialNumber == serialNumber }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DropperFloatingSafetyRow(
            online = online,
            armed = armed,
            feedback = visibleFeedback,
            onToggleSafety = {
                serialNumber?.let { deviceId ->
                    if (armed) viewModel?.disarm(deviceId) else viewModel?.arm(deviceId)
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjiActionButton(
                text = "全部开钩",
                enabled = online && armed,
                color = PayloadColors.Primary,
                onClick = {
                    serialNumber?.let { deviceId ->
                        viewModel?.toggleAll(
                            serialNumber = deviceId,
                            open = true,
                            durationMs = DROPPER_DEFAULT_OPEN_DURATION_MS
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            TjiActionButton(
                text = "全部关闭",
                enabled = online,
                color = PayloadColors.Warning,
                onClick = {
                    serialNumber?.let { deviceId ->
                        viewModel?.toggleAll(deviceId, false)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            stages.forEach { stage ->
                DropperStageCompactToggle(
                    stage = stage.index,
                    checked = stage.isOpen,
                    enabled = online && armed,
                    onCheckedChange = { shouldOpen ->
                        serialNumber?.let { deviceId ->
                            if (shouldOpen) {
                                viewModel?.timedOpenStage(
                                    serialNumber = deviceId,
                                    stage = stage.index,
                                    durationMs = DROPPER_DEFAULT_OPEN_DURATION_MS
                                )
                            } else {
                                viewModel?.toggleStage(deviceId, stage.index, false)
                            }
                        }
                    }
                )
            }
        }
        Text(
            text = "开钩后 1 秒自动关闭",
            style = MaterialTheme.typography.labelSmall,
            color = PayloadColors.TextMuted
        )
    }
}

@Composable
private fun DropperFloatingSafetyRow(
    online: Boolean,
    armed: Boolean,
    feedback: DropperCommandFeedback?,
    onToggleSafety: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (armed) "已解锁" else "已上锁",
            style = MaterialTheme.typography.labelMedium,
            color = if (armed) PayloadColors.Success else PayloadColors.Warning,
            fontWeight = FontWeight.SemiBold
        )
        feedback?.text?.let { message ->
            TjiFeedbackBadge(
                text = message,
                color = when (feedback.status) {
                    DropperCommandFeedbackStatus.Success -> PayloadColors.Success
                    DropperCommandFeedbackStatus.Failed,
                    DropperCommandFeedbackStatus.Timeout -> PayloadColors.Danger
                    DropperCommandFeedbackStatus.Pending -> PayloadColors.Primary
                    DropperCommandFeedbackStatus.Idle -> PayloadColors.TextMuted
                },
                modifier = Modifier.weight(1f)
            )
        } ?: Box(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onToggleSafety,
            enabled = online
        ) {
            Text(if (armed) "上锁" else "解锁")
        }
    }
}

@Composable
private fun DropperStageCompactToggle(
    stage: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .width(122.dp)
            .background(PayloadColors.Surface.copy(alpha = 0.82f), RoundedCornerShape(PayloadDimens.ControlRadius))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    color = when {
                        !enabled -> PayloadColors.Border
                        checked -> PayloadColors.Primary
                        else -> PayloadColors.PrimarySoft
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stage.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled && !checked) PayloadColors.Primary else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${stage}段",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        TjiMiniSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
