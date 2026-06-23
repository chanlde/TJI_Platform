package com.tji.device.product.solarclean.ui.control

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.icon.common.Eye
import com.tji.device.ui.icon.common.EyeOff
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiWarning
import kotlin.math.roundToInt

private const val CONTROL_PANEL_PREFS = "solar_clean_control_panel"
private const val CONTROL_PANEL_EXPANDED_PREFIX = "controls_expanded_"
private const val TELEMETRY_PANEL_EXPANDED_PREFIX = "telemetry_expanded_"

@Composable
internal fun SolarCleanControlPage(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?,
    controlSettings: SolarCleanControlSettings,
    enabled: Boolean,
    commandFeedback: SolarCleanCommandFeedback,
    onPumpOn: () -> Unit,
    onPumpOff: () -> Unit,
    onPressureChanged: (Int) -> Unit,
    onSprayAngleChanged: (Int) -> Unit,
    onSwingSpeedChanged: (Int) -> Unit,
    onSwingOn: () -> Unit,
    onSwingOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var controlsExpanded by remember(device.serialNumber) {
        mutableStateOf(readControlsExpanded(context, device.serialNumber))
    }
    var telemetryExpanded by remember(device.serialNumber) {
        mutableStateOf(readTelemetryExpanded(context, device.serialNumber))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PayloadColors.Background),
        contentPadding = PaddingValues(PayloadDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item {
            PrimaryControlsCard(
                enabled = enabled,
                expanded = controlsExpanded,
                controlSettings = controlSettings,
                commandFeedback = commandFeedback,
                onExpandedChange = {
                    controlsExpanded = it
                    saveControlsExpanded(context, device.serialNumber, it)
                },
                onPumpOn = onPumpOn,
                onPumpOff = onPumpOff,
                onPressureChanged = onPressureChanged,
                onSprayAngleChanged = onSprayAngleChanged,
                onSwingSpeedChanged = onSwingSpeedChanged,
                onSwingOn = onSwingOn,
                onSwingOff = onSwingOff
            )
        }
        item {
            TelemetryCard(
                state = state,
                expanded = telemetryExpanded,
                onExpandedChange = {
                    telemetryExpanded = it
                    saveTelemetryExpanded(context, device.serialNumber, it)
                }
            )
        }
    }
}

@Composable
internal fun TelemetryCard(
    state: SolarCleanDeviceState?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    TjiSectionCard(
        title = "飞行状态",
        trailing = {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Eye else EyeOff,
                    contentDescription = if (expanded) "收起飞行状态" else "展开飞行状态",
                    modifier = Modifier.size(21.dp),
                    tint = PayloadColors.Primary
                )
            }
        }
    ) {
        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TjiMetricTile("水位", state?.waterLevel?.let { waterLevelText(it) } ?: "--")
                TjiMetricTile("卫星", state?.satelliteCount?.toString() ?: "--")
                TjiMetricTile("高度", state?.altitudeMeters?.let { "${it.toInt()}m" } ?: "--")
                TjiMetricTile("速度", state?.speedMetersPerSecond?.let { "%.1fm/s".format(it) } ?: "--")
                TjiMetricTile("偏航", state?.yawDegrees?.let { "${it.toInt()}°" } ?: "--")
                TjiMetricTile("俯仰", state?.pitchDegrees?.let { "${it.toInt()}°" } ?: "--")
                TjiMetricTile("横滚", state?.rollDegrees?.let { "${it.toInt()}°" } ?: "--")
                TjiMetricTile("纬度", state?.latitude?.let { "%.5f".format(it) } ?: "--")
                TjiMetricTile("经度", state?.longitude?.let { "%.5f".format(it) } ?: "--")
                TjiMetricTile("连接", mqttStatusText(state))
            }
        }
    }
}

