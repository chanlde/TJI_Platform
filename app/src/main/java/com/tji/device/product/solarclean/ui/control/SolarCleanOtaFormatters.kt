package com.tji.device.product.solarclean.ui.control

import androidx.compose.ui.graphics.Color
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.network.data.OtaLatestResponse

internal fun waterLevelText(value: Int): String {
    return when (value) {
        0 -> "低"
        1 -> "正常"
        2 -> "高"
        else -> value.toString()
    }
}

internal fun mqttStatusText(state: SolarCleanDeviceState?): String {
    return when (state?.mqttConnected) {
        true -> "正常"
        false -> state.mqttLastError?.let { "错误 $it" } ?: "断开"
        null -> "--"
    }
}

internal fun otaStatusText(status: SolarCleanOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "IDLE",
        "NONE" -> "空闲"
        "STARTED" -> "准备升级"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> "--"
    }
}

internal fun otaStatusColor(status: String): Color {
    return when (status.normalizedOtaStatus()) {
        "SUCCESS" -> TjiOnline
        "FAILED",
        "ROLLBACK" -> TjiError
        "DOWNLOADING",
        "VERIFYING",
        "STARTED",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> PayloadColors.Primary
        else -> PayloadColors.TextPrimary
    }
}

internal fun otaProgressTitle(status: SolarCleanOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "SUCCESS" -> "升级完成"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "升级完成，等待设备重启"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "升级失败，已回滚"
        "STARTED" -> "设备已响应，准备升级"
        else -> "正在升级"
    }
}

internal fun otaStateText(status: String?): String {
    return when (status?.normalizedOtaStatus()) {
        null,
        "",
        "UNKNOWN" -> "--"
        "IDLE" -> "空闲"
        "NONE" -> "无"
        "STARTED" -> "准备升级"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> status
    }
}

internal fun SolarCleanOtaStatus.shouldShowOtaProgress(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "IDLE",
        "NONE",
        "UNKNOWN" -> false
        else -> true
    }
}

internal fun SolarCleanOtaStatus.isOtaBusy(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "STARTED",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> true
        else -> false
    }
}

internal fun SolarCleanOtaStatus.toCompletedIfDeviceReachedLatest(
    deviceReachedLatest: Boolean
): SolarCleanOtaStatus {
    if (!deviceReachedLatest) return this
    return when (status.normalizedOtaStatus()) {
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> copy(status = "SUCCESS", progress = 100, message = null, reason = null)
        else -> this
    }
}

internal fun isDeviceAtLatest(
    info: SolarCleanDeviceInfo?,
    latest: OtaLatestResponse?
): Boolean {
    val currentInner = info?.firmwareInnerVersion
    val latestInner = latest?.innerVersion
    if (currentInner != null && latestInner != null) {
        return currentInner >= latestInner
    }
    val currentVersion = info?.firmwareVersion
    val latestVersion = latest?.latestVersion
    return !currentVersion.isNullOrBlank() &&
        !latestVersion.isNullOrBlank() &&
        currentVersion == latestVersion
}

internal fun otaUserMessage(raw: String): String? {
    val normalized = raw.normalizedOtaStatus()
    return if (normalized in hiddenOtaMachineStates) null else raw
}

internal fun String.normalizedOtaStatus(): String {
    return trim()
        .uppercase()
        .removePrefix("OTA_")
}

internal fun OtaLatestResponse.isStartable(): Boolean =
    !latestVersion.isNullOrBlank() &&
        !downloadUrl.isNullOrBlank() &&
        fileSize != null &&
        !sha256.isNullOrBlank()

private val hiddenOtaMachineStates = setOf(
    "IDLE",
    "NONE",
    "UNKNOWN",
    "STARTED",
    "DOWNLOADING",
    "VERIFYING",
    "INSTALLING",
    "READY_TO_REBOOT",
    "PENDING_REBOOT",
    "REBOOTING",
    "SUCCESS"
)
