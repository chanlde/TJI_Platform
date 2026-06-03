package com.tji.device.ui.floating

import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.Switch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FloatingWindowStateTest {

    @Test
    fun selectedLinkUsesBoundDeviceFallbackWhenRuntimeIsMissing() {
        val state = FloatingWindowUiState(
            links = emptyList(),
            selectedLinkSerial = "3333333333333",
            selectedLinkName = "HydroLink_V3-3333333333333",
            preferredProductType = ProductType.FireBucket,
            isLoading = false
        )

        val selected = state.selectedLink

        assertNotNull(selected)
        assertEquals("3333333333333", selected?.serialNumber)
        assertEquals("HydroLink_V3-3333333333333", selected?.name)
        assertEquals(ProductType.FireBucket, selected?.productType)
        assertFalse(selected?.isOnline ?: true)
    }

    @Test
    fun selectedLinkDoesNotFallbackToAnotherProductWhenSerialIsSelected() {
        val solarLink = FloatingLinkSummary(
            serialNumber = "SC-001",
            name = "光伏清洗 01",
            isOnline = true,
            productType = ProductType.SolarClean,
            onlineSwitches = emptyList(),
            offlineSwitches = emptyList()
        )
        val state = FloatingWindowUiState(
            links = listOf(solarLink),
            selectedLinkSerial = "FB-001",
            selectedLinkName = "消防 Link 01",
            preferredProductType = ProductType.FireBucket,
            isLoading = false
        )

        val selected = state.selectedLink

        assertEquals("FB-001", selected?.serialNumber)
        assertEquals("消防 Link 01", selected?.name)
        assertEquals(ProductType.FireBucket, state.activeProductType)
    }

    @Test
    fun fireBucketAllSwitchesIncludesOnlineAndOfflineBuckets() {
        val link = FloatingLinkSummary.fromSwitches(
            serialNumber = "HydroLink_V3-7003DEF5",
            name = "HydroLink_V3-7003DEF5",
            isOnline = true,
            productType = ProductType.FireBucket,
            switches = listOf(
                bucketSwitch(serial = "BUCKET-ONLINE", online = true),
                bucketSwitch(serial = "BUCKET-OFFLINE", online = false)
            )
        )

        assertEquals(1, link.onlineSwitches.size)
        assertEquals(1, link.offlineSwitches.size)
        assertEquals(
            listOf("BUCKET-ONLINE", "BUCKET-OFFLINE"),
            link.allSwitches.map { it.serialNumber }
        )
    }

    private fun bucketSwitch(
        serial: String,
        online: Boolean
    ): Switch = Switch(
        serialNumber = serial,
        deviceName = serial,
        deviceType = "HydroSwitch",
        isOnline = online,
        currentAngle = 45.0,
        currentCurrent = 120.0,
        inputVoltage = 7.6,
        servoMinAngle = 0.0,
        servoMaxAngle = 90.0,
        uptime = 60
    )
}
