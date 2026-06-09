package com.tji.device.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

private enum class MetricKind {
    Device,
    Online,
    Type
}

@Composable
internal fun PlatformHomeHeader(
    totalDeviceCount: Int,
    onlineDeviceCount: Int,
    productCount: Int,
    onSettingsClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "设备平台首页",
                    style = MaterialTheme.typography.displaySmall,
                    color = PlatformInk,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = when {
                        totalDeviceCount > 0 -> "统一查看账号下各产品设备，并按设备进入对应控制台。"
                        else -> "暂无绑定设备，请先添加或联系管理员开通。"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = PlatformMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
                    .border(1.dp, PayloadColors.Border, RoundedCornerShape(PayloadDimens.ControlRadius))
                    .background(PayloadColors.Surface)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = PlatformInk
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlatformMetaCard(
                title = "绑定设备",
                value = "$totalDeviceCount",
                unit = "台",
                kind = MetricKind.Device,
                modifier = Modifier.weight(1f)
            )
            PlatformMetaCard(
                title = "在线设备",
                value = "$onlineDeviceCount",
                unit = "台",
                kind = MetricKind.Online,
                modifier = Modifier.weight(1f)
            )
            PlatformMetaCard(
                title = "设备类型",
                value = "$productCount",
                unit = "类",
                kind = MetricKind.Type,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricGlyph(kind: MetricKind) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(PayloadColors.PrimarySoft, RoundedCornerShape(PayloadDimens.ControlRadius)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            MetricKind.Device -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width((22 - it * 3).dp)
                                .height(5.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            MetricKind.Online -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(16.dp)
                            .background(PlatformBlue, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .background(PlatformBlue, RoundedCornerShape(4.dp))
                    )
                }
            }

            MetricKind.Type -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.width(22.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformMetaCard(
    title: String,
    value: String,
    unit: String,
    kind: MetricKind,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PayloadDimens.CardRadius))
            .border(1.dp, PayloadColors.Border, RoundedCornerShape(PayloadDimens.CardRadius))
            .background(PayloadColors.Surface)
            .padding(PayloadDimens.CardPadding)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MetricGlyph(kind = kind)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = PayloadColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = PlatformBlue,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = PayloadColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlatformMetaCardPreview() {
    PlatformMetaCard(
        title = "我的设备",
        value = "12",
        unit = "台",
        kind = MetricKind.Device,
        modifier = Modifier.width(120.dp)
    )
}
