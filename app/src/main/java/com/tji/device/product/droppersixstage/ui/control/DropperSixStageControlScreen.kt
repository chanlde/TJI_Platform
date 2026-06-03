package com.tji.device.product.droppersixstage.ui.control

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalInspectionMode
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedback
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedbackStatus
import com.tji.device.product.droppersixstage.viewmodel.DropperSixStageViewModel
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiMiniSwitch
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.ui.theme.TjiBackground
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiTextMuted
import com.tji.device.ui.theme.TjiTextPrimary
import com.tji.device.ui.theme.TjiWarning

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TjiBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    FeedbackBadge(feedback)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TjiActionButton(
                        text = "全部抛投",
                        enabled = enabled,
                        color = TjiPrimary,
                        onClick = { viewModel?.toggleAll(device.serialNumber, true) },
                        modifier = Modifier.weight(1f)
                    )
                    TjiActionButton(
                        text = "全部复位",
                        enabled = enabled,
                        color = TjiWarning,
                        onClick = { viewModel?.toggleAll(device.serialNumber, false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        items(displayState?.stages ?: DropperStageState.defaults(), key = { it.index }) { stage ->
            DropperStageRow(
                stage = stage,
                enabled = enabled,
                onCheckedChange = { open ->
                    viewModel?.toggleStage(device.serialNumber, stage.index, open)
                }
            )
        }
    }
}

@Composable
private fun DropperHeaderCard(
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
private fun DropperStageRow(
    stage: DropperStageState,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TjiSectionCard(title = stage.displayName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (stage.isOpen) TjiPrimary else TjiTextMuted.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stage.index.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (stage.isOpen) androidx.compose.ui.graphics.Color.White else TjiTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = if (stage.isOpen) "已抛投" else "待命",
                        style = MaterialTheme.typography.titleSmall,
                        color = TjiTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stage.payloadLoaded?.let { if (it) "载荷就绪" else "载荷未检测" } ?: "载荷状态未上报",
                        style = MaterialTheme.typography.labelMedium,
                        color = TjiTextMuted
                    )
                }
            }
            TjiMiniSwitch(
                checked = stage.isOpen,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun FeedbackBadge(feedback: DropperCommandFeedback) {
    val color = when (feedback.status) {
        DropperCommandFeedbackStatus.Success -> TjiOnline
        DropperCommandFeedbackStatus.Failed,
        DropperCommandFeedbackStatus.Timeout -> TjiError
        DropperCommandFeedbackStatus.Pending -> TjiPrimary
        DropperCommandFeedbackStatus.Idle -> TjiTextMuted
    }
    TjiFeedbackBadge(text = feedback.text, color = color)
}

private fun previewDropperState(serialNumber: String, name: String) =
    DropperSixStageState(
        serialNumber = serialNumber,
        name = name,
        isOnline = true,
        stages = listOf(
            DropperStageState(1, isOpen = false, payloadLoaded = true),
            DropperStageState(2, isOpen = true, payloadLoaded = true),
            DropperStageState(3, isOpen = false, payloadLoaded = true),
            DropperStageState(4, isOpen = false, payloadLoaded = false),
            DropperStageState(5, isOpen = false, payloadLoaded = null),
            DropperStageState(6, isOpen = false, payloadLoaded = true)
        ),
        batteryPercent = 86
    )

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DropperSixStageControlScreenPreview() {
    BucketTheme {
        DropperSixStageControlScreen(
            device = BoundAccountDevice(
                serialNumber = "DROP-6-0001",
                name = "六段抛投 01",
                productType = ProductType.DropperSixStage
            )
        )
    }
}
