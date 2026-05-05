package com.tji.device.product.solarclean.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModel
import com.tji.device.ui.floating.ExpandedCard
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.product.solarclean.ui.icon.PumpPressure
import com.tji.device.product.solarclean.ui.icon.SprayAngle
import com.tji.device.product.solarclean.ui.icon.SwingSpeed
import kotlin.math.roundToInt

private val PrimaryBlue = Color(0xFF1677FF)
private val WarningOrange = Color(0xFFFA8C16)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextMuted = Color(0xFF8C8C8C)

@Composable
fun SolarCleanFloatingPanel(
    link: FloatingLinkSummary?
) {
    val viewModel: SolarCleanControlViewModel = viewModel(
        factory = AppContainer.solarCleanControlViewModelFactory
    )
    val serialNumber = link?.serialNumber
    SolarCleanFloatingPanelContent(
        link = link,
        onPumpPressureChanged = { value ->
            serialNumber?.let { viewModel.setPumpPressure(it, value.toDouble()) }
        },
        onSprayAngleChanged = { value ->
            serialNumber?.let { viewModel.setSprayAngle(it, value.toDouble()) }
        },
        onSwingSpeedChanged = { value ->
            serialNumber?.let { viewModel.setSwingSpeed(it, value.toDouble()) }
        },
        onPumpOn = {
            serialNumber?.let { viewModel.setPump(it, true) }
        },
        onPumpOff = {
            serialNumber?.let { viewModel.setPump(it, false) }
        },
        onSwingOn = {
            serialNumber?.let { viewModel.setServoSwing(it, true) }
        },
        onSwingOff = {
            serialNumber?.let { viewModel.setServoSwing(it, false) }
        }
    )
}

@Composable
private fun SolarCleanFloatingPanelContent(
    link: FloatingLinkSummary?,
    onPumpPressureChanged: (Int) -> Unit,
    onSprayAngleChanged: (Int) -> Unit,
    onSwingSpeedChanged: (Int) -> Unit,
    onPumpOn: () -> Unit,
    onPumpOff: () -> Unit,
    onSwingOn: () -> Unit,
    onSwingOff: () -> Unit
) {
    val serialNumber = link?.serialNumber
    val enabled = !serialNumber.isNullOrBlank()
    var pumpPressure by remember(serialNumber) { mutableFloatStateOf(50f) }
    var sprayAngle by remember(serialNumber) { mutableFloatStateOf(20f) }
    var swingSpeed by remember(serialNumber) { mutableFloatStateOf(50f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "设备控制",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                FloatingControlSlider(
                    kind = SliderKind.Pressure,
                    value = pumpPressure,
                    unit = "%",
                    enabled = enabled,
                    onValueChange = { pumpPressure = it },
                    onValueChangeFinished = { onPumpPressureChanged(pumpPressure.roundToInt()) }
                )
                FloatingControlSlider(
                    kind = SliderKind.Angle,
                    value = sprayAngle,
                    unit = "°",
                    valueRange = 0f..40f,
                    enabled = enabled,
                    onValueChange = { sprayAngle = it },
                    onValueChangeFinished = { onSprayAngleChanged(sprayAngle.roundToInt()) }
                )
                FloatingControlSlider(
                    kind = SliderKind.Speed,
                    value = swingSpeed,
                    unit = "%",
                    enabled = enabled,
                    onValueChange = { swingSpeed = it },
                    onValueChangeFinished = { onSwingSpeedChanged(swingSpeed.roundToInt()) }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingActionButton(
                            text = "开泵",
                            enabled = enabled,
                            color = PrimaryBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onPumpOn
                        )
                        FloatingActionButton(
                            text = "关泵",
                            enabled = enabled,
                            color = WarningOrange,
                            modifier = Modifier.weight(1f),
                            onClick = onPumpOff
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingActionButton(
                            text = "摆动开",
                            enabled = enabled,
                            color = PrimaryBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onSwingOn
                        )
                        FloatingActionButton(
                            text = "摆动关",
                            enabled = enabled,
                            color = WarningOrange,
                            modifier = Modifier.weight(1f),
                            onClick = onSwingOff
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingControlSlider(
    kind: SliderKind,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SliderGlyph(kind = kind)
        Slider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(3.dp, RoundedCornerShape(50))
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PrimaryBlue, RoundedCornerShape(50))
                    )
                }
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                    colors = SliderDefaults.colors(
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = Color(0xFFE5ECF8),
                        disabledActiveTrackColor = Color(0xFFCBD5E1),
                        disabledInactiveTrackColor = Color(0xFFE5E7EB)
                    )
                )
            }
        )
        Text(
            text = "${value.roundToInt()}$unit",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun SliderGlyph(kind: SliderKind) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(Color(0xFFEAF2FF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (kind) {
                SliderKind.Pressure -> PumpPressure
                SliderKind.Angle -> SprayAngle
                SliderKind.Speed -> SwingSpeed
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = PrimaryBlue
        )
    }
}

private enum class SliderKind {
    Pressure,
    Angle,
    Speed
}

@Composable
private fun FloatingActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFFE5E7EB),
            disabledContentColor = TextMuted
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Preview(
    name = "光伏清洗悬浮窗",
    showBackground = true,
    backgroundColor = 0xFFF5F7FA,
    widthDp = 360
)
@Composable
private fun PreviewSolarCleanFloatingPanel() {
    ExpandedCard(
        productType = ProductType.SolarClean,
        link = previewFloatingLink(isOnline = true),
        allSwitches = emptyList(),
        currentSwitchIndex = 0,
        onSwitchSelected = {},
        onClose = {},
        onMinimize = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> }
    )
}

@Preview(
    name = "光伏清洗悬浮窗-未连接",
    showBackground = true,
    backgroundColor = 0xFFF5F7FA,
    widthDp = 360
)
@Composable
private fun PreviewSolarCleanFloatingPanelOffline() {
    ExpandedCard(
        productType = ProductType.SolarClean,
        link = previewFloatingLink(isOnline = false),
        allSwitches = emptyList(),
        currentSwitchIndex = 0,
        onSwitchSelected = {},
        onClose = {},
        onMinimize = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> }
    )
}

@Preview(
    name = "光伏清洗面板内容",
    showBackground = true,
    backgroundColor = 0xFFF5F7FA,
    widthDp = 340
)
@Composable
private fun PreviewSolarCleanFloatingPanelContent() {
    SolarCleanFloatingPanelContent(
        link = previewFloatingLink(isOnline = true),
        onPumpPressureChanged = {},
        onSprayAngleChanged = {},
        onSwingSpeedChanged = {},
        onPumpOn = {},
        onPumpOff = {},
        onSwingOn = {},
        onSwingOff = {}
    )
}

private fun previewFloatingLink(isOnline: Boolean): FloatingLinkSummary {
    return FloatingLinkSummary(
        serialNumber = "T36393932",
        name = "T36393932",
        isOnline = isOnline,
        productType = ProductType.SolarClean,
        onlineSwitches = emptyList(),
        offlineSwitches = emptyList()
    )
}
