package com.tji.device.product.firebucket.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.ui.theme.TjiBackground

@Composable
fun FireBucketControlScreen(
    link: FireBucketLinkDevice,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TjiBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LinkItem(link = link)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun FireBucketControlScreenPreview() {
    BucketTheme {
        FireBucketControlScreen(
            link = FireBucketLinkDevice(
                event_type = "LinkDeviceStartup",
                serial_number = "HydroLink_V3-7003DEF5",
                deviceName = "HydroLink_V3-7003DEF5",
                deviceType = "HydroLink",
                manufacturer = "TJI",
                deviceModel = "HydroLink V3",
                isOnline = true,
                hwVersion = "HW-1",
                swVersion = "1.0.0",
                uptime = 3600,
                deviceConfig = "",
                subDevices = listOf(
                    Switch(
                        serialNumber = "Bucket-001",
                        deviceName = "消防吊桶 01",
                        deviceType = "HydroSwitch",
                        isOnline = true,
                        currentAngle = 42.0,
                        currentCurrent = 100.0,
                        inputVoltage = 7.8,
                        servoMinAngle = 0.0,
                        servoMaxAngle = 90.0,
                        uptime = 300
                    )
                ),
                timestamp = "0"
            )
        )
    }
}
