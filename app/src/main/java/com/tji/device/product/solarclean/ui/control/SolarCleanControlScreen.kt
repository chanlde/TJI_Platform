package com.tji.device.product.solarclean.ui.control

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.di.AppContainer
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModel
import com.tji.device.product.solarclean.viewmodel.SolarCleanOtaCheckState
import com.tji.device.ui.components.productSceneRes
import com.tji.device.product.solarclean.ui.icon.PumpPressure
import com.tji.device.product.solarclean.ui.icon.SprayAngle
import com.tji.device.product.solarclean.ui.icon.SwingSpeed
import com.tji.network.data.OtaLatestResponse
import kotlin.math.roundToInt

private val PageBackground = Color(0xFFF5F7FA)
private val PrimaryBlue = Color(0xFF1677FF)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextMuted = Color(0xFF8C8C8C)
private val OnlineGreen = Color(0xFF52C41A)
private val WarningOrange = Color(0xFFFA8C16)
private val ErrorRed = Color(0xFFFF4D4F)

@Composable
fun SolarCleanControlScreen(
    device: BoundAccountDevice,
    showSettings: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val viewModel: SolarCleanControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.solarCleanControlViewModelFactory)
    }
    val devices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    }
    val state = devices.firstOrNull { it.serialNumber == device.serialNumber }
    val displayState = if (isPreview) previewSolarCleanState(device.serialNumber) else state
    val otaCheckState by viewModel?.otaCheckState?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(previewOtaCheckState()) }
    }

    LaunchedEffect(viewModel, device.serialNumber) {
        viewModel?.requestDeviceInfo(device.serialNumber)
    }

    if (showSettings) {
        SolarCleanSettingsPage(
            device = device,
            state = displayState,
            otaCheckState = otaCheckState,
            enabled = viewModel != null,
            onRefreshDeviceInfo = { viewModel?.requestDeviceInfo(device.serialNumber) },
            onCheckUpdate = {
                Log.d(
                    "SolarCleanControlUI",
                    "检测更新按钮点击: sn=${device.serialNumber}, hasViewModel=${viewModel != null}, hasDeviceInfo=${displayState?.deviceInfo != null}"
                )
                viewModel?.checkOta(device.serialNumber, displayState?.deviceInfo)
            },
            onStartOta = { viewModel?.startOta(device.serialNumber, displayState?.deviceInfo) },
            modifier = modifier
        )
    } else {
        SolarCleanControlPage(
            device = device,
            state = displayState,
            enabled = viewModel != null,
            onPumpOn = { viewModel?.setPump(device.serialNumber, true) },
            onPumpOff = { viewModel?.setPump(device.serialNumber, false) },
            onPressureChanged = { viewModel?.setPumpPressure(device.serialNumber, it.toDouble()) },
            onSprayAngleChanged = { viewModel?.setSprayAngle(device.serialNumber, it.toDouble()) },
            onSwingSpeedChanged = { viewModel?.setSwingSpeed(device.serialNumber, it.toDouble()) },
            onSwingOn = { viewModel?.setServoSwing(device.serialNumber, true) },
            onSwingOff = { viewModel?.setServoSwing(device.serialNumber, false) },
            modifier = modifier
        )
    }
}

@Composable
private fun SolarCleanControlPage(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?,
    enabled: Boolean,
    onPumpOn: () -> Unit,
    onPumpOff: () -> Unit,
    onPressureChanged: (Int) -> Unit,
    onSprayAngleChanged: (Int) -> Unit,
    onSwingSpeedChanged: (Int) -> Unit,
    onSwingOn: () -> Unit,
    onSwingOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PrimaryControlsCard(
                enabled = enabled,
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
            TelemetryCard(state = state)
        }
    }
}

@Composable
private fun SolarCleanSettingsPage(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?,
    otaCheckState: SolarCleanOtaCheckState,
    enabled: Boolean,
    onRefreshDeviceInfo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartOta: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeviceInfoSettingsCard(device = device, state = state)
        }
        item {
            OtaCard(
                state = state,
                otaCheckState = otaCheckState,
                enabled = enabled,
                onRefreshDeviceInfo = onRefreshDeviceInfo,
                onCheckUpdate = onCheckUpdate,
                onStartOta = onStartOta
            )
        }
    }
}

