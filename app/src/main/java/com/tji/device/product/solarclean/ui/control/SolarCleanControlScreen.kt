package com.tji.device.product.solarclean.ui.control

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedbackStatus
import com.tji.device.product.solarclean.viewmodel.SolarCleanOtaCheckState
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiCardShell
import com.tji.device.ui.components.TjiControlSlider
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiMetricTile
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.components.productSceneRes
import com.tji.device.product.solarclean.ui.icon.PumpPressure
import com.tji.device.product.solarclean.ui.icon.SprayAngle
import com.tji.device.product.solarclean.ui.icon.SwingSpeed
import com.tji.device.ui.theme.TjiBackground
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiPrimarySoft
import com.tji.device.ui.theme.TjiSurfaceSoft
import com.tji.device.ui.theme.TjiTextMuted
import com.tji.device.ui.theme.TjiTextPrimary
import com.tji.device.ui.theme.TjiTextSecondary
import com.tji.device.ui.theme.TjiWarning
import com.tji.network.data.OtaLatestResponse
import com.tji.device.ui.icon.common.Eye
import com.tji.device.ui.icon.common.EyeOff
import kotlin.math.roundToInt
import androidx.core.content.edit

private const val CONTROL_PANEL_PREFS = "solar_clean_control_panel"
private const val CONTROL_PANEL_EXPANDED_PREFIX = "controls_expanded_"
private const val TELEMETRY_PANEL_EXPANDED_PREFIX = "telemetry_expanded_"
private const val OTA_SUCCESS_NOTICE_VISIBLE_MS = 5_000L

private val RenameEditIcon: ImageVector
    get() = ImageVector.Builder(
        name = "RenameEdit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color(0xFF1677FF))
        ) {
            moveTo(5f, 17.2f)
            verticalLineTo(19f)
            horizontalLineTo(6.8f)
            lineTo(17.1f, 8.7f)
            lineTo(15.3f, 6.9f)
            lineTo(5f, 17.2f)
            close()
            moveTo(18.5f, 7.3f)
            lineTo(16.7f, 5.5f)
            lineTo(17.7f, 4.5f)
            curveTo(18.1f, 4.1f, 18.8f, 4.1f, 19.2f, 4.5f)
            lineTo(19.5f, 4.8f)
            curveTo(19.9f, 5.2f, 19.9f, 5.9f, 19.5f, 6.3f)
            lineTo(18.5f, 7.3f)
            close()
        }
    }.build()

