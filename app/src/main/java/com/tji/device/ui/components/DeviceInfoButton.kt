package com.tji.device.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tji.device.ui.icon.common.InfoCircle
import com.tji.device.R
import com.tji.device.product.firebucket.model.Switch

@Composable
fun DeviceInfoButton(
    switch: Switch,
) {
    var showDialog by remember { mutableStateOf(false) }

    // IconButton 替代 Box
    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .size(24.dp) // 设置按钮整体大小
    ) {
        Icon(
            imageVector  = InfoCircle,
            contentDescription = "显示设备信息",
            modifier = Modifier
                .size(24.dp) // 图标尺寸与按钮相同
        )
    }

    // 设备信息对话框
    if (showDialog) {
        DeviceInfoDialog(
            switch = switch,
            onDismiss = { showDialog = false }
        )
    }
}
@Composable
private fun DeviceInfoDialog(
    switch: Switch,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 头部
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "设备 ${switch.deviceName}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                val deviceInfoItems = listOf(
                    "设备ID" to switch.serialNumber,  // 使用 serialNumber
                    "设备名称" to switch.deviceName,  // 使用 deviceName
                    "连接状态" to if (switch.isOnline) "在线" else "离线",  // 使用 isOnline
                    "当前角度" to "${switch.currentAngle}°",  // 使用 currentAngle
                    "当前电流" to "${switch.currentCurrent}mA",  // 使用 currentCurrent
                    "输入电压" to "${switch.inputVoltage}V",  // 使用 inputVoltage
                    "舵机最小角度" to "${switch.servoMinAngle}°",  // 使用 servoMinAngle
                    "舵机最大角度" to "${switch.servoMaxAngle}°",  // 使用 servoMaxAngle
                    "运行时间" to "${switch.uptime}秒"  // 使用 uptime
                )

                deviceInfoItems.forEach { (label, value) ->
                    DeviceInfoItem(label = label, value = value)
                }

                Spacer(modifier = Modifier.height(5.dp))

            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceInfoExample() {
    val sampleSwitch = Switch(
        serialNumber = "HD20240101002",
        deviceName = "Servo-Controller-01",
        deviceType = "HydroSwitch",
        isOnline = true,
        currentAngle = 45.0,
        currentCurrent = 120.5,
        inputVoltage = 12.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 180.0,
        uptime = 118
    )

    DeviceInfoDialog(sampleSwitch, onDismiss={})
    DeviceInfoButton(sampleSwitch)
    StatusChip(isOnline = true)
}

@Preview(showBackground = true)
@Composable
fun Example() {
    val sampleSwitch = Switch(
        serialNumber = "HD20240101002",
        deviceName = "Servo-Controller-01",
        deviceType = "HydroSwitch",
        isOnline = true,
        currentAngle = 45.0,
        currentCurrent = 120.5,
        inputVoltage = 12.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 180.0,
        uptime = 118
    )

    DeviceInfoButton(sampleSwitch)
}
