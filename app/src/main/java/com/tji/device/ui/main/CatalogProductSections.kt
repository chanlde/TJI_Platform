package com.tji.device.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.CatalogBoundDevice
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@Composable
internal fun CatalogProductEntryCard(
    productName: String,
    productCode: String,
    deviceCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PayloadDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = PayloadColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PayloadColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(PayloadDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = productName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PlatformInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$deviceCount 台已绑定设备 · 仅查看",
                style = MaterialTheme.typography.bodyMedium,
                color = PlatformMuted
            )
            Text(
                text = productCode,
                style = MaterialTheme.typography.bodySmall,
                color = PlatformMuted
            )
        }
    }
}

@Composable
internal fun CatalogProductDevicesScreen(
    productCode: String,
    devices: List<CatalogBoundDevice>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PayloadDimens.ScreenPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item {
            Text(
                text = "此产品暂无 App 专用控制模块，设备信息仅供查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = PlatformMuted
            )
        }
        items(
            items = devices.filter { it.productCode == productCode },
            key = { it.serialNumber }
        ) { device ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PayloadDimens.CardRadius),
                colors = CardDefaults.cardColors(containerColor = PayloadColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, PayloadColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(PayloadDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatformInk
                    )
                    Text(
                        text = device.serialNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlatformMuted
                    )
                }
            }
        }
    }
}
