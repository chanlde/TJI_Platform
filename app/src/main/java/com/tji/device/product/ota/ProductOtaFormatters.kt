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
        "RESERVED",
        "PUBLISHED",
        "ACCEPTED" -> "等待设备响应"
        "ERASING" -> "正在擦除"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "BOOT_VERIFY" -> "等待设备确认"
        "TEST_DONE" -> "下载校验成功"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "正在回滚"
        "ROLLED_BACK" -> "已回滚"
        "UNKNOWN" -> "结果待核对"
        else -> "--"
    }
}

fun otaStatusColor(status: String): Color {
    return when (status.normalizedOtaStatus()) {
        "TEST_DONE",
        "SUCCESS" -> TjiOnline
        "FAILED",
        "ROLLED_BACK" -> TjiError
        "DOWNLOADING",
        "VERIFYING",
        "PREPARING",
        "ERASING",
        "STARTED",
        "RESERVED",
        "PUBLISHED",
        "ACCEPTED",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "BOOT_VERIFY",
        "ROLLBACK",
        "UNKNOWN" -> PayloadColors.Primary
        else -> PayloadColors.TextPrimary
    }
}

fun otaProgressTitle(status: ProductOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "TEST_DONE" -> "下载与校验成功，设备未升级"
        "SUCCESS" -> "升级完成"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待设备重启并核对升级结果"
        "BOOT_VERIFY" -> "设备已上线，等待升级结果确认"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "升级失败，正在回滚"
        "ROLLED_BACK" -> "升级失败，已回滚"
        "UNKNOWN" -> "升级结果待核对"
        "RESERVED" -> "等待设备接收升级任务"
        "PUBLISHED" -> "指令已送达消息服务，等待设备确认"
        "ACCEPTED" -> "设备已接收升级任务"
        "PREPARING" -> "设备已响应，准备升级"
        "ERASING" -> "正在擦除升级分区"
        "STARTED" -> "设备已响应，准备升级"
        else -> "正在升级"
    }
}

fun otaStateText(status: String?): String {
    return when (status?.normalizedOtaStatus()) {
        null,
        "" -> "--"
        "UNKNOWN" -> "结果待核对"
        "IDLE" -> "空闲"
        "NONE" -> "无"
        "PREPARING",
        "STARTED" -> "准备升级"
        "RESERVED",
        "PUBLISHED",
        "ACCEPTED" -> "等待设备响应"
        "ERASING" -> "正在擦除"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "BOOT_VERIFY" -> "等待设备确认"
        "TEST_DONE" -> "下载校验成功"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "正在回滚"
        "ROLLED_BACK" -> "已回滚"
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
        "RESERVED",
        "PUBLISHED",
        "ACCEPTED",
        "PREPARING",
        "ERASING",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "BOOT_VERIFY",
        "ROLLBACK" -> true
        "UNKNOWN" -> !cmdId.isNullOrBlank()
        else -> false
    }
}

fun otaCandidateSummaryText(hasUpdate: Boolean, status: ProductOtaStatus?): String {
    return when (status?.status?.normalizedOtaStatus()) {
        "READY_TO_REBOOT", "PENDING_REBOOT", "REBOOTING", "BOOT_VERIFY", "ROLLBACK", "UNKNOWN" ->
            "升级结果待核对"
        "STARTED", "PREPARING", "ERASING", "DOWNLOADING", "VERIFYING", "INSTALLING" ->
            "升级进行中"
        "RESERVED", "PUBLISHED", "ACCEPTED" -> "升级任务待确认"
        else -> if (hasUpdate) "发现新版本" else "已是最新版本"
    }
}

fun ProductOtaStatus.displayProgressPercent(): Int? {
    val normalizedStatus = status.normalizedOtaStatus()
    val isAwaitingFinalResult = normalizedStatus in setOf(
        "READY_TO_REBOOT", "PENDING_REBOOT", "REBOOTING", "BOOT_VERIFY", "ROLLBACK", "UNKNOWN"
    )
    progress?.let { return it.coerceIn(0, if (isAwaitingFinalResult) 99 else 100) }
    val downloadedBytes = downloaded
    val totalBytes = total
    if (downloadedBytes != null && totalBytes != null && totalBytes > 0) {
        return ((downloadedBytes.coerceAtLeast(0) * 100.0) / totalBytes)
            .toInt()
            .coerceIn(0, if (isAwaitingFinalResult) 99 else 100)
    }
    return when (status.normalizedOtaStatus()) {
        "PREPARING",
        "STARTED",
        "ERASING" -> 0
        "VERIFYING" -> 90
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "BOOT_VERIFY",
        "ROLLBACK" -> 99
        "TEST_DONE",
        "SUCCESS" -> 100
        else -> null
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
    val message = raw.trim()
    if (message.isEmpty() || message.normalizedOtaStatus() in hiddenOtaMachineStates) return null
    if (message.any { it in '\u4e00'..'\u9fff' }) return message

    val lower = message.lowercase()
    return when {
        "size mismatch" in lower || "exceeds expected size" in lower ->
            "固件文件大小与发布信息不一致，请联系管理员"
        "sha256 mismatch" in lower || "hash mismatch" in lower ->
            "固件校验失败，请联系管理员"
        "hardware mismatch" in lower ->
            "固件与设备硬件不匹配，已停止升级"
        "not newer" in lower ->
            "目标版本不是更新版本，已停止升级"
        "origin is not allowed" in lower ->
            "固件下载地址不受信任，已停止升级"
        "http 404" in lower ->
            "固件文件不存在，请联系管理员"
        "fetch failed" in lower || "terminated" in lower || "download" in lower ->
            "固件下载失败，请检查网络后重试"
        "flash write" in lower ->
            "固件写入失败，请联系管理员"
        else ->
            "设备升级失败，请查看后台升级记录"
    }
}

fun String.normalizedOtaStatus(): String {
    return trim()
        .uppercase()
        .removePrefix("OTA_")
}

fun OtaLatestResponse.isStartable(): Boolean =
    !latestVersion.isNullOrBlank() &&
        !downloadUrl.isNullOrBlank() &&
        fileSize?.let(::isValidFirmwareSize) == true &&
        sha256?.trim()?.let(::isValidFirmwareSha256) == true

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
    "BOOT_VERIFY",
    "ROLLBACK",
    "ROLLED_BACK",
    "TEST_DONE",
    "SUCCESS"
)
