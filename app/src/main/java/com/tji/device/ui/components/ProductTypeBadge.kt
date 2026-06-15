package com.tji.device.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.ui.theme.PayloadColors

@Composable
fun ProductTypeBadge(
    productType: ProductType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when (productType) {
        ProductType.FireBucket -> PayloadColors.PrimarySoft to PayloadColors.Primary
        ProductType.SolarClean -> PayloadColors.WarningSoft to PayloadColors.Warning
        ProductType.DropperSixStage -> PayloadColors.PrimarySoft to PayloadColors.Primary
        ProductType.RadioDetection -> PayloadColors.PrimarySoft to PayloadColors.Primary
        ProductType.Speaker -> PayloadColors.WarningSoft to PayloadColors.Warning
        ProductType.BreakWindowProjectile -> PayloadColors.PrimarySoft to PayloadColors.Primary
        ProductType.Searchlight -> PayloadColors.PrimarySoft to PayloadColors.Primary
    }

    Box(
        modifier = modifier
            .background(backgroundColor, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = ProductCatalog.definitionOf(productType).displayName,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
