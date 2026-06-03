package com.tji.device.product.firebucket.ui.control

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.model.SwitchControlParms
import com.tji.device.ui.components.BatteryIndicator
import com.tji.device.ui.components.CustomSlider
import com.tji.device.ui.components.DeviceInfoButton
import com.tji.device.ui.components.StatusChip
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.theme.TjiBorder
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiSurface
import com.tji.device.ui.theme.TjiTextPrimary
import com.tji.device.ui.theme.TjiTextSecondary
import com.tji.device.ui.theme.TjiWarning

@Composable
fun SwitchItem(
    linkSn: String,
    switch: Switch,
    scParms: SwitchControlParms,
    onControl: (SwitchControlParms) -> Unit,
    onAngleChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var angle by remember { mutableFloatStateOf(switch.currentAngle?.toFloat() ?: scParms.angle?.toFloat() ?: 30f) }
    fun updateAngleAndControl(newAngle: Float) {
        angle = newAngle
        scParms.angle = newAngle.toInt()
        scParms.speed = 100
        onControl(scParms)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = TjiSurface,
            contentColor = TjiTextPrimary
        )
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = switch.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TjiTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(switch.isOnline)
                    BatteryIndicator(
                        voltage = switch.inputVoltage,
                        iconSize = 20.dp
                    )
                }

                DeviceInfoButton(switch = switch)
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = TjiBorder,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(5.dp))
            AngleSlider(
                value = angle,
                onValueChange = { newAngle ->
                    angle = newAngle
                    scParms.angle = newAngle.toInt()
                    onAngleChange(newAngle.toInt())
                    Log.d("SwitchItem", "滑块角度更新: $linkSn,$newAngle, scParms.angle: ${scParms.angle}")
                },
                modifier = Modifier.fillMaxWidth(),
                minValue = 0f,
                maxValue = 90f
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 0.dp)
                .padding(bottom = 12.dp)
        ) {
            TjiActionButton(
                text = "打开",
                enabled = true,
                color = TjiPrimary,
                onClick = { updateAngleAndControl(90f) },
                modifier = Modifier
                    .weight(1f)
            )

            TjiActionButton(
                text = "关闭",
                enabled = true,
                color = TjiWarning,
                onClick = { updateAngleAndControl(0f) },
                modifier = Modifier
                    .weight(1f)
            )
        }
    }
}

@Composable
fun AngleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 90f
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "阀门角度",
                style = MaterialTheme.typography.bodyMedium,
                color = TjiTextPrimary
            )
            Text(
                text = "${value.toInt()}°",
                style = MaterialTheme.typography.titleMedium,
                color = TjiTextPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically  // 添加垂直居中对齐
        ) {
            Text(
                text = "${minValue.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = TjiTextSecondary
            )

            CustomSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)  // 关键：让滑块占据剩余空间
                    .padding(horizontal = 20.dp)
            )

            Text(
                text = "${maxValue.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = TjiTextSecondary
            )
        }
    }
}

// 预览 Composable
@Preview(showBackground = true)
@Composable
fun SwitchItemPreview() {
    MaterialTheme {
        val switch = Switch(
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
        SwitchItem(
            linkSn = "dddddddddddddddd",
            switch = switch,
            scParms = SwitchControlParms(
                sn = switch.serialNumber,
                angle = switch.currentAngle.toInt(), // 转换为 Int
                speed = 10,
                mode = ControlMode.ABSOLUTE
            ),
            onControl = { parms ->
                // 模拟控制操作
                println("Control: ${parms.angle}, ${parms.mode}")
            },
            onAngleChange = { angle ->
                // 模拟角度变化
                println("Angle changed to: $angle")
            }
        )
    }
}
