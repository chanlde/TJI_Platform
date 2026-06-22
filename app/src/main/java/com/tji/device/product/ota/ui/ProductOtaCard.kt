package com.tji.device.product.ota.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.product.ota.ProductDeviceInfo
import com.tji.device.product.ota.ProductOtaCheckState
import com.tji.device.product.ota.ProductOtaCommandFeedback
import com.tji.device.product.ota.ProductOtaCommandFeedbackStatus
import com.tji.device.product.ota.ProductOtaStatus
import com.tji.device.product.ota.displayProgressPercent
import com.tji.device.product.ota.isDeviceAtLatest
import com.tji.device.product.ota.isOtaBusy
import com.tji.device.product.ota.isStartable
import com.tji.device.product.ota.normalizedOtaStatus
import com.tji.device.product.ota.otaProgressTitle
import com.tji.device.product.ota.otaStatusColor
import com.tji.device.product.ota.otaStatusText
import com.tji.device.product.ota.otaUserMessage
import com.tji.device.product.ota.shouldShowOtaProgress
import com.tji.device.product.ota.toCompletedIfDeviceReachedLatest
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiWarning
import com.tji.network.data.OtaLatestResponse
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val OTA_SUCCESS_NOTICE_VISIBLE_MS = 5_000L
private const val OTA_PROGRESS_ANIMATION_MS = 700
private const val OTA_PROGRESS_TICK_MS = 300L
private const val OTA_PROGRESS_OPTIMISTIC_STEP = 0.004f

@Composable
fun ProductOtaCard(
    deviceInfo: ProductDeviceInfo?,
    otaStatus: ProductOtaStatus?,
    otaCheckState: ProductOtaCheckState,
    commandFeedback: ProductOtaCommandFeedback,
    enabled: Boolean,
    onRefreshDeviceInfo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartOta: () -> Unit
) {
    val latest = otaCheckState.latest
    val deviceReachedLatest = isDeviceAtLatest(deviceInfo, latest)
    val effectiveOtaStatus = otaStatus?.toCompletedIfDeviceReachedLatest(deviceReachedLatest)
    val successNoticeKey = "${latest?.innerVersion}:${deviceInfo?.firmwareInnerVersion}"
    var showSuccessNotice by remember(successNoticeKey) { mutableStateOf(true) }
    LaunchedEffect(successNoticeKey, effectiveOtaStatus?.status) {
        if (effectiveOtaStatus?.status?.normalizedOtaStatus() == "SUCCESS") {
            showSuccessNotice = true
            kotlinx.coroutines.delay(OTA_SUCCESS_NOTICE_VISIBLE_MS)
            showSuccessNotice = false
        }
    }
    val hasUpdate = otaCheckState.hasUpdate && !deviceReachedLatest
    val isOtaBusy = effectiveOtaStatus?.isOtaBusy() == true
    val latestStartable = latest?.isStartable() == true
    val displayOtaStatus = effectiveOtaStatus?.takeUnless {
        it.status.normalizedOtaStatus() == "SUCCESS" && !showSuccessNotice
    }
    TjiSectionCard(
        title = "固件升级",
        trailing = { ProductOtaFeedbackBadge(commandFeedback) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactInfo("当前版本", deviceInfo?.firmwareVersion ?: "--")
            CompactInfo("内部版本", deviceInfo?.firmwareInnerVersion?.toString() ?: "--")
            CompactInfo("硬件版本", deviceInfo?.hardwareVersion ?: "--")
            CompactInfo("升级状态", displayOtaStatus?.let { otaStatusText(it) } ?: "空闲")
        }
        latest?.let {
            OtaUpdateInfo(
                latest = it,
                hasUpdate = hasUpdate
            )
        }
        displayOtaStatus?.takeIf { it.shouldShowOtaProgress() }?.let { OtaProgressLine(it) }
        otaCheckState.errorMessage?.let {
            Text(text = it, fontSize = 12.sp, color = TjiError)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjiActionButton(
                text = "刷新信息",
                enabled = enabled,
                color = PayloadColors.Primary,
                onClick = onRefreshDeviceInfo
            )
            TjiActionButton(
                text = if (otaCheckState.isChecking) "检测中" else "检测更新",
                enabled = enabled && !otaCheckState.isChecking,
                color = PayloadColors.Primary,
                onClick = onCheckUpdate
            )
            TjiActionButton(
                text = "立即升级",
                enabled = enabled && !isOtaBusy && hasUpdate && latestStartable,
                color = TjiWarning,
                onClick = onStartOta
            )
        }
    }
}

