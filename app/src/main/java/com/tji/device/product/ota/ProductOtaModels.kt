package com.tji.device.product.ota

import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.runtime.ProductRuntimePayload
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.speaker.model.SpeakerDeviceState

data class ProductDeviceInfo(
    val hardwareVersion: String? = null,
    val firmwareVersion: String? = null,
    val firmwareInnerVersion: Int? = null,
    val otaStatus: String? = null,
    val lastOtaResult: String? = null,
    val lastFailReason: String? = null,
    val batteryPercent: Int? = null,
    val timestamp: Long? = null
)

data class ProductOtaStatus(
    val status: String,
    val cmdId: String? = null,
    val seq: Long? = null,
    val progress: Int? = null,
    val targetVersion: String? = null,
    val targetInnerVersion: Int? = null,
    val firmwareVersion: String? = null,
    val reason: String? = null,
    val message: String? = null,
    val downloaded: Long? = null,
    val total: Long? = null,
    val timestamp: Long? = null
)

data class ProductOtaPackage(
    val targetVersion: String,
    val downloadUrl: String,
    val fileSize: Long,
    val sha256: String,
    val targetInnerVersion: Int? = null,
    val hardwareVersion: String? = null,
    val signature: String? = null
)

data class ProductOtaCheckState(
    val isChecking: Boolean = false,
    val latest: com.tji.network.data.OtaLatestResponse? = null,
    val hasUpdate: Boolean = false,
    val errorMessage: String? = null
)

data class ProductOtaCommandFeedback(
    val msgId: String? = null,
    val status: ProductOtaCommandFeedbackStatus = ProductOtaCommandFeedbackStatus.Idle,
    val text: String? = null
)

enum class ProductOtaCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed
}

fun ProductRuntimePayload?.toProductDeviceInfo(): ProductDeviceInfo? {
    return when (this) {
        is SolarCleanDeviceState -> {
            val info = deviceInfo
            ProductDeviceInfo(
                hardwareVersion = info?.hardwareVersion,
                firmwareVersion = info?.firmwareVersion,
                firmwareInnerVersion = info?.firmwareInnerVersion,
                otaStatus = info?.otaStatus,
                lastOtaResult = info?.lastOtaResult,
                lastFailReason = info?.lastFailReason,
                batteryPercent = info?.batteryPercent ?: batteryPercent?.toInt(),
                timestamp = info?.timestamp ?: timestamp
            )
        }
        is FireBucketLinkDevice -> ProductDeviceInfo(
            hardwareVersion = hwVersion.takeIf { it.isNotBlank() },
            firmwareVersion = swVersion.takeIf { it.isNotBlank() }
        )
        is DropperSixStageState -> ProductDeviceInfo(
            firmwareVersion = firmwareVersion,
            batteryPercent = batteryPercent,
            timestamp = timestamp
        )
        is SpeakerDeviceState -> ProductDeviceInfo(
            timestamp = timestamp
        )
        else -> null
    }
}

fun ProductRuntimePayload?.toProductOtaStatus(): ProductOtaStatus? {
    return when (this) {
        is SolarCleanDeviceState -> otaStatus?.let {
            ProductOtaStatus(
                status = it.status,
                progress = it.progress,
                targetVersion = it.targetVersion,
                targetInnerVersion = it.targetInnerVersion,
                firmwareVersion = it.firmwareVersion ?: it.currentVersion,
                reason = it.reason,
                message = it.message,
                downloaded = it.downloaded,
                total = it.total,
                timestamp = it.timestamp
            )
        }
        else -> null
    }
}
