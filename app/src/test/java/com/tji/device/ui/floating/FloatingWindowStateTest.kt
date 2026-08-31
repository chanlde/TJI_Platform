package com.tji.device.ui.floating

import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.transport.DIRECT_FIRE_BUCKET_LINK_ID
import com.tji.device.product.firebucket.transport.DirectFireBucketState
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
            preferredProductType = ProductType.FireBucket
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
            preferredProductType = ProductType.FireBucket
        )

        val selected = state.selectedLink

        assertEquals("FB-001", selected?.serialNumber)
        assertEquals("消防 Link 01", selected?.name)
        assertEquals(ProductType.FireBucket, state.activeProductType)
    }

    @Test
    fun selectedLinkUsesProductTypeWhenDifferentProductsShareSerialNumber() {
        val sharedSerial = "DEVICE-001"
        val solarLink = link(sharedSerial, ProductType.SolarClean, "光伏设备")
        val fireBucketLink = link(sharedSerial, ProductType.FireBucket, "消防设备")
        val state = FloatingWindowUiState(
            links = listOf(solarLink, fireBucketLink),
            selectedLinkSerial = sharedSerial,
            preferredProductType = ProductType.FireBucket
        )

        assertEquals(fireBucketLink, state.selectedLink)
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

    @Test
    fun directModeUsesTheExistingFireBucketFloatingSummary() {
        val summary = DirectFireBucketState(
            isConnected = true,
            buckets = listOf(bucketSwitch(serial = "FB00A123", online = true))
        ).toFloatingLinkSummary()

        assertEquals(DIRECT_FIRE_BUCKET_LINK_ID, summary?.serialNumber)
        assertEquals(ProductType.FireBucket, summary?.productType)
        assertEquals("FB00A123", summary?.allSwitches?.single()?.serialNumber)
    }

    @Test
    fun directModeDoesNotInventAFloatingProductBeforeAStatusReport() {
        assertEquals(null, DirectFireBucketState(isConnected = true).toFloatingLinkSummary())
    }

    private fun bucketSwitch(
        serial: String,
        online: Boolean
    ): FireBucketSwitchState = FireBucketSwitchState(
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

    private fun link(
        serial: String,
        productType: ProductType,
        name: String
    ) = FloatingLinkSummary(
        serialNumber = serial,
        name = name,
        isOnline = true,
        productType = productType,
        onlineSwitches = emptyList(),
        offlineSwitches = emptyList()
    )
}
