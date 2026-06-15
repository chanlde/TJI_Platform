package com.tji.device.product.speaker.ui.control

import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeakerTalkSectionTest {

    @Test
    fun hidesInternalPacketCountFromCustomerFacingStatusText() {
        val state = SpeakerTalkState(
            mode = SpeakerTalkMode.Live,
            packetsSent = 42
        )

        val text = speakerTalkStatusText(state)

        assertEquals("正在实时喊话", text)
        assertFalse(text.contains("42"))
        assertFalse(text.contains("包"))
    }

    @Test
    fun formatsSavingRecordProgressAsCustomerFacingPercent() {
        val state = SpeakerTalkState(
            mode = SpeakerTalkMode.SavingRecord,
            progress = 0.367f,
            packetsSent = 99
        )

        assertEquals("正在保存录音 36%", speakerTalkStatusText(state))
    }
}
