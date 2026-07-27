package com.tji.device.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperDeviceWebRouterTest {
    @Test
    fun hydroLinkNetworkRoutesToLinkDevicePageIgnoringCase() {
        assertEquals(
            "http://192.168.5.1/index.html",
            DeveloperDeviceWebRouter.urlForSsid("field-hYdRoLiNk-01")
        )
    }

    @Test
    fun unknownSsidRoutesToSwitchDevicePageWithoutCrashing() {
        assertEquals(
            "http://192.168.5.10/index.html",
            DeveloperDeviceWebRouter.urlForSsid(null)
        )
    }

    @Test
    fun unrelatedNetworkRoutesToSwitchDevicePage() {
        assertEquals(
            "http://192.168.5.10/index.html",
            DeveloperDeviceWebRouter.urlForSsid("office-wifi")
        )
    }
}
