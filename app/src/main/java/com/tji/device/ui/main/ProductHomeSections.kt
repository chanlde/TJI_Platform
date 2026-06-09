package com.tji.device.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.ui.components.productSceneRes
import com.tji.device.ui.icon.product.productIconVector
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

internal val PlatformHomeBackground = PayloadColors.Background
internal val PlatformInk = PayloadColors.TextPrimary
internal val PlatformMuted = PayloadColors.TextSecondary
internal val PlatformBlue = PayloadColors.Primary

@Composable
internal fun ProductHome(
    onProductSelected: (ProductType) -> Unit,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    boundAccountDevices: List<BoundAccountDevice>,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onlineCount = boundAccountDevices.count { accountDevice ->
        runtimeDevices.any {
            it.serialNumber == accountDevice.serialNumber &&
                it.productType == accountDevice.productType &&
                it.isOnline
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PlatformHomeBackground),
        contentPadding = PaddingValues(horizontal = PayloadDimens.ScreenPadding, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            PlatformHomeHeader(
                totalDeviceCount = boundAccountDevices.size,
                onlineDeviceCount = onlineCount,
                productCount = ProductCatalog.allTypes.size,
                onSettingsClick = onSettingsClick
            )
        }
        item {
            Text(
                text = "产品概览",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PlatformInk,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val products = ProductCatalog.allTypes
                if (maxWidth >= 620.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
                    ) {
                        products.forEach { productType ->
                            ProductEntryCard(
                                productType = productType,
                                selected = false,
                                onClick = { onProductSelected(productType) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)) {
                        products.forEach { productType ->
                            ProductEntryCard(
                                productType = productType,
                                selected = false,
                                onClick = { onProductSelected(productType) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEntryCard(
    productType: ProductType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val definition = ProductCatalog.definitionOf(productType)
    val shape = RoundedCornerShape(PayloadDimens.CardRadius)
    val cardHeight = 210.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactCard = maxWidth < 420.dp
        val imageScale = if (maxWidth > 480.dp) {
            ContentScale.Fit
        } else {
            ContentScale.Crop
        }
        val maskStops = if (isCompactCard) {
            arrayOf(
                0.00f to PayloadColors.Surface,
                0.42f to PayloadColors.Surface.copy(alpha = 0.96f),
                0.58f to PayloadColors.Surface.copy(alpha = 0.64f),
                0.72f to PayloadColors.Surface.copy(alpha = 0.18f),
                1.00f to Color.Transparent
            )
        } else {
            arrayOf(
                0.00f to PayloadColors.Surface,
                0.50f to PayloadColors.Surface.copy(alpha = 0.98f),
                0.68f to PayloadColors.Surface.copy(alpha = 0.82f),
                0.84f to PayloadColors.Surface.copy(alpha = 0.30f),
                1.00f to Color.Transparent
            )
        }

        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = PayloadColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (selected) PayloadColors.Primary.copy(alpha = 0.32f) else PayloadColors.Border
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = when (productType) {
                                ProductType.FireBucket -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
                                ProductType.SolarClean -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
                                ProductType.DropperSixStage -> listOf(PayloadColors.Surface, PayloadColors.SurfaceSoft)
                                ProductType.RadioDetection -> listOf(PayloadColors.Surface, PayloadColors.PrimarySoft)
                                ProductType.Speaker -> listOf(PayloadColors.Surface, PayloadColors.WarningSoft)
                            }
                        ),
                        shape = shape
                    )
            ) {
                Image(
                    painter = painterResource(id = productSceneRes(productType)),
                    contentDescription = null,
                    contentScale = imageScale,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colorStops = maskStops
                            ),
                            shape = shape
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(if (isCompactCard) 0.54f else 0.56f)
                        .padding(start = PayloadDimens.CardPadding, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProductGlyph(productType = productType)
                    Text(
                        text = definition.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PlatformInk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = ProductCatalog.definitionOf(productType).platformSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlatformMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ProductCatalog.definitionOf(productType).platformValueLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = PayloadColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        CircularArrowButton(selected = selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGlyph(productType: ProductType) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(PayloadColors.PrimarySoft, RoundedCornerShape(PayloadDimens.ControlRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = productIconVector(productType),
            contentDescription = ProductCatalog.definitionOf(productType).displayName,
            modifier = Modifier.size(34.dp),
            tint = productAccentColor(productType)
        )
    }
}

internal fun productAccentColor(productType: ProductType): Color {
    return when (productType) {
        ProductType.FireBucket -> PlatformBlue
        ProductType.SolarClean -> PayloadColors.Warning
        ProductType.DropperSixStage -> PlatformBlue
        ProductType.RadioDetection -> PlatformBlue
        ProductType.Speaker -> PayloadColors.Warning
    }
}

@Composable
private fun CircularArrowButton(selected: Boolean) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = if (selected) PayloadColors.Primary else PlatformBlue
        )
    }
}
