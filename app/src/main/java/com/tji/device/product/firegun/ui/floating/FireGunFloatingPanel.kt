package com.tji.device.product.firegun.ui.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.di.AppContainer
import com.tji.device.product.firegun.control.FireGunControlState
import com.tji.device.product.firegun.model.FireGunLinkState
import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.viewmodel.FireGunControlViewModel
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.ui.theme.PayloadColors

@Composable
fun FireGunFloatingPanel(
    link: FloatingLinkSummary?,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val viewModel: FireGunControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.fireGunControlViewModelFactory)
    }
    val state by viewModel?.state?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(FireGunControlState()) }
    }
    val linkStatus by viewModel?.linkStatus?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf<FireGunLinkState?>(null) }
    }
    val mqttConnected by viewModel?.mqttConnected?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(false) }
    }
    val serialNumber = link?.serialNumber

    DisposableEffect(serialNumber, viewModel) {
        if (!serialNumber.isNullOrBlank()) viewModel?.bindDevice(serialNumber)
        onDispose {
            if (!serialNumber.isNullOrBlank()) viewModel?.unbindDevice(serialNumber)
        }
    }

    FireGunFloatingPanelContent(
        state = state,
        linkStatus = linkStatus,
        canControl = !serialNumber.isNullOrBlank() && mqttConnected,
        onUnlock = { serialNumber?.let { viewModel?.unlock(it) } },
        onOpen = { serialNumber?.let { viewModel?.actuator(it, FireGunAction.OPEN) } },
        onClose = { serialNumber?.let { viewModel?.actuator(it, FireGunAction.CLOSE) } },
        onStop = { serialNumber?.let { viewModel?.actuator(it, FireGunAction.STOP) } },
        modifier = modifier
    )
}

@Composable
internal fun FireGunFloatingPanelContent(
    state: FireGunControlState,
    linkStatus: FireGunLinkState?,
    canControl: Boolean,
    onUnlock: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actuatorBusy = state.pendingActuator != null
    val unlockBusy = state.pendingUnlock != null
    val feedback = when {
        actuatorBusy -> state.actuatorFeedback
        unlockBusy -> state.unlockFeedback
        !state.actuatorFeedback.isNullOrBlank() -> state.actuatorFeedback
        else -> state.unlockFeedback
    }
    val feedbackColor = when {
        actuatorBusy || unlockBusy -> PayloadColors.Primary
        state.lastCommandSucceeded == true -> PayloadColors.Success
        state.lastCommandSucceeded == false -> PayloadColors.Danger
        else -> PayloadColors.TextMuted
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjiOnlineStatus(isOnline = linkStatus?.isOnline == true, pill = true)
            Text(
                text = "${lockText(state.lockState)} · ${positionText(state.actuatorState)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        if (!feedback.isNullOrBlank()) {
            TjiFeedbackBadge(text = feedback, color = feedbackColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjiActionButton(
                text = if (unlockBusy) "解锁中" else "解锁",
                enabled = canControl && !unlockBusy,
                color = PayloadColors.Warning,
                onClick = onUnlock,
                modifier = Modifier.weight(1f)
            )
            TjiActionButton(
                text = "停止",
                enabled = canControl,
                color = PayloadColors.Danger,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjiActionButton(
                text = if (state.pendingActuator?.action == FireGunAction.OPEN) "展开中" else "展开",
                enabled = canControl && !actuatorBusy,
                color = PayloadColors.Primary,
                onClick = onOpen,
                modifier = Modifier.weight(1f)
            )
            TjiActionButton(
                text = if (state.pendingActuator?.action == FireGunAction.CLOSE) "收回中" else "收回",
                enabled = canControl && !actuatorBusy,
                color = PayloadColors.Primary,
                onClick = onClose,
                modifier = Modifier.weight(1f)
            )
        }
        if (!canControl) {
            Text(
                text = "正在连接云端",
                style = MaterialTheme.typography.labelSmall,
                color = PayloadColors.TextMuted
            )
        }
    }
}

private fun lockText(state: String): String = when (state) {
    "unlocking" -> "正在解锁"
    "unlocked" -> "已解锁"
    else -> "已锁定"
}

private fun positionText(state: String): String = when (state) {
    "opening" -> "正在展开"
    "closing" -> "正在收回"
    "open", "opened" -> "已展开"
    "closed" -> "已收回"
    else -> "已停止"
}
