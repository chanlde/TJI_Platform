package com.tji.device.product.ota

/**
 * 只有同一轮 OTA 明确经历离线后再次上线，才触发一次设备信息刷新。
 */
class OtaRebootRefreshGate {
    private val offlineObserved = mutableSetOf<String>()
    private val refreshRequested = mutableSetOf<String>()

    fun shouldRefresh(
        serialNumber: String,
        waitingForReboot: Boolean,
        isOnline: Boolean
    ): Boolean {
        if (!waitingForReboot) {
            offlineObserved.remove(serialNumber)
            refreshRequested.remove(serialNumber)
            return false
        }
        if (!isOnline) {
            offlineObserved.add(serialNumber)
            return false
        }
        return serialNumber in offlineObserved && refreshRequested.add(serialNumber)
    }
}
