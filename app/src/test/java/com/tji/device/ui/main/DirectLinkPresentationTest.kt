package com.tji.device.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectLinkPresentationTest {
    @Test
    fun directHomeAlwaysShowsFourPhysicalLoadPorts() {
        assertEquals(listOf("LOAD1", "LOAD2", "LOAD3", "LOAD4"), directWiredLoadLabels())
    }

    @Test
    fun wirelessEmptyStateOnlyDescribesTheCurrentLinkState() {
        assertEquals("等待全志上报无线负载", directWirelessEmptyMessage(isConnected = true))
        assertEquals("连接数传设备后显示无线负载", directWirelessEmptyMessage(isConnected = false))
    }
}
