package com.tji.device.product.ota

import com.tji.device.data.session.DeviceKey

/**
 * 防止同一设备在首次 OTA 状态上报前重复发送启动命令。
 *
 * MQTT 发布成功仅表示 Broker 已接收，不表示设备已经开始升级，因此不能在发布回调中释放。
 * 闸门按设备分别保存，只在发布失败、设备明确失败可重试或 ViewModel 销毁时释放；
 * 单纯切换页面/设备不能让同一升级被重复发送。
 */
internal class ProductOtaStartGuard {
    private val activeStarts = mutableMapOf<DeviceKey, String>()

    @Synchronized
    fun reserve(deviceKey: DeviceKey, msgId: String): OtaStartReservation {
        val activeMsgId = activeStarts[deviceKey]
        if (activeMsgId != null) {
            return OtaStartReservation.AlreadyReserved(activeMsgId)
        }
        activeStarts[deviceKey] = msgId
        return OtaStartReservation.Accepted
    }

    @Synchronized
    fun isActive(deviceKey: DeviceKey, msgId: String): Boolean =
        activeStarts[deviceKey] == msgId

    @Synchronized
    fun releaseAfterPublishFailure(deviceKey: DeviceKey, msgId: String): Boolean {
        if (!isActive(deviceKey, msgId)) return false
        activeStarts.remove(deviceKey)
        return true
    }

    @Synchronized
    fun releaseIfRetryAllowed(deviceKey: DeviceKey, status: ProductOtaStatus?): Boolean {
        val activeMsgId = activeStarts[deviceKey] ?: return false
        if (status?.allowsRetry() != true) return false
        if (!status.cmdId.isNullOrBlank() && status.cmdId != activeMsgId) return false
        activeStarts.remove(deviceKey)
        return true
    }

    @Synchronized
    fun reset() {
        activeStarts.clear()
    }
}

internal sealed interface OtaStartReservation {
    data object Accepted : OtaStartReservation
    data class AlreadyReserved(val activeMsgId: String) : OtaStartReservation
}

private fun ProductOtaStatus.allowsRetry(): Boolean =
    when (status.normalizedOtaStatus()) {
        "FAILED",
        "ROLLBACK" -> true
        else -> false
    }
