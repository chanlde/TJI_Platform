package com.tji.device.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.ui.icon.common.minimize
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import com.tji.device.product.firebucket.ui.floating.FireBucketFloatingPanel
import com.tji.device.product.solarclean.ui.floating.SolarCleanFloatingPanel

@Composable
fun ExpandedCard(
    productType: ProductType,
    link: FloatingLinkSummary?,
    allSwitches: List<FloatingSwitchSummary>,
    currentSwitchIndex: Int,
    onSwitchSelected: (Int) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onSwitchQuickToggle: (String, FloatingSwitchSummary, Boolean) -> Unit,
    onMove: (Float, Float) -> Unit
) {
    val switch = allSwitches.getOrNull(currentSwitchIndex)

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 280.dp, minHeight = 0.dp)
            .wrapContentHeight()
            .dragGesture(onMove)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.58f),
                        Color.White.copy(alpha = 0.58f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
            FloatingWindowHeader(
                productType = productType,
                link = link,
                onMinimize = onMinimize,
                onClose = onClose
            )

            when (productType) {
                ProductType.FireBucket -> FireBucketFloatingPanel(
                    link = link,
                    switch = switch,
                    allSwitches = allSwitches,
                    currentSwitchIndex = currentSwitchIndex,
                    onSwitchSelected = onSwitchSelected,
                    onSwitchQuickToggle = onSwitchQuickToggle
                )
                ProductType.SolarClean -> SolarCleanFloatingPanel(link = link)
            }
        }
    }
}

@Composable
private fun FloatingWindowHeader(
    productType: ProductType,
    link: FloatingLinkSummary?,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val title = if (productType == ProductType.SolarClean) {
        link?.name ?: link?.serialNumber ?: ProductCatalog.definitionOf(productType).displayName
    } else {
        ProductCatalog.definitionOf(productType).displayName
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProductFloatingGlyph(
                productType = productType,
                compact = true
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (productType == ProductType.SolarClean) {
                FloatingStatusBadge(isOnline = link?.isOnline == true)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = minimize,
                    contentDescription = "收起",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingStatusBadge(isOnline: Boolean) {
    val color = if (isOnline) Color(0xFF16A34A) else Color(0xFFFF4D4F)
    val background = if (isOnline) Color(0xFFEAF8EF) else Color(0xFFFFEAEA)
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isOnline) "在线" else "离线",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
