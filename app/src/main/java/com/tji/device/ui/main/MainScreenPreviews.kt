package com.tji.device.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot

@Preview(
    name = "平台首页",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
fun PreviewMainScreen() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = listOf(previewRuntimeDevice()),
        boundAccountDevices = listOf(
            BoundAccountDevice("LINK-001", "示例 Link", ProductType.FireBucket),
            BoundAccountDevice("SC-001", "光伏清洗 01", ProductType.SolarClean)
        ),
        activeProductPage = null,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(
    name = "平台首页-空状态",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
fun PreviewMainScreenEmpty() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = emptyList(),
        boundAccountDevices = emptyList(),
        activeProductPage = null,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(
    name = "消防吊桶设备列表",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
fun PreviewFireBucketProductPage() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = listOf(previewRuntimeDevice()),
        boundAccountDevices = listOf(
            BoundAccountDevice("LINK-001", "示例 Link", ProductType.FireBucket)
        ),
        activeProductPage = ProductType.FireBucket,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

private fun previewFireBucketLinkDevice(): FireBucketLinkDevice {
    val mockSwitch = Switch(
        serialNumber = "SW-001",
        deviceName = "示例水桶",
        deviceType = "Bucket",
        isOnline = true,
        currentAngle = 45.0,
        currentCurrent = 120.0,
        inputVoltage = 12.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 90.0,
        uptime = 3600
    )
    return FireBucketLinkDevice(
        event_type = "Preview",
        serial_number = "LINK-001",
        deviceName = "示例 Link",
        deviceType = "HydroLink",
        manufacturer = "TJI",
        deviceModel = "V3",
        isOnline = true,
        hwVersion = "1.0",
        swVersion = "1.0.0",
        uptime = 12345,
        deviceConfig = "{}",
        subDevices = listOf(mockSwitch),
        timestamp = "2025-01-01T00:00:00Z"
    )
}

private fun previewRuntimeDevice(): ProductDeviceRuntimeSnapshot {
    val link = previewFireBucketLinkDevice()
    return ProductDeviceRuntimeSnapshot(
        serialNumber = link.serial_number,
        name = link.deviceName,
        productType = link.productType,
        isOnline = link.isOnline,
        childCount = link.subDevices.size,
        payload = link
    )
}
