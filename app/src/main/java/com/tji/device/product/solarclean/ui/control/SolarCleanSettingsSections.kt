package com.tji.device.product.solarclean.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.product.solarclean.viewmodel.SolarCleanOtaCheckState
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiWarning
import com.tji.network.data.OtaLatestResponse

private const val OTA_SUCCESS_NOTICE_VISIBLE_MS = 5_000L

private val RenameEditIcon: ImageVector
    get() = ImageVector.Builder(
        name = "RenameEdit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFF1677FF))) {
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
internal fun SolarCleanSettingsPage(
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
            .background(PayloadColors.Background)
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
                color = PayloadColors.Primary,
                onClick = onRefreshDeviceInfo
            )
            ControlButton(
                text = if (otaCheckState.isChecking) "检测中" else "检测更新",
                enabled = enabled && !otaCheckState.isChecking,
                color = PayloadColors.Primary,
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
            color = PayloadColors.TextMuted,
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
                            color = PayloadColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(PayloadColors.Primary),
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
                            .background(PayloadColors.Primary)
                    )
                }
            } else {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayloadColors.TextPrimary,
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
                        tint = PayloadColors.Primary,
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
            color = PayloadColors.TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Composable
private fun CompactInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 11.sp, color = PayloadColors.TextMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
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
            .background(PayloadColors.SurfaceSoft, RoundedCornerShape(PayloadDimens.ControlRadius))
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
                color = PayloadColors.TextPrimary
            )
        }
        latest.releaseNote?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, fontSize = 12.sp, color = PayloadColors.TextMuted)
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
            .background(PayloadColors.SurfaceSoft, RoundedCornerShape(PayloadDimens.ControlRadius))
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
                    color = PayloadColors.TextSecondary
                )
            }
        }
        progress?.let {
            LinearProgressIndicator(
                progress = { it / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = color,
                trackColor = PayloadColors.PrimarySoft
            )
        }
        reasonText?.takeIf { it.isNotBlank() }?.let { rawMessage ->
            otaUserMessage(rawMessage)?.let {
                Text(text = it, fontSize = 12.sp, color = PayloadColors.TextMuted)
            }
        }
    }
}
