package com.tji.device.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.icon.common.BatteryOutline
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiWarning

/**
 * 根据电压显示电量图标（使用 Canvas 精确绘制）
 */
@Composable
fun BatteryIndicator(
    voltage: Double,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp
) {
    val batteryLevel = calculateBatteryLevel(voltage)
    val fillColor = getBatteryColor(batteryLevel)
    val fillWidth = batteryLevel.coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        // 电池外框
        Icon(
            imageVector = BatteryOutline,
            contentDescription = "电量 ${(batteryLevel * 100).toInt()}%",
            modifier = Modifier.size(iconSize),
            tint = PayloadColors.TextMuted
        )

        // 使用 Canvas 绘制电量填充
        Canvas(modifier = Modifier.size(iconSize)) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 根据 16x16 viewport 计算实际像素位置
            // 内部填充区域：从 (1, 5) 到 (11, 11)
            val innerLeft = canvasWidth * (1f / 16f)
            val innerTop = canvasHeight * (5f / 16f)
            val innerWidth = canvasWidth * (10f / 16f)
            val innerHeight = canvasHeight * (6f / 16f)

            if (fillWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(innerLeft, innerTop),
                    size = Size(
                        width = innerWidth * fillWidth,
                        height = innerHeight
                    ),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

private fun calculateBatteryLevel(voltage: Double): Float {
    val minVoltage = 7.0
    val maxVoltage = 8.1
    val clampedVoltage = voltage.coerceIn(minVoltage, maxVoltage)
    return ((clampedVoltage - minVoltage) / (maxVoltage - minVoltage)).toFloat()
}

private fun getBatteryColor(level: Float): Color {
    return when {
        level <= 0.0f -> TjiError
        level <= 0.15f -> TjiError
        level <= 0.3f -> TjiWarning
        level <= 0.45f -> TjiWarning
        level <= 0.6f -> TjiOnline.copy(alpha = 0.72f)
        level <= 0.75f -> TjiOnline
        level <= 0.9f -> TjiOnline
        else -> TjiOnline
    }
}
