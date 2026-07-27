package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioRgbStatusPresentationTest {
    @Test
    fun matchingAckCompletesCurrentFeedback() {
        val status = resolveRadioRgbStatus(
            latestAck = ack(msgId = "rgb-1", ok = true, message = "rgb preview applied"),
            feedback = feedback(msgId = "rgb-1", text = "正在发送", pending = true)
        )

        assertEquals("灯语预览已应用", status.text)
        assertEquals(RadioRgbStatusTone.Positive, status.tone)
    }

    @Test
    fun staleAckCannotCompleteNewerPendingCommand() {
        val status = resolveRadioRgbStatus(
            latestAck = ack(msgId = "old", ok = true, message = "rgb config saved"),
            feedback = feedback(msgId = "new", text = "正在保存默认灯语", pending = true)
        )

        assertEquals("正在保存默认灯语", status.text)
        assertEquals(RadioRgbStatusTone.Pending, status.tone)
    }

    @Test
    fun matchingFailedAckUsesDeviceErrorPresentation() {
        val status = resolveRadioRgbStatus(
            latestAck = ack(msgId = "rgb-2", ok = false, code = 123),
            feedback = feedback(msgId = "rgb-2", text = "正在发送", pending = true)
        )

        assertEquals("设备正在升级，灯语控制暂不可用", status.text)
        assertEquals(RadioRgbStatusTone.Negative, status.tone)
    }

    @Test
    fun latestUnrelatedAckIsShownOnlyAsHistory() {
        val status = resolveRadioRgbStatus(
            latestAck = ack(msgId = "old", ok = true, message = "rgb config saved"),
            feedback = null
        )

        assertEquals("最近确认：默认灯语已保存", status.text)
        assertEquals(RadioRgbStatusTone.Neutral, status.tone)
    }

    private fun ack(
        msgId: String,
        ok: Boolean,
        code: Int = 0,
        message: String = ""
    ) = RadioRgbAck(
        msgId = msgId,
        ok = ok,
        code = code,
        message = message,
        timestamp = 1L
    )

    private fun feedback(
        msgId: String,
        text: String,
        pending: Boolean
    ) = RadioRgbCommandFeedback(
        serialNumber = "RADIO-1",
        msgId = msgId,
        text = text,
        pending = pending,
        success = null
    )
}