@Composable
fun SolarCleanControlScreen(
    device: BoundAccountDevice,
    showSettings: Boolean = false,
    onRenameDevice: (BoundAccountDevice, String) -> Unit = { _, _ -> },
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
    val commandFeedback by viewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(SolarCleanCommandFeedback()) }
    }

    LaunchedEffect(viewModel, device.serialNumber) {
        viewModel?.requestDeviceInfo(device.serialNumber)
    }

    if (showSettings) {
        SolarCleanSettingsPage(
            device = device,
            state = displayState,
            otaCheckState = otaCheckState,
            commandFeedback = commandFeedback,
            enabled = viewModel != null,
            onRenameDevice = { newName -> onRenameDevice(device, newName) },
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
        val controlsEnabled = viewModel != null && displayState?.isOnline == true
        SolarCleanControlPage(
            device = device,
            state = displayState,
            enabled = controlsEnabled,
            commandFeedback = commandFeedback,
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
            .background(TjiBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PrimaryControlsCard(
                enabled = enabled,
                expanded = controlsExpanded,
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
private fun SolarCleanSettingsPage(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?,
    otaCheckState: SolarCleanOtaCheckState,
    commandFeedback: SolarCleanCommandFeedback,
    enabled: Boolean,
    onRenameDevice: (String) -> Unit,
    onRefreshDeviceInfo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartOta: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TjiBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DeviceInfoSettingsCard(
                device = device,
                state = state,
                onRenameDevice = onRenameDevice
            )
        }
        item {
            OtaCard(
                state = state,
                otaCheckState = otaCheckState,
                commandFeedback = commandFeedback,
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
    TjiCardShell {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .background(
                    brush = Brush.horizontalGradient(listOf(Color.White, TjiSurfaceSoft)),
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
                    color = TjiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.serialNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TjiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TjiStatusText(
                    text = if (online) "在线" else "离线",
                    color = if (online) TjiOnline else TjiError
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(
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
                    tint = TjiPrimary
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
                TjiMetricTile("MQTT", mqttStatusText(state))
            }
        }
    }
}

@Composable
private fun PrimaryControlsCard(
    enabled: Boolean,
    expanded: Boolean,
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
    var pumpPressure by remember { mutableFloatStateOf(50f) }
    var sprayAngle by remember { mutableFloatStateOf(20f) }
    var swingSpeed by remember { mutableFloatStateOf(50f) }

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
                        tint = TjiPrimary
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
                ControlButton("开泵", enabled, TjiPrimary, onPumpOn)
                ControlButton("关泵", enabled, TjiWarning, onPumpOff)
                ControlButton("摆动开", enabled, TjiPrimary, onSwingOn)
                ControlButton("摆动关", enabled, TjiWarning, onSwingOff)
            }
        }
    }
}

@Composable
private fun CommandFeedbackBadge(feedback: SolarCleanCommandFeedback) {
    val color = when (feedback.status) {
        SolarCleanCommandFeedbackStatus.Success -> TjiOnline
        SolarCleanCommandFeedbackStatus.Failed,
        SolarCleanCommandFeedbackStatus.Timeout -> TjiError
        SolarCleanCommandFeedbackStatus.Pending,
        SolarCleanCommandFeedbackStatus.Idle -> TjiTextMuted
    }
    TjiFeedbackBadge(
        text = feedback.text,
        color = color,
        radius = 10.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 5.dp
    )
}

@Composable
private fun DeviceInfoSettingsCard(
    device: BoundAccountDevice,
    state: SolarCleanDeviceState?,
    onRenameDevice: (String) -> Unit
) {
    val info = state?.deviceInfo
    TjiSectionCard(title = "设备信息") {
        DeviceNameSettingLine(
            value = device.name,
            onRenameDevice = onRenameDevice
        )
        SettingInfoLine(label = "设备 SN", value = device.serialNumber)
        SettingInfoLine(label = "当前状态", value = if (state?.isOnline == true) "在线" else "离线")
        SettingInfoLine(label = "固件版本", value = info?.firmwareVersion ?: "--")
        SettingInfoLine(label = "内部版本", value = info?.firmwareInnerVersion?.toString() ?: "--")
        SettingInfoLine(label = "硬件版本", value = info?.hardwareVersion ?: "--")
    }
}

@Composable
private fun OtaCard(
    state: SolarCleanDeviceState?,
    otaCheckState: SolarCleanOtaCheckState,
    commandFeedback: SolarCleanCommandFeedback,
    enabled: Boolean,
    onRefreshDeviceInfo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartOta: () -> Unit
) {
    val info = state?.deviceInfo
    val otaStatus = state?.otaStatus
    val latest = otaCheckState.latest
    val deviceReachedLatest = isDeviceAtLatest(info, latest)
    val effectiveOtaStatus = otaStatus?.toCompletedIfDeviceReachedLatest(deviceReachedLatest)
    val successNoticeKey = "${state?.serialNumber}:${latest?.innerVersion}:${info?.firmwareInnerVersion}"
    var showSuccessNotice by remember(successNoticeKey) { mutableStateOf(true) }
    LaunchedEffect(successNoticeKey, effectiveOtaStatus?.status) {
        if (effectiveOtaStatus?.status?.normalizedOtaStatus() == "SUCCESS") {
            showSuccessNotice = true
            kotlinx.coroutines.delay(OTA_SUCCESS_NOTICE_VISIBLE_MS)
            showSuccessNotice = false
        }
    }
    val hasUpdate = otaCheckState.hasUpdate && !deviceReachedLatest
    val isOtaBusy = effectiveOtaStatus?.isOtaBusy() == true
    val displayOtaStatus = effectiveOtaStatus?.takeUnless {
        it.status.normalizedOtaStatus() == "SUCCESS" && !showSuccessNotice
    }
    TjiSectionCard(
        title = "固件升级",
        trailing = { CommandFeedbackBadge(commandFeedback) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactInfo("当前版本", info?.firmwareVersion ?: "--")
            CompactInfo("内部版本", info?.firmwareInnerVersion?.toString() ?: "--")
            CompactInfo("硬件版本", info?.hardwareVersion ?: "--")
            CompactInfo("升级状态", displayOtaStatus?.let { otaStatusText(it) } ?: "空闲")
        }
        latest?.let {
            OtaUpdateInfo(
                latest = it,
                hasUpdate = hasUpdate
            )
        }
        displayOtaStatus?.takeIf { it.shouldShowOtaProgress() }?.let { OtaProgressLine(it) }
        otaCheckState.errorMessage?.let {
            Text(text = it, fontSize = 12.sp, color = TjiError)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlButton(
                text = "刷新信息",
                enabled = enabled,
                color = TjiPrimary,
                onClick = onRefreshDeviceInfo
            )
            ControlButton(
                text = if (otaCheckState.isChecking) "检测中" else "检测更新",
                enabled = enabled && !otaCheckState.isChecking,
                color = TjiPrimary,
                onClick = onCheckUpdate
            )
            ControlButton(
                text = "立即升级",
                enabled = enabled && !isOtaBusy && hasUpdate && latest?.isStartable() == true,
                color = TjiWarning,
                onClick = onStartOta
            )
        }
    }
}

@Composable
private fun DeviceNameSettingLine(
    value: String,
    onRenameDevice: (String) -> Unit
) {
    var editing by remember(value) { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    var hasFocused by remember(value) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun commitRename() {
        val nextName = draft.trim()
        editing = false
        hasFocused = false
        if (nextName.isBlank()) {
            draft = value
            return
        }
        if (nextName != value) {
            onRenameDevice(nextName)
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "设备名称",
            fontSize = 12.sp,
            color = TjiTextMuted,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.weight(1.5f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TjiTextPrimary
                        ),
                        cursorBrush = SolidColor(TjiPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { commitRename() }
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    hasFocused = true
                                } else if (hasFocused && editing) {
                                    commitRename()
                                }
                            }
                            .fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TjiPrimary)
                    )
                }
            } else {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TjiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            draft = value
                            editing = true
                        }
                )
                IconButton(
                    onClick = {
                        draft = value
                        editing = true
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = RenameEditIcon,
                        contentDescription = "修改设备名",
                        tint = TjiPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
            color = TjiTextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TjiTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Composable
private fun CompactInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 11.sp, color = TjiTextMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TjiTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OtaUpdateInfo(latest: OtaLatestResponse, hasUpdate: Boolean) {
    val color = if (hasUpdate) TjiWarning else TjiOnline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TjiSurfaceSoft, RoundedCornerShape(10.dp))
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
                text = latest.innerVersion?.let { "内部 $it" } ?: latest.latestVersion ?: "--",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TjiTextPrimary
            )
        }
        latest.releaseNote?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 12.sp, color = TjiTextMuted)
        }
    }
}

