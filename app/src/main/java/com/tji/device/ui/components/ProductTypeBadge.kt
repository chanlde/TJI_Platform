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
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiPrimarySoft
import com.tji.device.ui.theme.TjiWarning
import com.tji.device.ui.theme.TjiWarningSoft

@Composable
fun ProductTypeBadge(
    productType: ProductType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor) = when (productType) {
        ProductType.FireBucket -> TjiPrimarySoft to TjiPrimary
        ProductType.SolarClean -> TjiWarningSoft to TjiWarning
        ProductType.DropperSixStage -> TjiPrimarySoft to TjiPrimary
        ProductType.RadioDetection -> TjiPrimarySoft to TjiPrimary
        ProductType.Speaker -> TjiWarningSoft to TjiWarning
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
