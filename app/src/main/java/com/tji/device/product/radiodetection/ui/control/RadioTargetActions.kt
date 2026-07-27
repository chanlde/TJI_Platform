package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioListStatus

internal enum class RadioTargetPrimaryAction(val label: String) {
    QueueForEnforcement("处置"),
    AddToList("加入名单")
}

internal fun RadioDetectionTarget.primaryAction(): RadioTargetPrimaryAction =
    if (listStatus == RadioListStatus.Blacklist) {
        RadioTargetPrimaryAction.QueueForEnforcement
    } else {
        RadioTargetPrimaryAction.AddToList
    }

internal fun RadioTargetPrimaryAction.unavailableMessage(targetName: String): String = when (this) {
    RadioTargetPrimaryAction.QueueForEnforcement ->
        "$targetName 暂不能加入处置队列：设备协议尚未提供处置接口，本次未执行任何操作。"

    RadioTargetPrimaryAction.AddToList ->
        "$targetName 暂不能加入名单：名单管理接口尚未接入，本次未修改名单状态。"
}

internal fun blacklistUnavailableMessage(targetName: String): String =
    "$targetName 暂不能加入黑名单：名单管理接口尚未接入，本次未修改名单状态。"

internal fun enforcementRecordUnavailableMessage(targetName: String): String =
    "$targetName 暂不能生成执法记录：执法记录接口尚未接入，本次未创建记录。"
