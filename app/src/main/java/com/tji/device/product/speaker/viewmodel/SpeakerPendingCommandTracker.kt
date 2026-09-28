package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerAck
import kotlinx.coroutines.CompletableDeferred

/**
 * 统一管理尚未收到设备 ACK 的喊话器命令。
 *
 * ViewModel 会从主线程发起命令、从 IO 任务处理超时，并从 MQTT 收集协程完成 ACK；
 * 所有状态集中在这里同步访问，避免两张普通 MutableMap 并发读写和清理不一致。
 */
internal class SpeakerPendingCommandTracker {
    private val commands = mutableMapOf<String, PendingSpeakerCommand>()

    @Synchronized
    fun track(
        msgId: String,
        serialNumber: String,
        label: String,
        ackWaiter: CompletableDeferred<SpeakerAck>? = null
    ) {
        commands[msgId] = PendingSpeakerCommand(serialNumber, label, ackWaiter)
    }

    fun acknowledge(serialNumber: String, ack: SpeakerAck): String? {
        val pending = synchronized(this) {
            commands[ack.msgId]?.takeIf { it.serialNumber == serialNumber }?.also {
                commands.remove(ack.msgId)
            }
        } ?: return null
        pending.ackWaiter?.complete(ack)
        return pending.label
    }

    @Synchronized
    fun remove(msgId: String): Boolean =
        commands.remove(msgId) != null

    @Synchronized
    internal fun size(): Int = commands.size
}

private data class PendingSpeakerCommand(
    val serialNumber: String,
    val label: String,
    val ackWaiter: CompletableDeferred<SpeakerAck>?
)
