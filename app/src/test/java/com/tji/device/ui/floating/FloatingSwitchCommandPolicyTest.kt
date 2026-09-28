package com.tji.device.ui.floating

import com.tji.device.data.model.ProductType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingSwitchCommandPolicyTest {
    @Test
    fun onlineLinkCanProbeAReportedOfflineBucket() {
        assertTrue(canToggleFloatingSwitch(link(online = true), switch(online = true)))
        assertTrue(canToggleFloatingSwitch(link(online = true), switch(online = false)))
        assertFalse(canToggleFloatingSwitch(link(online = false), switch(online = true)))
        assertFalse(canToggleFloatingSwitch(null, switch(online = true)))
    }

    private fun link(online: Boolean) = FloatingLinkSummary(
        serialNumber = "LINK-1",
        name = "Link",
        isOnline = online,
        productType = ProductType.FireBucket,
        onlineSwitches = emptyList(),
        offlineSwitches = emptyList()
    )

    private fun switch(online: Boolean) = FloatingSwitchSummary(
        serialNumber = "SWITCH-1",
        name = "Switch",
        isOnline = online,
        currentAngle = 0
    )
}
