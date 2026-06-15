package com.tji.device.product.solarclean.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedbackStatus
import com.tji.device.ui.theme.BucketTheme

internal fun previewSolarCleanState(serialNumber: String): SolarCleanDeviceState {
    return SolarCleanDeviceState(
        serialNumber = serialNumber,
        isOnline = true,
        altitudeMeters = 18.6,
        speedMetersPerSecond = 2.4,
        satelliteCount = 18,
        yawDegrees = 126.0,
        pitchDegrees = 3.0,
        rollDegrees = -2.0,
        latitude = 22.543096,
        longitude = 114.057865,
        mqttConnected = true,
        mqttLastError = 0,
        waterLevel = 1,
        deviceInfo = SolarCleanDeviceInfo(
            hardwareVersion = "HW-A",
            firmwareVersion = "1.0.3",
            firmwareInnerVersion = 3,
            slot = "A",
            otaStatus = "IDLE",
            lastOtaResult = "NONE",
            lastFailReason = "NONE",
            batteryPercent = 86,
            network = "online"
        ),
        otaStatus = SolarCleanOtaStatus(
            status = "DOWNLOADING",
            progress = 42,
            targetVersion = "1.0.4",
            downloaded = 103219,
            total = 245760
        )
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewSolarCleanControlScreen() {
    SolarCleanControlScreen(
        device = BoundAccountDevice(
            serialNumber = "T36393932",
            name = "光伏清洗 01",
            productType = ProductType.SolarClean
        )
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewSolarCleanSettingsScreen() {
    SolarCleanControlScreen(
        device = BoundAccountDevice(
            serialNumber = "T36393932",
            name = "光伏清洗 01",
            productType = ProductType.SolarClean
        )
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewSolarCleanPrimaryControlsCard() {
    BucketTheme {
        PrimaryControlsCard(
            enabled = true,
            expanded = true,
            commandFeedback = SolarCleanCommandFeedback(
                status = SolarCleanCommandFeedbackStatus.Success,
                text = "指令已确认"
            ),
            onExpandedChange = {},
            onPumpOn = {},
            onPumpOff = {},
            onPressureChanged = {},
            onSprayAngleChanged = {},
            onSwingSpeedChanged = {},
            onSwingOn = {},
            onSwingOff = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewSolarCleanTelemetryCard() {
    BucketTheme {
        TelemetryCard(
            state = previewSolarCleanState("T36393932"),
            expanded = true,
            onExpandedChange = {}
        )
    }
}
