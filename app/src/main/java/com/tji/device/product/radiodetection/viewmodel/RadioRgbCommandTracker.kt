package com.tji.device.product.radiodetection.viewmodel

import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback

/**
 * 管理 RGB 命令从发送、Broker 接收、设备 ACK 到超时的单次状态转换。
 * 晚到的旧命令回调会被消费，但不会覆盖当前命令反馈。
 */
class RadioRgbCommandTracker {
    private val pendingCommandIds = mutableSetOf<String>()
    private var currentCommandId: String? = null

    fun start(serialNumber: String, msgId: String, text: String): RadioRgbCommandFeedback {
        pendingCommandIds.add(msgId)
        currentCommandId = msgId
        return feedback(serialNumber, msgId, text, pending = true, success = null)
    }

    fun markPublished(msgId: String): RadioRgbCommandFeedback? =
        currentPendingFeedback(msgId, "指令已发送，等待设备确认")

    fun complete(ack: RadioRgbAck): RadioRgbCommandFeedback? {
        if (!pendingCommandIds.remove(ack.msgId) || currentCommandId != ack.msgId) return null
        val current = currentFeedback ?: return null
        return feedback(current.serialNumber, ack.msgId, ack.statusText, pending = false, success = ack.ok)
    }

    fun fail(msgId: String, text: String): RadioRgbCommandFeedback? {
        if (!pendingCommandIds.remove(msgId) || currentCommandId != msgId) return null
        val current = currentFeedback ?: return null
        return feedback(current.serialNumber, msgId, text, pending = false, success = false)
    }

    fun timeout(msgId: String): RadioRgbCommandFeedback? =
        fail(msgId, "设备未确认灯语指令")

    fun clearIfCurrent(msgId: String): Boolean {
        if (currentCommandId != msgId) return false
        currentCommandId = null
        currentFeedback = null
        return true
    }

    fun reset() {
        pendingCommandIds.clear()
        currentCommandId = null
        currentFeedback = null
    }

    private fun currentPendingFeedback(
        msgId: String,
        text: String
    ): RadioRgbCommandFeedback? {
        if (msgId !in pendingCommandIds || currentCommandId != msgId) return null
        val current = currentFeedback ?: return null
        return feedback(current.serialNumber, msgId, text, pending = true, success = null)
    }

    private var currentFeedback: RadioRgbCommandFeedback? = null

    private fun feedback(
        serialNumber: String,
        msgId: String,
        text: String,
        pending: Boolean,
        success: Boolean?
    ) = RadioRgbCommandFeedback(
        serialNumber = serialNumber,
        msgId = msgId,
        text = text,
        pending = pending,
        success = success
    ).also { currentFeedback = it }
}