@Composable
internal fun PrimaryControlsCard(
    enabled: Boolean,
    expanded: Boolean,
    controlSettings: SolarCleanControlSettings,
    commandFeedback: SolarCleanCommandFeedback,
    onExpandedChange: (Boolean) -> Unit,
    onPumpOn: () -> Unit,
    onPumpOff: () -> Unit,
    onPressureChanged: (Int) -> Unit,
    onSprayAngleChanged: (Int) -> Unit,
    onSwingSpeedChanged: (Int) -> Unit,
    onSwingOn: () -> Unit,
    onSwingOff: () -> Unit
) {
    var pumpPressure by remember { mutableFloatStateOf(controlSettings.pumpPressurePercent.toFloat()) }
    var sprayAngle by remember { mutableFloatStateOf(controlSettings.sprayAngleDegrees.toFloat()) }
    var swingSpeed by remember { mutableFloatStateOf(controlSettings.swingSpeedPercent.toFloat()) }

    LaunchedEffect(controlSettings.pumpPressurePercent) {
        pumpPressure = controlSettings.pumpPressurePercent.toFloat()
    }
    LaunchedEffect(controlSettings.sprayAngleDegrees) {
        sprayAngle = controlSettings.sprayAngleDegrees.toFloat()
    }
    LaunchedEffect(controlSettings.swingSpeedPercent) {
        swingSpeed = controlSettings.swingSpeedPercent.toFloat()
    }

    TjiSectionCard(
        title = "设备控制",
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommandFeedbackBadge(commandFeedback)
                IconButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Eye else EyeOff,
                        contentDescription = if (expanded) "收起操作面板" else "展开操作面板",
                        modifier = Modifier.size(21.dp),
                        tint = PayloadColors.Primary
                    )
                }
            }
        }
    ) {
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlSlider(
                    kind = SliderKind.Pressure,
                    title = "水泵压力",
                    value = pumpPressure,
                    unit = "%",
                    enabled = enabled,
                    onValueChange = { pumpPressure = it },
                    onValueChangeFinished = { onPressureChanged(pumpPressure.roundToInt()) }
                )
                ControlSlider(
                    kind = SliderKind.Angle,
                    title = "喷洒角度",
                    value = sprayAngle,
                    unit = "°",
                    valueRange = 0f..40f,
                    enabled = enabled,
                    onValueChange = { sprayAngle = it },
                    onValueChangeFinished = { onSprayAngleChanged(sprayAngle.roundToInt()) }
                )
                ControlSlider(
                    kind = SliderKind.Speed,
                    title = "摆动速度",
                    value = swingSpeed,
                    unit = "%",
                    enabled = enabled,
                    onValueChange = { swingSpeed = it },
                    onValueChangeFinished = { onSwingSpeedChanged(swingSpeed.roundToInt()) }
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlButton("开泵", enabled, PayloadColors.Primary, onPumpOn)
                ControlButton("关泵", enabled, TjiWarning, onPumpOff)
                ControlButton("摆动开", enabled, PayloadColors.Primary, onSwingOn)
                ControlButton("摆动关", enabled, TjiWarning, onSwingOff)
            }
        }
    }
}

private fun readControlsExpanded(context: Context, serialNumber: String): Boolean {
    return context
        .getSharedPreferences(CONTROL_PANEL_PREFS, Context.MODE_PRIVATE)
        .getBoolean(CONTROL_PANEL_EXPANDED_PREFIX + serialNumber, true)
}

private fun saveControlsExpanded(context: Context, serialNumber: String, expanded: Boolean) {
    context
        .getSharedPreferences(CONTROL_PANEL_PREFS, Context.MODE_PRIVATE)
        .edit {
            putBoolean(CONTROL_PANEL_EXPANDED_PREFIX + serialNumber, expanded)
        }
}

private fun readTelemetryExpanded(context: Context, serialNumber: String): Boolean {
    return context
        .getSharedPreferences(CONTROL_PANEL_PREFS, Context.MODE_PRIVATE)
        .getBoolean(TELEMETRY_PANEL_EXPANDED_PREFIX + serialNumber, true)
}

private fun saveTelemetryExpanded(context: Context, serialNumber: String, expanded: Boolean) {
    context
        .getSharedPreferences(CONTROL_PANEL_PREFS, Context.MODE_PRIVATE)
        .edit {
            putBoolean(TELEMETRY_PANEL_EXPANDED_PREFIX + serialNumber, expanded)
        }
}