@Composable
private fun OtaProgressLine(status: SolarCleanOtaStatus) {
    val progress = status.progress?.coerceIn(0, 100)
    val reasonText = status.message ?: status.reason
    val color = otaStatusColor(status.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.74f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = otaProgressTitle(status),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            progress?.let {
                Text(
                    text = "$it%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TjiTextSecondary
                )
            }
        }
        progress?.let {
            LinearProgressIndicator(
                progress = { it / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = color,
                trackColor = TjiPrimarySoft
            )
        }
        reasonText?.takeIf { it.isNotBlank() }?.let { rawMessage ->
            otaUserMessage(rawMessage)?.let {
                Text(text = it, fontSize = 12.sp, color = TjiTextMuted)
            }
        }
    }
}

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
                Text(text = title, fontSize = 12.sp, color = TjiTextMuted)
            }
            Text(
                text = "${value.roundToInt()}$unit",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TjiTextPrimary
            )
        }
        TjiControlSlider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

@Composable
private fun SolarCleanControlGlyph(kind: SliderKind, size: Int = 30) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(TjiPrimarySoft, RoundedCornerShape(10.dp)),
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
            tint = TjiPrimary
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
    TjiActionButton(
        text = text,
        enabled = enabled,
        color = color,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.47f)
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
    return when (status.status.normalizedOtaStatus()) {
        "IDLE",
        "NONE" -> "空闲"
        "STARTED" -> "准备升级"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> "--"
    }
}

