package com.tji.device.product.speaker.viewmodel

/**
 * 在按下录音键时锁定目标设备，避免松手前切换页面后把音频发送到另一台设备。
 */
internal class SpeakerPttTargetLock {
    private var serialNumber: String? = null

    @Synchronized
    fun claim(serialNumber: String): Boolean {
        if (this.serialNumber != null) return false
        this.serialNumber = serialNumber
        return true
    }

    @Synchronized
    fun release(): String? =
        serialNumber.also { serialNumber = null }

    @Synchronized
    fun clearIfOwnedBy(serialNumber: String? = null): Boolean {
        if (serialNumber != null && this.serialNumber != serialNumber) return false
        this.serialNumber = null
        return true
    }

    @Synchronized
    fun clearAndGetIfOwnedBy(serialNumber: String? = null): String? {
        val owner = this.serialNumber ?: return null
        if (serialNumber != null && owner != serialNumber) return null
        this.serialNumber = null
        return owner
    }
}
