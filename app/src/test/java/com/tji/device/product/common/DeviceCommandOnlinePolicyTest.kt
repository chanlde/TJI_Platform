package com.tji.device.product.common

import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import com.tji.device.product.droppersixstage.viewmodel.requiresOnlineDevice as dropperRequiresOnline
import com.tji.device.product.glassbreaker.model.GlassBreakerCommand
import com.tji.device.product.glassbreaker.viewmodel.requiresOnlineDevice as glassRequiresOnline
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.viewmodel.requiresOnlineDevice as solarRequiresOnline
import com.tji.device.product.speaker.model.SpeakerCommand
import com.tji.device.product.speaker.viewmodel.requiresOnlineDevice as speakerRequiresOnline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommandOnlinePolicyTest {
    @Test
    fun diagnosticQueriesCanProbeAnOfflineDevice() {
        assertFalse(DropperSixStageCommand.Ping("dropper-ping").dropperRequiresOnline())
        assertFalse(GlassBreakerCommand.GetDeviceInfo("glass-info").glassRequiresOnline())
        assertFalse(SolarCleanCommand.Ping("solar-ping").solarRequiresOnline())
        assertFalse(SolarCleanCommand.GetDeviceInfo("solar-info").solarRequiresOnline())
        assertFalse(SpeakerCommand.GetStatus("speaker-status").speakerRequiresOnline())
    }

    @Test
    fun physicalControlsRequireAnOnlineDevice() {
        assertTrue(DropperSixStageCommand.AllStages("dropper-all", open = true).dropperRequiresOnline())
        assertTrue(GlassBreakerCommand.Unlock("glass-unlock").glassRequiresOnline())
        assertTrue(SolarCleanCommand.PumpSwitch("solar-pump", on = true).solarRequiresOnline())
        assertTrue(SpeakerCommand.SetVolume("speaker-volume", volume = 50).speakerRequiresOnline())
    }

    @Test
    fun safetyStopCommandsRemainAvailableOffline() {
        assertFalse(SpeakerCommand.Stop("speaker-stop").speakerRequiresOnline())
        assertFalse(
            SpeakerCommand.SetMcuMicrophoneFeedback(
                "speaker-mcu-mic-off",
                enabled = false
            ).speakerRequiresOnline()
        )
    }
}
