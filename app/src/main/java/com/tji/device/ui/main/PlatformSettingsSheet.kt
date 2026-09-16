package com.tji.device.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tji.device.BuildConfig
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.ui.components.TjiControlSlider
import com.tji.device.ui.floating.FloatingWindowAppearance
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun PlatformSettingsSheet(
    account: String?,
    deviceCount: Int,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
    connectionMode: FireBucketConnectionMode,
    onConnectionModeChange: (FireBucketConnectionMode) -> Unit,
    onFloatingWindowEnabledChange: (Boolean) -> Unit,
    onOpenFloatingWindowPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        FloatingWindowAppearance.load(context)
    }
    val floatingWindowBackgroundAlpha by FloatingWindowAppearance.backgroundAlpha.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PayloadColors.Surface,
        shape = RoundedCornerShape(topStart = PayloadDimens.CardRadius, topEnd = PayloadDimens.CardRadius)
    ) {
        PlatformSettingsContent(
            account = account,
            deviceCount = deviceCount,
            isFloatingWindowEnabled = isFloatingWindowEnabled,
            hasFloatingWindowPermission = hasFloatingWindowPermission,
            onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
            onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
            floatingWindowBackgroundAlpha = floatingWindowBackgroundAlpha,
            connectionMode = connectionMode,
            onConnectionModeChange = onConnectionModeChange
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun PlatformSettingsContent(
    account: String?,
    deviceCount: Int,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
    onFloatingWindowEnabledChange: (Boolean) -> Unit,
    onOpenFloatingWindowPermission: () -> Unit,
    floatingWindowBackgroundAlpha: Float,
    connectionMode: FireBucketConnectionMode,
    onConnectionModeChange: (FireBucketConnectionMode) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsSheetHeader()
        SettingsGroup {
            ConnectionModeRow(mode = connectionMode, onModeChange = onConnectionModeChange)
        }
        FloatingWindowSettingsGroup(
            isEnabled = isFloatingWindowEnabled,
            hasPermission = hasFloatingWindowPermission,
            alpha = floatingWindowBackgroundAlpha,
            onEnabledChange = onFloatingWindowEnabledChange,
            onOpenPermission = onOpenFloatingWindowPermission,
            onAlphaChange = { FloatingWindowAppearance.setBackgroundAlpha(context, it) }
        )
        AccountSettingsGroup(account = account, deviceCount = deviceCount)
        VersionSettingsGroup()
    }
}

@Composable
private fun SettingsSheetHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            color = PayloadColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "管理控制链路、悬浮窗和当前应用信息",
            style = MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextSecondary
        )
    }
}

@Composable
private fun FloatingWindowSettingsGroup(
    isEnabled: Boolean,
    hasPermission: Boolean,
    alpha: Float,
    onEnabledChange: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    onAlphaChange: (Float) -> Unit
) {
    SettingsGroup {
        SettingSwitchRow(
            title = "悬浮窗",
            description = if (hasPermission) {
                "开启后可在其他页面快速查看当前产品控制面板"
            } else {
                "需要先授予悬浮窗权限"
            },
            checked = isEnabled,
            onCheckedChange = { enabled ->
                onEnabledChange(enabled)
                if (enabled && !hasPermission) onOpenPermission()
            }
        )
        HorizontalDivider(color = PayloadColors.Border)
        SettingActionRow(
            title = "悬浮窗权限",
            value = if (hasPermission) "已授权" else "未授权",
            actionText = if (hasPermission) null else "去授权",
            onAction = onOpenPermission
        )
        HorizontalDivider(color = PayloadColors.Border)
        FloatingWindowOpacityRow(alpha = alpha, onAlphaChange = onAlphaChange)
    }
}

@Composable
private fun AccountSettingsGroup(account: String?, deviceCount: Int) {
    if (account == null) return
    SettingsGroup {
        SettingInfoRow(title = "当前账号", value = account.ifBlank { "未获取" })
        HorizontalDivider(color = PayloadColors.Border)
        SettingInfoRow(title = "绑定设备", value = "$deviceCount 台")
    }
}

@Composable
private fun ConnectionModeRow(
    mode: FireBucketConnectionMode,
    onModeChange: (FireBucketConnectionMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "控制链路",
            style = MaterialTheme.typography.titleMedium,
            color = PayloadColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (mode == FireBucketConnectionMode.CLOUD) {
                "通过 4G 云端发送控制命令"
            } else {
                "通过当前 Wi-Fi 连接的数传设备发送控制命令"
            },
            style = MaterialTheme.typography.bodySmall,
            color = PayloadColors.TextSecondary
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                FireBucketConnectionMode.CLOUD to "4G",
                FireBucketConnectionMode.DIRECT_LINK to "数传"
            ).forEachIndexed { index, (itemMode, label) ->
                SegmentedButton(
                    selected = mode == itemMode,
                    onClick = { onModeChange(itemMode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun VersionSettingsGroup() {
    SettingsGroup {
        SettingInfoRow(
            title = "当前版本",
            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.FLAVOR}"
        )
    }
}

@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PayloadDimens.CardRadius))
            .background(PayloadColors.SurfaceSoft)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PayloadColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = PayloadColors.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun FloatingWindowOpacityRow(
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingTextPair(
            title = "悬浮窗透明度",
            value = "${(alpha * 100).toInt()}%"
        )
        TjiControlSlider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = 0f..1f
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    value: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingTextPair(
            title = title,
            value = value,
            modifier = Modifier.weight(1f)
        )
        if (actionText != null) {
            TextButton(onClick = onAction) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun SettingInfoRow(
    title: String,
    value: String
) {
    SettingTextPair(
        title = title,
        value = value,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    )
}

@Composable
private fun SettingTextPair(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