@Composable
private fun SolarCleanHeroCard(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?
) {
    val online = state?.isOnline == true
    CardShell {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .background(
                    brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFFAFBFF))),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Image(
                painter = painterResource(id = productSceneRes(ProductType.SolarClean)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.45f)
                    .height(136.dp)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White,
                                0.58f to Color.White.copy(alpha = 0.98f),
                                0.76f to Color.White.copy(alpha = 0.62f),
                                1.00f to Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.58f)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = device.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.serialNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusBadge(
                    text = if (online) "在线" else "离线",
                    color = if (online) OnlineGreen else ErrorRed
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(state: SolarCleanDeviceState?) {
    SectionCard(title = "飞行状态") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile("水位", state?.waterLevel?.let { waterLevelText(it) } ?: "--")
            MetricTile("卫星", state?.satelliteCount?.toString() ?: "--")
            MetricTile("高度", state?.altitudeMeters?.let { "${it.toInt()}m" } ?: "--")
            MetricTile("速度", state?.speedMetersPerSecond?.let { "%.1fm/s".format(it) } ?: "--")
            MetricTile("偏航", state?.yawDegrees?.let { "${it.toInt()}°" } ?: "--")
            MetricTile("俯仰", state?.pitchDegrees?.let { "${it.toInt()}°" } ?: "--")
            MetricTile("横滚", state?.rollDegrees?.let { "${it.toInt()}°" } ?: "--")
            MetricTile("纬度", state?.latitude?.let { "%.5f".format(it) } ?: "--")
            MetricTile("经度", state?.longitude?.let { "%.5f".format(it) } ?: "--")
            MetricTile("MQTT", mqttStatusText(state))
        }
    }
}

@Composable
private fun PrimaryControlsCard(
    enabled: Boolean,
    onPumpOn: () -> Unit,
    onPumpOff: () -> Unit,
    onPressureChanged: (Int) -> Unit,
    onSprayAngleChanged: (Int) -> Unit,
    onSwingSpeedChanged: (Int) -> Unit,
    onSwingOn: () -> Unit,
    onSwingOff: () -> Unit
) {
    var pumpPressure by remember { mutableFloatStateOf(50f) }
    var sprayAngle by remember { mutableFloatStateOf(20f) }
    var swingSpeed by remember { mutableFloatStateOf(50f) }

    SectionCard(title = "设备控制") {
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
            ControlButton("开泵", enabled, PrimaryBlue, onPumpOn)
            ControlButton("关泵", enabled, WarningOrange, onPumpOff)
            ControlButton("摆动开", enabled, PrimaryBlue, onSwingOn)
            ControlButton("摆动关", enabled, WarningOrange, onSwingOff)
        }
    }
}

@Composable
private fun DeviceInfoSettingsCard(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?
) {
    val info = state?.deviceInfo
    SectionCard(title = "设备信息") {
        SettingInfoLine(label = "设备名称", value = device.name)
        SettingInfoLine(label = "设备 SN", value = device.serialNumber)
        SettingInfoLine(label = "当前状态", value = if (state?.isOnline == true) "在线" else "离线")
        SettingInfoLine(label = "固件版本", value = info?.firmwareVersion ?: "--")
        SettingInfoLine(label = "硬件版本", value = info?.hardwareVersion ?: "--")
        info?.slot?.takeIf { it.isNotBlank() }?.let {
            SettingInfoLine(label = "运行分区", value = it)
        }
        info?.lastOtaResult?.takeIf { it.isNotBlank() }?.let {
            SettingInfoLine(label = "上次升级", value = it)
        }
    }
}

@Composable
private fun OtaCard(
    state: SolarCleanDeviceState?,
    otaCheckState: SolarCleanOtaCheckState,
    enabled: Boolean,
    onRefreshDeviceInfo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartOta: () -> Unit
) {
    val info = state?.deviceInfo
    val otaStatus = state?.otaStatus
    val latest = otaCheckState.latest
    SectionCard(title = "固件升级") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactInfo("当前版本", info?.firmwareVersion ?: "--")
            CompactInfo("硬件版本", info?.hardwareVersion ?: "--")
            CompactInfo("升级状态", otaStatus?.let { otaStatusText(it) } ?: info?.otaStatus ?: "--")
        }
        latest?.let {
            OtaUpdateInfo(
                latest = it,
                hasUpdate = otaCheckState.hasUpdate
            )
        }
        otaStatus?.let { OtaProgressLine(it) }
        otaCheckState.errorMessage?.let {
            Text(text = it, fontSize = 12.sp, color = ErrorRed)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlButton(
                text = "刷新信息",
                enabled = enabled,
                color = PrimaryBlue,
                onClick = onRefreshDeviceInfo
            )
            ControlButton(
                text = if (otaCheckState.isChecking) "检测中" else "检测更新",
                enabled = enabled && !otaCheckState.isChecking,
                color = PrimaryBlue,
                onClick = onCheckUpdate
            )
            ControlButton(
                text = "立即升级",
                enabled = enabled && otaCheckState.hasUpdate && latest?.isStartable() == true,
                color = WarningOrange,
                onClick = onStartOta
            )
        }
    }
}

