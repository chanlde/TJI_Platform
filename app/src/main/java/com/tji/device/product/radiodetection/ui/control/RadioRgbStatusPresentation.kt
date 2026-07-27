package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbCommandFeedback

internal enum class RadioRgbStatusTone {
    Positive,
    Negative,
    Pending,
    Neutral
}

internal data class RadioRgbStatusPresentation(
    val text: String,
    val tone: RadioRgbStatusTone
)

/**
 * 合并设备 ACK 与本地发送状态。只有 msgId 匹配时，ACK 才能完成当前反馈。
 */
internal fun resolveRadioRgbStatus(
    latestAck: RadioRgbAck?,
    feedback: RadioRgbCommandFeedback?
): RadioRgbStatusPresentation {
    val matchingAck = latestAck?.takeIf { ack ->
        feedback?.msgId == ack.msgId
    }
    return when {
        matchingAck != null -> RadioRgbStatusPresentation(
            text = matchingAck.statusText,
            tone = if (matchingAck.ok) {
                RadioRgbStatusTone.Positive
            } else {
                RadioRgbStatusTone.Negative
            }
        )

        feedback != null -> RadioRgbStatusPresentation(
            text = feedback.text,
            tone = when {
                feedback.success == false -> RadioRgbStatusTone.Negative
                feedback.pending -> RadioRgbStatusTone.Pending
                else -> RadioRgbStatusTone.Neutral
            }
        )

        latestAck != null -> RadioRgbStatusPresentation(
            text = "最近确认：${latestAck.statusText}",
            tone = RadioRgbStatusTone.Neutral
        )

        else -> RadioRgbStatusPresentation(
            text = "点击模式、颜色或数值会立即预览；保存默认才会写入设备配置。",
            tone = RadioRgbStatusTone.Neutral
        )
    }
}
