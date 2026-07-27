package com.tji.device.product.speaker.ui.control

import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeakerTalkSectionTest {

    @Test
    fun formatsSavingRecordProgressAsCustomerFacingPercent() {
        val state = SpeakerTalkState(
            mode = SpeakerTalkMode.SavingRecord,
            progress = 0.367f
        )

        assertEquals("正在保存录音 36%", speakerTalkStatusText(state))
    }

    @Test
    fun formatsTtsStatusWithoutRecordingSaveWording() {
        val text = speakerTalkStatusText(SpeakerTalkState(mode = SpeakerTalkMode.Tts, progress = 0.4f))

        assertEquals("文字语音发送中", text)
        assertFalse(text.contains("保存录音"))
    }
}
