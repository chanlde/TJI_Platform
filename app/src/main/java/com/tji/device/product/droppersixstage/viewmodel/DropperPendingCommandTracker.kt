package com.tji.device.product.droppersixstage.viewmodel

/**
 * 跟踪六段抛投尚未收到 ACK 的命令。
 *
 * 同一设备、同一执行目标只允许一个在途命令，避免循环测试或快速点击向设备堆积互相冲突的动作。
 */
internal class DropperPendingCommandTracker {
    private val commandsById = mutableMapOf<String, PendingDropperCommand>()
    private val activeResourceKeys = mutableSetOf<String>()

    fun start(
        msgId: String,
        serialNumber: String,
        resourceKeys: Set<String>,
        label: String,
        safetyEffect: DropperSafetyEffect = DropperSafetyEffect.None
    ): Boolean {
        if (resourceKeys.any(activeResourceKeys::contains)) return false
        activeResourceKeys += resourceKeys
        commandsById[msgId] = PendingDropperCommand(serialNumber, resourceKeys, label, safetyEffect)
        return true
    }

    fun complete(msgId: String, serialNumber: String? = null): PendingDropperCommand? {
        val pending = commandsById[msgId] ?: return null
        if (serialNumber != null && pending.serialNumber != serialNumber) return null
        commandsById.remove(msgId)
        activeResourceKeys -= pending.resourceKeys
        return pending
    }

    fun isPending(resourceKey: String): Boolean = resourceKey in activeResourceKeys
}

internal data class PendingDropperCommand(
    val serialNumber: String,
    val resourceKeys: Set<String>,
    val label: String,
    val safetyEffect: DropperSafetyEffect
)

internal enum class DropperSafetyEffect {
    None,
    Arm,
    Disarm
}
