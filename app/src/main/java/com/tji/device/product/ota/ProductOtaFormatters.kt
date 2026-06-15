package com.tji.device.product.ota

import androidx.compose.ui.graphics.Color
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.network.data.OtaLatestResponse

fun otaStatusText(status: ProductOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "IDLE",
        "NONE" -> "空闲"
        "PREPARING",
        "STARTED" -> "准备升级"
        "ERASING" -> "正在擦除"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "TEST_DONE" -> "下载校验成功"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> "--"
    }
}

fun otaStatusColor(status: String): Color {
    return when (status.normalizedOtaStatus()) {
        "TEST_DONE",
        "SUCCESS" -> TjiOnline
        "FAILED",
        "ROLLBACK" -> TjiError
        "DOWNLOADING",
        "VERIFYING",
        "PREPARING",
        "ERASING",
        "STARTED",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> PayloadColors.Primary
        else -> PayloadColors.TextPrimary
    }
}

fun otaProgressTitle(status: ProductOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "TEST_DONE" -> "下载与校验成功，设备未升级"
        "SUCCESS" -> "升级完成"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "升级完成，等待设备重启"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "升级失败，已回滚"
        "PREPARING" -> "设备已响应，准备升级"
        "ERASING" -> "正在擦除升级分区"
        "STARTED" -> "设备已响应，准备升级"
        else -> "正在升级"
    }
}

fun otaStateText(status: String?): String {
    return when (status?.normalizedOtaStatus()) {
        null,
        "",
        "UNKNOWN" -> "--"
        "IDLE" -> "空闲"
        "NONE" -> "无"
        "PREPARING",
        "STARTED" -> "准备升级"
        "ERASING" -> "正在擦除"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "TEST_DONE" -> "下载校验成功"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> status
    }
}

fun ProductOtaStatus.shouldShowOtaProgress(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "IDLE",
        "NONE",
        "UNKNOWN" -> false
        else -> true
    }
}

fun ProductOtaStatus.isOtaBusy(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "STARTED",
        "PREPARING",
        "ERASING",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> true
        else -> false
    }
}

fun ProductOtaStatus.displayProgressPercent(): Int? {
    progress?.let { return it.coerceIn(0, 100) }
    val downloadedBytes = downloaded
    val totalBytes = total
    if (downloadedBytes != null && totalBytes != null && totalBytes > 0) {
        return ((downloadedBytes.coerceAtLeast(0) * 100.0) / totalBytes).toInt().coerceIn(0, 100)
    }
    return when (status.normalizedOtaStatus()) {
        "PREPARING",
        "STARTED",
        "ERASING" -> 0
        "VERIFYING" -> 90
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "TEST_DONE",
        "SUCCESS" -> 100
        else -> null
    }
}

fun ProductOtaStatus.toCompletedIfDeviceReachedLatest(
    deviceReachedLatest: Boolean
): ProductOtaStatus {
    if (!deviceReachedLatest) return this
    return when (status.normalizedOtaStatus()) {
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> copy(status = "SUCCESS", progress = 100, message = null, reason = null)
        else -> this
    }
}

fun isDeviceAtLatest(
    info: ProductDeviceInfo?,
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

fun otaUserMessage(raw: String): String? {
    val normalized = raw.normalizedOtaStatus()
    return if (normalized in hiddenOtaMachineStates) null else raw
}

fun String.normalizedOtaStatus(): String {
    return trim()
        .uppercase()
        .removePrefix("OTA_")
}

fun OtaLatestResponse.isStartable(): Boolean =
    !latestVersion.isNullOrBlank() &&
        !downloadUrl.isNullOrBlank() &&
        fileSize != null &&
        !sha256.isNullOrBlank()

private val hiddenOtaMachineStates = setOf(
    "IDLE",
    "NONE",
    "UNKNOWN",
    "PREPARING",
    "STARTED",
    "ERASING",
    "DOWNLOADING",
    "VERIFYING",
    "INSTALLING",
    "READY_TO_REBOOT",
    "PENDING_REBOOT",
    "REBOOTING",
    "TEST_DONE",
    "SUCCESS"
)