@Composable
private fun ProductOtaFeedbackBadge(feedback: ProductOtaCommandFeedback) {
    val text = feedback.text ?: return
    val color = when (feedback.status) {
        ProductOtaCommandFeedbackStatus.Pending -> PayloadColors.TextSecondary
        ProductOtaCommandFeedbackStatus.Success -> TjiOnline
        ProductOtaCommandFeedbackStatus.Failed -> TjiError
        ProductOtaCommandFeedbackStatus.Idle -> return
    }
    TjiFeedbackBadge(text = text, color = color)
}

@Composable
private fun CompactInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 11.sp, color = PayloadColors.TextMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OtaUpdateInfo(latest: OtaLatestResponse, hasUpdate: Boolean) {
    val color = if (hasUpdate) TjiWarning else TjiOnline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PayloadColors.SurfaceSoft, RoundedCornerShape(PayloadDimens.ControlRadius))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (hasUpdate) "发现新版本" else "已是最新版本",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = latest.innerVersion?.let { "内部 $it" } ?: latest.latestVersion ?: "--",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextPrimary
            )
        }
        latest.releaseNote?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 12.sp, color = PayloadColors.TextMuted)
        }
    }
}

@Composable
private fun OtaProgressLine(status: ProductOtaStatus) {
    val progress = status.displayProgressPercent()
    val progressAnim = rememberOtaProgressAnimation(status, progress)
    val displayProgress = (progressAnim.value * 100).roundToInt().coerceIn(0, 100)
    val reasonText = status.message ?: status.reason
    val color = otaStatusColor(status.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PayloadColors.SurfaceSoft, RoundedCornerShape(PayloadDimens.ControlRadius))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = otaProgressTitle(status),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = "$displayProgress%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextSecondary
            )
        }
        LinearProgressIndicator(
            progress = { progressAnim.value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = PayloadColors.PrimarySoft
        )
        reasonText?.takeIf { it.isNotBlank() }?.let { rawMessage ->
            otaUserMessage(rawMessage)?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = PayloadColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun rememberOtaProgressAnimation(
    status: ProductOtaStatus,
    rawProgress: Int?
): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val statusKey = "${status.targetVersion}:${status.targetInnerVersion}:${status.status.normalizedOtaStatus()}"
    val initialProgress = (rawProgress ?: status.displayProgressPercent() ?: 0) / 100f
    val progressAnim = remember(statusKey) { Animatable(initialProgress.coerceIn(0f, 1f)) }
    val normalizedStatus = status.status.normalizedOtaStatus()
    val isBusy = status.isOtaBusy()
    val terminalTarget = when (normalizedStatus) {
        "TEST_DONE",
        "SUCCESS" -> 1f
        else -> null
    }

    LaunchedEffect(rawProgress, normalizedStatus) {
        val target = terminalTarget ?: rawProgress?.let { it / 100f }
        val nonDecreasingTarget = target?.coerceIn(progressAnim.value, 1f)
        if (nonDecreasingTarget != null && nonDecreasingTarget > progressAnim.value) {
            progressAnim.animateTo(
                targetValue = nonDecreasingTarget,
                animationSpec = tween(
                    durationMillis = OTA_PROGRESS_ANIMATION_MS,
                    easing = LinearEasing
                )
            )
        }
    }

    LaunchedEffect(statusKey, isBusy) {
        while (isBusy) {
            delay(OTA_PROGRESS_TICK_MS)
            val cap = optimisticProgressCap(normalizedStatus)
            val next = (progressAnim.value + OTA_PROGRESS_OPTIMISTIC_STEP).coerceAtMost(cap)
            if (next > progressAnim.value) {
                progressAnim.animateTo(
                    targetValue = next,
                    animationSpec = tween(
                        durationMillis = OTA_PROGRESS_TICK_MS.toInt(),
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    return progressAnim
}

private fun optimisticProgressCap(normalizedStatus: String): Float {
    return when (normalizedStatus) {
        "VERIFYING" -> 0.98f
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> 1f
        else -> 0.95f
    }
}
