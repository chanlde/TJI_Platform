package com.tji.device.product.glassbreaker.viewmodel

import com.tji.device.product.glassbreaker.model.GlassBreakerCommand

/**
 * 跟踪破窗弹尚未收到 ACK 的控制命令。
 *
 * 冲突键按“设备 + 命令族”划分，而不是按具体参数划分。例如选择通道 1 和通道 2
 * 都属于同一个通道选择族，必须等前一条完成，避免 ACK 乱序后选中错误通道。
 */
internal class GlassBreakerPendingCommandTracker {
    private val commandsById = mutableMapOf<String, PendingGlassBreakerCommand>()
    private val activeConflictKeys = mutableSetOf<String>()

    fun start(
        msgId: String,
        serialNumber: String,
        conflictKey: String,
        label: String
    ): Boolean {
        if (!activeConflictKeys.add(conflictKey)) return false
        commandsById[msgId] = PendingGlassBreakerCommand(
            serialNumber = serialNumber,
            conflictKey = conflictKey,
            label = label
        )
        return true
    }

    fun complete(msgId: String, serialNumber: String? = null): PendingGlassBreakerCommand? {
        val pending = commandsById[msgId] ?: return null
        if (serialNumber != null && pending.serialNumber != serialNumber) return null
        commandsById.remove(msgId)
        activeConflictKeys.remove(pending.conflictKey)
        return pending
    }

    fun isPending(conflictKey: String): Boolean = conflictKey in activeConflictKeys
}

internal data class PendingGlassBreakerCommand(
    val serialNumber: String,
    val conflictKey: String,
    val label: String
)

internal fun GlassBreakerCommand.conflictKey(serialNumber: String): String = when (this) {
    is GlassBreakerCommand.GetDeviceInfo -> "$serialNumber:device-info"
    is GlassBreakerCommand.Unlock,
    is GlassBreakerCommand.Lock -> "$serialNumber:safety-lock"
    is GlassBreakerCommand.SelectChannel -> "$serialNumber:channel-selection"
    is GlassBreakerCommand.FireChannel -> "$serialNumber:fire"
    is GlassBreakerCommand.LaserSwitch -> "$serialNumber:laser"
}
