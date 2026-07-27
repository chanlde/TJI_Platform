package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerCommandOnlinePolicyTest {
    @Test
    fun cleanupAndStatusCommandsRemainAvailableWhileOffline() {
        assertFalse(SpeakerCommand.Stop("stop").requiresOnlineDevice())
        assertFalse(
            SpeakerCommand.SetMcuMicrophoneFeedback(
                "mcu-mic-off",
                enabled = false
            ).requiresOnlineDevice()
        )
        assertFalse(SpeakerCommand.GetStatus("status").requiresOnlineDevice())
    }

    @Test
    fun normalControlCommandRequiresOnlineDevice() {
        assertTrue(
            SpeakerCommand.SetVolume(
                msgId = "volume",
                volume = 50
            ).requiresOnlineDevice()
        )
    }
}
