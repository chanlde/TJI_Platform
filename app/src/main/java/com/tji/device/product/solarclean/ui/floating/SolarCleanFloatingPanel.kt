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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModel
import com.tji.device.ui.floating.ExpandedCard
import com.tji.device.ui.floating.FloatingWindowAppearance
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.ui.components.TjiMiniSwitch
import com.tji.device.ui.components.TjiControlSlider
import com.tji.device.product.solarclean.ui.icon.PumpPressure
import com.tji.device.product.solarclean.ui.icon.SprayAngle
import com.tji.device.product.solarclean.ui.icon.SwingSpeed
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import kotlin.math.roundToInt

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
    val enabled = !serialNumber.isNullOrBlank() && link?.isOnline == true
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        FloatingWindowAppearance.load(context)
    }
    val backgroundAlpha by FloatingWindowAppearance.backgroundAlpha.collectAsStateWithLifecycle()
    var pumpPressure by remember(serialNumber) { mutableFloatStateOf(50f) }
    var sprayAngle by remember(serialNumber) { mutableFloatStateOf(20f) }
    var swingSpeed by remember(serialNumber) { mutableFloatStateOf(50f) }
    var pumpOn by remember(serialNumber) { androidx.compose.runtime.mutableStateOf(false) }
    var swingOn by remember(serialNumber) { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PayloadColors.Surface.copy(alpha = backgroundAlpha), RoundedCornerShape(PayloadDimens.ControlRadius))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FloatingControlSlider(
                kind = SliderKind.Pressure,
                title = "压力",
                value = pumpPressure,
                unit = "%",
                enabled = enabled,
                onValueChange = { pumpPressure = it },
                onValueChangeFinished = { onPumpPressureChanged(pumpPressure.roundToInt()) }
            )
            FloatingControlSlider(
                kind = SliderKind.Angle,
                title = "角度",
                value = sprayAngle,
                unit = "°",
                valueRange = 0f..40f,
                enabled = enabled,
                onValueChange = { sprayAngle = it },
                onValueChangeFinished = { onSprayAngleChanged(sprayAngle.roundToInt()) }
            )
            FloatingControlSlider(
                kind = SliderKind.Speed,
                title = "速度",
                value = swingSpeed,
                unit = "%",
                enabled = enabled,
                onValueChange = { swingSpeed = it },
                onValueChangeFinished = { onSwingSpeedChanged(swingSpeed.roundToInt()) }
            )
            CompactToggleBar(
                pumpOn = pumpOn,
                swingOn = swingOn,
                enabled = enabled,
                backgroundAlpha = backgroundAlpha,
                onPumpChange = {
                    pumpOn = it
                    if (it) onPumpOn() else onPumpOff()
                },
                onSwingChange = {
                    swingOn = it
                    if (it) onSwingOn() else onSwingOff()
                }
            )
        }
    }
}

@Composable
private fun FloatingControlSlider(
    kind: SliderKind,
    title: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SliderGlyph(kind = kind)
        TjiControlSlider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.width(132.dp),
            thumbSize = 16.dp
        )
        Row(
            modifier = Modifier.width(52.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextMuted,
                maxLines = 1
            )
            Text(
                text = "${value.roundToInt()}$unit",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PayloadColors.Primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SliderGlyph(kind: SliderKind) {
    Box(
        modifier = Modifier
            .size(24.dp),
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
            tint = PayloadColors.Primary
        )
    }
}

@Composable
private fun CompactToggleBar(
    pumpOn: Boolean,
    swingOn: Boolean,
    enabled: Boolean,
    backgroundAlpha: Float,
    onPumpChange: (Boolean) -> Unit,
    onSwingChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(PayloadColors.SurfaceSoft.copy(alpha = backgroundAlpha), RoundedCornerShape(PayloadDimens.CompactRadius)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactSwitchCell(
            iconKind = SliderKind.Pressure,
            title = "水泵",
            checked = pumpOn,
            enabled = enabled,
            onCheckedChange = onPumpChange
        )
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(PayloadColors.Border, RoundedCornerShape(99.dp))
        )
        CompactSwitchCell(
            iconKind = SliderKind.Speed,
            title = "摆动",
            checked = swingOn,
            enabled = enabled,
            onCheckedChange = onSwingChange
        )
    }
}

@Composable
private fun CompactSwitchCell(
    iconKind: SliderKind,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (iconKind) {
                SliderKind.Pressure -> PumpPressure
                SliderKind.Angle -> SprayAngle
                SliderKind.Speed -> SwingSpeed
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) PayloadColors.Primary else PayloadColors.TextMuted
        )
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PayloadColors.TextMuted else PayloadColors.TextMuted.copy(alpha = 0.6f),
            maxLines = 1
        )
        TjiMiniSwitch(
            checked = checked,
            enabled = enabled
        )
    }
}

private enum class SliderKind {
    Pressure,
    Angle,
    Speed
}

@Preview(
    name = "光伏清洗悬浮窗",
    showBackground = true,
    backgroundColor = 0xFFF5F7FA,
    widthDp = 300
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
    widthDp = 300
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
    widthDp = 300
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
