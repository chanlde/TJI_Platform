package com.tji.device.product.glassbreaker.model

import com.tji.device.product.runtime.ProductRuntimePayload

const val GLASS_BREAKER_CHANNEL_COUNT = 4

data class GlassBreakerState(
    val serialNumber: String,
    val name: String? = null,
    val isOnline: Boolean = false,
    val lockState: String = GlassBreakerLockState.Locked,
    val selectedChannel: Int? = null,
    val laserEnabled: Boolean = false,
    val fireState: String = GlassBreakerFireState.Idle,
    val armRemainingMs: Long? = null,
    val batteryPercent: Int? = null,
    val hardwareVersion: String? = null,
    val firmwareVersion: String? = null,
    val firmwareInnerVersion: Int? = null,
    val lastAck: GlassBreakerAck? = null,
    val lastOtaAck: GlassBreakerAck? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload {
    val isUnlocked: Boolean
        get() = lockState.equals(GlassBreakerLockState.Unlocked, ignoreCase = true)
}

data class GlassBreakerAck(
    val msgId: String,
    val ok: Boolean,
    val code: Int? = null,
    val message: String? = null,
    val ofCmd: Int? = null,
    val ofType: String? = null,
    val lockState: String? = null,
    val selectedChannel: Int? = null,
    val laserEnabled: Boolean? = null,
    val fireState: String? = null,
    val timestamp: Long? = null
) {
    fun userFacingMessage(): String {
        val raw = message?.trim().orEmpty()
        val normalized = raw.lowercase()
        return when {
            normalized.isBlank() -> code?.let { "设备拒绝命令（$it）" } ?: "设备拒绝命令"
            normalized == "accepted" || normalized == "ok" -> "命令已执行"
            "unsupported or rejected" in normalized -> "设备拒绝命令"
            "deviceid mismatch" in normalized -> "设备编号不匹配"
            "select channel first" in normalized -> "请先选择通道"
            "channel mismatch" in normalized -> "击发通道与已选通道不一致"
            "invalid channel" in normalized -> "通道参数无效"
            "not armed" in normalized -> "请先解锁设备"
            "channel output active" in normalized -> "通道输出未结束"
            raw.any { it.code > 127 } -> raw
            else -> code?.let { "设备拒绝命令（$it）" } ?: "设备拒绝命令"
        }
    }
}

object GlassBreakerLockState {
    const val Locked = "locked"
    const val Unlocked = "unlocked"
}

object GlassBreakerFireState {
    const val Idle = "idle"
}

object GlassBreakerCommandCode {
    const val GET_DEVICE_INFO = 1
    const val UNLOCK = 10
    const val LOCK = 11
    const val FIRE_CHANNEL = 12
    const val LASER_SWITCH = 13
    const val SELECT_CHANNEL = 14
}

sealed interface GlassBreakerCommand {
    val msgId: String

    data class GetDeviceInfo(override val msgId: String) : GlassBreakerCommand
    data class Unlock(override val msgId: String) : GlassBreakerCommand
    data class Lock(override val msgId: String) : GlassBreakerCommand
    data class SelectChannel(override val msgId: String, val channel: Int) : GlassBreakerCommand
    data class FireChannel(override val msgId: String, val channel: Int) : GlassBreakerCommand
    data class LaserSwitch(override val msgId: String, val on: Boolean) : GlassBreakerCommand
}
