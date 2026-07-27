package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioSignalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTargetActionsTest {
    @Test
    fun blacklistTargetUsesEnforcementAction() {
        assertEquals(
            RadioTargetPrimaryAction.QueueForEnforcement,
            target(RadioListStatus.Blacklist).primaryAction()
        )
    }

    @Test
    fun nonBlacklistTargetUsesListAction() {
        assertEquals(
            RadioTargetPrimaryAction.AddToList,
            target(RadioListStatus.Whitelist).primaryAction()
        )
        assertEquals(
            RadioTargetPrimaryAction.AddToList,
            target(RadioListStatus.Unknown).primaryAction()
        )
    }

    @Test
    fun unavailableMessagesStateThatNothingWasChanged() {
        assertTrue(
            RadioTargetPrimaryAction.QueueForEnforcement
                .unavailableMessage("目标 A")
                .contains("未执行任何操作")
        )
        assertTrue(
            RadioTargetPrimaryAction.AddToList
                .unavailableMessage("目标 A")
                .contains("未修改名单状态")
        )
        assertTrue(blacklistUnavailableMessage("目标 A").contains("未修改名单状态"))
        assertTrue(enforcementRecordUnavailableMessage("目标 A").contains("未创建记录"))
    }

    private fun target(status: RadioListStatus) = RadioDetectionTarget(
        id = "target",
        name = "目标",
        type = "无人机",
        serialNumber = "target",
        listStatus = status,
        latitude = 0.0,
        longitude = 0.0,
        altitudeMeters = 0,
        speedMetersPerSecond = 0,
        headingDegrees = 0,
        frequencyLabel = "-",
        signalLevel = RadioSignalLevel.Medium,
        lastSeenAtMillis = 0L,
        pilotName = "",
        pilotLatitude = 0.0,
        pilotLongitude = 0.0,
        pilotDistanceText = "-",
        mapXPercent = 0.5f,
        mapYPercent = 0.5f
    )
}
