package com.tji.device.product.radiodetection.ui.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tji.device.product.radiodetection.map.RadioDetectionAmapView
import com.tji.device.product.radiodetection.map.RadioDetectionMapConfig
import com.tji.device.product.radiodetection.map.RadioDetectionMapRuntime
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioDetectionUiState

internal enum class RadioMonitorMode {
    Map,
    List
}

@Composable
internal fun TacticalMap(
    state: RadioDetectionUiState,
    mode: RadioMonitorMode,
    focusedTargetId: String?,
    focusTargetSignal: Int,
    onModeChange: (RadioMonitorMode) -> Unit,
    onTargetClick: (RadioDetectionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomSignal by remember { mutableIntStateOf(0) }
    var zoomDelta by remember { mutableFloatStateOf(0f) }
    var recenterSignal by remember { mutableIntStateOf(0) }
    val useGaodeMap = remember { RadioDetectionMapRuntime.shouldUseGaodeMap() }

    BoxWithConstraints(modifier = modifier.background(MapBg)) {
        if (useGaodeMap) {
            RadioDetectionAmapView(
                config = RadioDetectionMapConfig(),
                state = state,
                focusedTargetId = focusedTargetId,
                focusTargetSignal = focusTargetSignal,
                zoomSignal = zoomSignal,
                zoomDelta = zoomDelta,
                recenterSignal = recenterSignal,
                onTargetClick = onTargetClick,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PrototypeRadioDetectionMap(
                state = state,
                focusedTargetId = focusedTargetId,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.currentCoordinate.hasUsableCoordinate()) {
            Text(
                text = "LAT %.6f\nLNG %.6f\nALT %.1fm".format(
                    state.currentCoordinate.latitude,
                    state.currentCoordinate.longitude,
                    state.currentCoordinate.altitudeMeters
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF171717).copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        } else {
            Text(
                text = "等待 RID 位置",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF171717).copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111111).copy(alpha = 0.84f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
        ) {
            MapToggle(
                text = "地图",
                selected = mode == RadioMonitorMode.Map,
                onClick = { onModeChange(RadioMonitorMode.Map) }
            )
            MapToggle(
                text = "列表",
                selected = mode == RadioMonitorMode.List,
                onClick = { onModeChange(RadioMonitorMode.List) }
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapTool("+") {
                zoomDelta = 1f
                zoomSignal += 1
            }
            MapTool("-") {
                zoomDelta = -1f
                zoomSignal += 1
            }
            MapTool("◎") {
                recenterSignal += 1
            }
        }
        if (!useGaodeMap) {
            Text(
                text = "兼容态势图",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PrototypeRadioDetectionMap(
    state: RadioDetectionUiState,
    focusedTargetId: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(MapBg)) {
        val gridColor = Color.White.copy(alpha = 0.08f)
        val stripeColor = Color.White.copy(alpha = 0.08f)
        val center = Offset(size.width * 0.5f, size.height * 0.48f)
        val cell = 56.dp.toPx()

        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += cell
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += cell
        }

        drawLine(
            color = stripeColor,
            start = Offset(-size.width * 0.15f, size.height * 0.70f),
            end = Offset(size.width * 0.92f, -size.height * 0.10f),
            strokeWidth = 42.dp.toPx()
        )
        drawCircle(
            color = Blue.copy(alpha = 0.55f),
            radius = size.minDimension * 0.34f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )

        val usableTargets = state.targets.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        usableTargets.forEachIndexed { index, target ->
            val focused = target.id == focusedTargetId
            val angle = (index * 58f + 24f) * kotlin.math.PI.toFloat() / 180f
            val radius = size.minDimension * (0.12f + (index % 3) * 0.09f)
            val targetPoint = Offset(
                x = center.x + kotlin.math.cos(angle) * radius,
                y = center.y + kotlin.math.sin(angle) * radius
            )
            val pilotPoint = Offset(
                x = center.x - kotlin.math.cos(angle) * radius * 0.72f,
                y = center.y - kotlin.math.sin(angle) * radius * 0.72f
            )
            val color = when (target.listStatus) {
                com.tji.device.product.radiodetection.model.RadioListStatus.Blacklist -> Red
                com.tji.device.product.radiodetection.model.RadioListStatus.Whitelist -> Green
                com.tji.device.product.radiodetection.model.RadioListStatus.Unknown -> Amber
            }

            drawLine(
                color = color.copy(alpha = 0.85f),
                start = targetPoint,
                end = pilotPoint,
                strokeWidth = if (focused) 5.dp.toPx() else 3.dp.toPx()
            )
            drawCircle(
                color = color.copy(alpha = if (focused) 0.30f else 0.18f),
                radius = if (focused) 24.dp.toPx() else 18.dp.toPx(),
                center = targetPoint
            )
            drawCircle(color = color, radius = 12.dp.toPx(), center = targetPoint)
            drawCircle(color = Color.White, radius = 12.dp.toPx(), center = targetPoint, style = Stroke(2.dp.toPx()))
            drawCircle(color = Blue, radius = 10.dp.toPx(), center = pilotPoint)
            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = pilotPoint, style = Stroke(2.dp.toPx()))
        }
    }
}

private fun com.tji.device.product.radiodetection.model.RadioCoordinate.hasUsableCoordinate(): Boolean =
    latitude != 0.0 || longitude != 0.0

@Composable
private fun MapToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) TextPrimary else Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun MapTool(text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
