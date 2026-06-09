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
import com.tji.device.ui.components.PayloadSlider
import com.tji.device.ui.floating.FloatingWindowAppearance
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingsSheet(
    account: String,
    deviceCount: Int,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PayloadColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "管理悬浮窗、权限和当前应用信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PayloadColors.TextSecondary
                )
            }

            SettingsGroup {
                SettingSwitchRow(
                    title = "悬浮窗",
                    description = if (hasFloatingWindowPermission) {
                        "开启后可在其他页面快速查看当前产品控制面板"
                    } else {
                        "需要先授予悬浮窗权限"
                    },
                    checked = isFloatingWindowEnabled,
                    onCheckedChange = { enabled ->
                        onFloatingWindowEnabledChange(enabled)
                        if (enabled && !hasFloatingWindowPermission) {
                            onOpenFloatingWindowPermission()
                        }
                    }
                )
                HorizontalDivider(color = PayloadColors.Border)
                SettingActionRow(
                    title = "悬浮窗权限",
                    value = if (hasFloatingWindowPermission) "已授权" else "未授权",
                    actionText = if (hasFloatingWindowPermission) null else "去授权",
                    onAction = onOpenFloatingWindowPermission
                )
                HorizontalDivider(color = PayloadColors.Border)
                FloatingWindowOpacityRow(
                    alpha = floatingWindowBackgroundAlpha,
                    onAlphaChange = { alpha ->
                        FloatingWindowAppearance.setBackgroundAlpha(context, alpha)
                    }
                )
            }

            SettingsGroup {
                SettingInfoRow(
                    title = "当前账号",
                    value = account.ifBlank { "未获取" }
                )
                HorizontalDivider(color = PayloadColors.Border)
                SettingInfoRow(
                    title = "绑定设备",
                    value = "$deviceCount 台"
                )
            }

            SettingsGroup {
                SettingInfoRow(
                    title = "当前版本",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
        }
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
        PayloadSlider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = 0f..1f,
            color = PayloadColors.Primary
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
