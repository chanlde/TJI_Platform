package com.tji.device.product.firegun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.firegun.control.FireGunControlState
import com.tji.device.product.firegun.model.FireGunLinkState
import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.viewmodel.FireGunControlViewModel
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import java.util.Locale

@Composable
fun FireGunControlScreen(
    device: BoundAccountDevice,
    modifier: Modifier = Modifier
) {
    val viewModel: FireGunControlViewModel =
        viewModel(factory = AppContainer.fireGunControlViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linkStatus by viewModel.linkStatus.collectAsStateWithLifecycle()
    val mqttConnected by viewModel.mqttConnected.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf<ConfirmAction?>(null) }

    DisposableEffect(device.serialNumber, viewModel) {
        viewModel.bindDevice(device.serialNumber)
        onDispose { viewModel.unbindDevice(device.serialNumber) }
    }

    FireGunControlPage(
        state = state,
        linkStatus = linkStatus,
        mqttConnected = mqttConnected,
        onUnlock = { confirmation = ConfirmAction.UNLOCK },
        onOpen = { confirmation = ConfirmAction.OPEN },
        onClose = { confirmation = ConfirmAction.CLOSE },
        onStop = { viewModel.actuator(device.serialNumber, FireGunAction.STOP) },
        modifier = modifier
    )

    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            ConfirmAction.UNLOCK -> viewModel.unlock(device.serialNumber)
                            ConfirmAction.OPEN -> viewModel.actuator(device.serialNumber, FireGunAction.OPEN)
                            ConfirmAction.CLOSE -> viewModel.actuator(device.serialNumber, FireGunAction.CLOSE)
                        }
                        confirmation = null
                    },
                    shape = RoundedCornerShape(PayloadDimens.ControlRadius)
                ) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FireGunControlPage(
    state: FireGunControlState,
    linkStatus: FireGunLinkState?,
    mqttConnected: Boolean,
    onUnlock: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlsEnabled = mqttConnected
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PayloadColors.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = PayloadDimens.ScreenPadding,
            vertical = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item { DeviceStatusCard(linkStatus) }
        item {
            UnlockCard(
                state = state,
                enabled = controlsEnabled,
                onUnlock = onUnlock
            )
        }
        item {
            MotionCard(
                state = state,
                enabled = controlsEnabled,
                onOpen = onOpen,
                onClose = onClose,
                onStop = onStop
            )
        }
    }
}

@Composable
private fun DeviceStatusCard(link: FireGunLinkState?) {
    val status = link?.deviceStatus
    TjiSectionCard(
        title = "实时状态",
        trailing = {
            StatusChip(
                text = when {
                    link == null -> "等待心跳"
                    !link.isOnline -> "连接中断"
                    status == null -> "连接正常"
                    status.isOnline -> "设备在线"
                    else -> "设备离线"
                },
                positive = link?.isOnline == true
            )
        }
    ) {
        if (link == null) {
            Text(
                text = "尚未收到控制端心跳，不影响发送控制指令",
                style = MaterialTheme.typography.bodyMedium,
                color = PayloadColors.TextSecondary
            )
            return@TjiSectionCard
        }
        if (status == null) {
            Text(
                text = "控制端已连接，等待设备状态",
                style = MaterialTheme.typography.bodyMedium,
                color = PayloadColors.TextSecondary
            )
            return@TjiSectionCard
        }

        StatusValueRow(
            leftLabel = "锁定",
            leftValue = lockStateText(status.lockState),
            rightLabel = "位置",
            rightValue = actuatorStateText(status.actuatorState)
        )
        StatusValueRow(
            leftLabel = "角度",
            leftValue = status.currentAngle.formatted("°"),
            rightLabel = "供电",
            rightValue = status.inputVoltage.formatted(" V")
        )
        StatusValueRow(
            leftLabel = "运行电流",
            leftValue = status.currentCurrent.formatted(" A"),
            rightLabel = "信号",
            rightValue = status.rssi?.let { "$it dBm" } ?: "--"
        )
    }
}

@Composable
private fun StatusValueRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusValue(leftLabel, leftValue, Modifier.weight(1f))
        StatusValue(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatusValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PayloadColors.TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary
        )
    }
}

@Composable
private fun UnlockCard(
    state: FireGunControlState,
    enabled: Boolean,
    onUnlock: () -> Unit
) {
    val busy = state.pendingUnlock != null
    TjiSectionCard(
        title = "解锁",
        trailing = {
            StatusChip(
                text = if (busy) "等待回执" else lockStateText(state.lockState),
                positive = state.lockState == "unlocking"
            )
        }
    ) {
        Text(
            "执行解锁前请确认周边安全。",
            style = MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextSecondary
        )
        Button(
            onClick = onUnlock,
            enabled = enabled && !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(PayloadDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = PayloadColors.Warning,
                disabledContainerColor = PayloadColors.Border,
                disabledContentColor = PayloadColors.TextMuted
            )
        ) {
            Text(if (busy) "正在解锁" else "解锁", fontWeight = FontWeight.SemiBold)
        }
        FeedbackText(state.unlockFeedback, state.lastCommandSucceeded)
    }
}

@Composable
private fun MotionCard(
    state: FireGunControlState,
    enabled: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit
) {
    val busy = state.pendingActuator != null
    TjiSectionCard(
        title = "动作控制",
        trailing = {
            StatusChip(
                text = if (busy) "等待回执" else actuatorStateText(state.actuatorState),
                positive = state.actuatorState != "stopped"
            )
        }
    ) {
        Text(
            "展开或收回，动作过程中可随时停止。",
            style = MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpen,
                enabled = enabled && !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(PayloadDimens.ControlRadius)
            ) { Text("展开") }
            OutlinedButton(
                onClick = onClose,
                enabled = enabled && !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(PayloadDimens.ControlRadius)
            ) { Text("收回") }
        }
        Button(
            onClick = onStop,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(PayloadDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = PayloadColors.Danger,
                disabledContainerColor = PayloadColors.Border,
                disabledContentColor = PayloadColors.TextMuted
            )
        ) {
            Text("停止动作", fontWeight = FontWeight.Bold)
        }
        FeedbackText(state.actuatorFeedback, state.lastCommandSucceeded)
    }
}

@Composable
private fun StatusChip(text: String, positive: Boolean) {
    val color = if (positive) PayloadColors.Success else PayloadColors.TextSecondary
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun FeedbackText(message: String?, succeeded: Boolean?) {
    if (message.isNullOrBlank()) return
    val color = when (succeeded) {
        true -> PayloadColors.Success
        false -> PayloadColors.Danger
        null -> PayloadColors.Warning
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(PayloadDimens.CompactRadius))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

private fun lockStateText(state: String?): String = when (state) {
    "unlocking" -> "正在解锁"
    "unlocked" -> "已解锁"
    "locked" -> "已锁定"
    else -> "--"
}

private fun actuatorStateText(state: String?): String = when (state) {
    "opening" -> "正在展开"
    "closing" -> "正在收回"
    "open", "opened" -> "已展开"
    "closed" -> "已收回"
    "stopped" -> "已停止"
    else -> "--"
}

private fun Double?.formatted(suffix: String): String =
    this?.let { String.format(Locale.getDefault(), "%.1f%s", it, suffix) } ?: "--"

private enum class ConfirmAction(val title: String, val message: String) {
    UNLOCK("确认解锁", "将立即执行解锁，请确认现场安全。"),
    OPEN("确认展开", "设备将开始展开，请确认周边无人和障碍物。"),
    CLOSE("确认收回", "设备将开始收回，请确认周边无人和障碍物。")
}