@Composable
private fun SettingInfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Composable
private fun CompactInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OtaUpdateInfo(latest: OtaLatestResponse, hasUpdate: Boolean) {
    val color = if (hasUpdate) WarningOrange else OnlineGreen
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFF), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (hasUpdate) "发现新版本" else "已是最新版本",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = latest.latestVersion ?: "--",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        latest.releaseNote?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun OtaProgressLine(status: SolarCleanOtaStatus) {
    val progressText = status.progress?.let { " · $it%" }.orEmpty()
    val reasonText = status.message ?: status.reason
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.74f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${otaStatusText(status)}$progressText",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = otaStatusColor(status.status)
        )
        reasonText?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun CardShell(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { content() }
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    CardShell {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFAFBFF))))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            content()
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .size(width = 94.dp, height = 68.dp)
            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlSlider(
    kind: SliderKind,
    title: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SolarCleanControlGlyph(kind = kind, size = 28)
                Text(text = title, fontSize = 12.sp, color = TextMuted)
            }
            Text(
                text = "${value.roundToInt()}$unit",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(18.dp)
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
            },
            colors = SliderDefaults.colors(
                thumbColor = PrimaryBlue,
                activeTrackColor = PrimaryBlue,
                inactiveTrackColor = Color(0xFFE5ECF8),
                disabledThumbColor = Color(0xFFCBD5E1),
                disabledActiveTrackColor = Color(0xFFCBD5E1),
                disabledInactiveTrackColor = Color(0xFFE5E7EB)
            )
        )
    }
}

@Composable
private fun SolarCleanControlGlyph(kind: SliderKind, size: Int = 30) {
    Box(
        modifier = Modifier
            .size(size.dp)
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
            modifier = Modifier.size((size - 12).dp),
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
private fun ControlButton(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .height(46.dp)
            .fillMaxWidth(0.47f),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFFE5E7EB),
            disabledContentColor = TextMuted
        )
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

private fun waterLevelText(value: Int): String {
    return when (value) {
        0 -> "低"
        1 -> "正常"
        2 -> "高"
        else -> value.toString()
    }
}

private fun mqttStatusText(state: SolarCleanDeviceState?): String {
    return when (state?.mqttConnected) {
        true -> "正常"
        false -> state.mqttLastError?.let { "错误 $it" } ?: "断开"
        null -> "--"
    }
}

private fun otaStatusText(status: SolarCleanOtaStatus): String {
    return when (status.status.uppercase()) {
        "STARTED" -> "准备升级"
        "DOWNLOADING" -> "下载中"
        "VERIFYING" -> "校验中"
        "READY_TO_REBOOT",
        "REBOOTING" -> "准备重启"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> status.status
    }
}

private fun otaStatusColor(status: String): Color {
    return when (status.uppercase()) {
        "SUCCESS" -> OnlineGreen
        "FAILED",
        "ROLLBACK" -> ErrorRed
        "DOWNLOADING",
        "VERIFYING",
        "STARTED",
        "READY_TO_REBOOT",
        "REBOOTING" -> PrimaryBlue
        else -> TextPrimary
    }
}

private fun OtaLatestResponse.isStartable(): Boolean =
    !latestVersion.isNullOrBlank() &&
            !hardwareVersion.isNullOrBlank() &&
            fileSize != null &&
            !sha256.isNullOrBlank() &&
            !downloadUrl.isNullOrBlank()

private fun previewSolarCleanState(serialNumber: String): SolarCleanDeviceState {
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

private fun previewOtaCheckState(): SolarCleanOtaCheckState {
    return SolarCleanOtaCheckState(
        latest = OtaLatestResponse(
            hasUpdate = true,
            latestVersion = "1.0.4",
            hardwareVersion = "HW-A",
            fileSize = 245760,
            sha256 = "xxxxxxxx",
            downloadUrl = "https://example.com/firmware/HW-A/v1.0.4/app.bin",
            releaseNote = "修复电机保护逻辑"
        ),
        hasUpdate = true
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
        ),
        showSettings = true
    )
}