private fun otaStatusColor(status: String): Color {
    return when (status.normalizedOtaStatus()) {
        "SUCCESS" -> TjiOnline
        "FAILED",
        "ROLLBACK" -> TjiError
        "DOWNLOADING",
        "VERIFYING",
        "STARTED",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> TjiPrimary
        else -> TjiTextPrimary
    }
}

private fun otaProgressTitle(status: SolarCleanOtaStatus): String {
    return when (status.status.normalizedOtaStatus()) {
        "SUCCESS" -> "升级完成"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "升级完成，等待设备重启"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "升级失败，已回滚"
        "STARTED" -> "设备已响应，准备升级"
        else -> "正在升级"
    }
}

private fun otaStateText(status: String?): String {
    return when (status?.normalizedOtaStatus()) {
        null,
        "",
        "UNKNOWN" -> "--"
        "IDLE" -> "空闲"
        "NONE" -> "无"
        "STARTED" -> "准备升级"
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> "正在升级"
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> "等待重启"
        "SUCCESS" -> "升级成功"
        "FAILED" -> "升级失败"
        "ROLLBACK" -> "已回滚"
        else -> status
    }
}

private fun SolarCleanOtaStatus.shouldShowOtaProgress(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "IDLE",
        "NONE",
        "UNKNOWN" -> false
        else -> true
    }
}

private fun SolarCleanOtaStatus.isOtaBusy(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "STARTED",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING",
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING" -> true
        else -> false
    }
}

private fun SolarCleanOtaStatus.toCompletedIfDeviceReachedLatest(
    deviceReachedLatest: Boolean
): SolarCleanOtaStatus {
    if (!deviceReachedLatest) return this
    return when (status.normalizedOtaStatus()) {
        "READY_TO_REBOOT",
        "PENDING_REBOOT",
        "REBOOTING",
        "DOWNLOADING",
        "VERIFYING",
        "INSTALLING" -> copy(status = "SUCCESS", progress = 100, message = null, reason = null)
        else -> this
    }
}

private fun isDeviceAtLatest(
    info: SolarCleanDeviceInfo?,
    latest: OtaLatestResponse?
): Boolean {
    val currentInner = info?.firmwareInnerVersion
    val latestInner = latest?.innerVersion
    if (currentInner != null && latestInner != null) {

        return currentInner >= latestInner
    }
    val currentVersion = info?.firmwareVersion
    val latestVersion = latest?.latestVersion
    return !currentVersion.isNullOrBlank() &&
            !latestVersion.isNullOrBlank() &&
            currentVersion == latestVersion
}

private fun otaUserMessage(raw: String): String? {
    val normalized = raw.normalizedOtaStatus()
    if (normalized != raw && normalized != "UNKNOWN") return null
    return when (normalized) {
        "IDLE",
        "NONE",
        "UNKNOWN" -> null
        else -> raw
    }
}

private fun String.normalizedOtaStatus(): String {
    return trim()
        .uppercase()
        .removePrefix("OTA_")
}

private fun OtaLatestResponse.isStartable(): Boolean =
    !latestVersion.isNullOrBlank() &&
            !downloadUrl.isNullOrBlank() &&
            fileSize != null &&
            !sha256.isNullOrBlank()

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
            firmwareInnerVersion = 3,
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
