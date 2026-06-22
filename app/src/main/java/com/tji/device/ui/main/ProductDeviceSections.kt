package com.tji.device.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.components.productEmptyIllustrationRes
import com.tji.device.ui.icon.product.productIconVector
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

/**
 * 某产品线下先选设备再进控制台：**可选设备清单以登录接口返回的绑定设备列表（boundDeviceRows / 旧 bucketsns）（[knownLinks]）为准**，
 * 这是账号名下设备，与 MQTT 无关；MQTT 仅在有数据时补充在线/子设备等实时态。
 */
@Composable
internal fun ProductDevicesScreen(
    productType: ProductType,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    knownLinks: List<BoundAccountDevice>,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val scopedLive = runtimeDevices.filter { it.productType == productType }
    val accountDevices = knownLinks.filter { it.productType == productType }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PayloadDimens.ScreenPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        when {
            accountDevices.isNotEmpty() -> {
                items(accountDevices, key = { it.serialNumber }) { info ->
                    val live = scopedLive.firstOrNull { it.serialNumber == info.serialNumber }
                    PlatformDeviceCard(
                        device = info,
                        live = live,
                        onClick = { onLinkSelected(info) }
                    )
                }
            }

            else -> {
                item {
                    CompactEmptyCard(
                        productType = productType,
                        knownDeviceCount = 0
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactEmptyCard(
    productType: ProductType,
    knownDeviceCount: Int
) {
    Card(
        shape = RoundedCornerShape(PayloadDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = PayloadColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PayloadColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PayloadDimens.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = productEmptyIllustrationRes(productType)),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Text(
                text = if (knownDeviceCount > 0) "正在连接设备" else "暂无设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextPrimary
            )
            Text(
                text = if (knownDeviceCount > 0) "已进入 ${productTitle(productType)}，正在等待设备上线" else productTitle(productType),
                style = MaterialTheme.typography.bodyMedium,
                color = PayloadColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PlatformDeviceCard(
    device: BoundAccountDevice,
    live: ProductDeviceRuntimeSnapshot?,
    onClick: () -> Unit
) {
    val accentColors = when (device.productType) {
        ProductType.FireBucket -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
        ProductType.SolarClean -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
        ProductType.DropperSixStage -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
        ProductType.RadioDetection -> listOf(PayloadColors.Surface, PayloadColors.PrimarySoft)
        ProductType.Speaker -> listOf(PayloadColors.Surface, PayloadColors.WarningSoft)
        ProductType.BreakWindowProjectile -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
        ProductType.Searchlight -> listOf(PayloadColors.Surface, PayloadColors.PrimarySoft)
    }
    val subDeviceCount = live?.childCount
    val isOnline = live?.isOnline == true
    val displayName = device.name
    val subtitle = when {
        device.productType == ProductType.RadioDetection -> device.serialNumber
        subDeviceCount != null -> "$subDeviceCount 台子设备"
        else -> device.serialNumber.takeUnless { it.equals(displayName, ignoreCase = true) }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PayloadDimens.CardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = PayloadColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PayloadColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PayloadDimens.CardRadius))
                .background(
                    brush = Brush.verticalGradient(accentColors),
                    shape = RoundedCornerShape(PayloadDimens.CardRadius)
                )
                .padding(PayloadDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
                    .background(PayloadColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = productIconVector(device.productType),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = productAccentColor(device.productType)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PayloadColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            InlineDeviceStatus(isOnline = isOnline)

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = PayloadColors.TextSecondary
            )
        }
    }
}

@Composable
private fun InlineDeviceStatus(isOnline: Boolean) {
    TjiOnlineStatus(isOnline = isOnline)
}

private fun productTitle(productType: ProductType): String {
    return ProductCatalog.definitionOf(productType).displayName
}
