package com.tji.device.product.firebucket.ui.control

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.ui.components.TjiCardShell
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.icon.product.productIconVector
import com.tji.device.ui.theme.TjiBorder
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiPrimarySoft
import com.tji.device.ui.theme.TjiTextPrimary
import com.tji.device.ui.theme.TjiTextSecondary

@Composable
fun LinkItem(
    link: FireBucketLinkDevice,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    TjiCardShell(
        modifier = modifier.fillMaxWidth(),
        radius = 22.dp,
        elevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            val subtitle = link.serial_number.takeUnless {
                it.equals(link.deviceName, ignoreCase = true)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(TjiPrimarySoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = productIconVector(link.productType),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = TjiPrimary
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = link.deviceName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TjiTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        FireBucketInlineStatus(isOnline = link.isOnline)
                    }
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TjiTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (link.subDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = TjiBorder)
                Spacer(modifier = Modifier.height(12.dp))

                if (isPortrait) {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(link.subDevices) { switch ->
                            SwitchItemComposable(link.serial_number, switch = switch)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(link.subDevices) { switch ->
                            SwitchItemComposable(link.serial_number, switch = switch)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FireBucketInlineStatus(isOnline: Boolean) {
    TjiOnlineStatus(isOnline = isOnline)
}

@Preview(showBackground = true)
@Composable
fun LinkItemPreview() {
    val mockLink = FireBucketLinkDevice(
        event_type = "device_status",
        serial_number = "TJI001",
        deviceName = "客厅控制器",
        deviceType = "Controller",
        manufacturer = "TJI",
        deviceModel = "TJI-C100",
        isOnline = true,
        hwVersion = "1.2",
        swVersion = "2.1.0",
        uptime = 86400,
        deviceConfig = "",
        subDevices = listOf(
            Switch(
                serialNumber = "客厅-SWITCH1",
                deviceName = "客厅 舵机控制器 1",
                deviceType = "HydroSwitch",
                isOnline = true,
                currentAngle = 15.0,
                currentCurrent = 110.0,
                inputVoltage = 12.0,
                servoMinAngle = 0.0,
                servoMaxAngle = 180.0,
                uptime = 100
            ),
            Switch(
                serialNumber = "客厅-SWITCH2",
                deviceName = "客厅 舵机控制器 2",
                deviceType = "HydroSwitch",
                isOnline = false,
                currentAngle = 30.0,
                currentCurrent = 120.0,
                inputVoltage = 12.0,
                servoMinAngle = 0.0,
                servoMaxAngle = 180.0,
                uptime = 200
            )
        ),
        timestamp = System.currentTimeMillis().toString()
    )

    MaterialTheme {
        LinkItem(link = mockLink)
    }
}
