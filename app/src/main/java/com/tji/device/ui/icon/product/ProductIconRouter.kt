package com.tji.device.ui.icon.product

import androidx.compose.ui.graphics.vector.ImageVector
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.ui.icon.PaintBucket
import com.tji.device.product.solarclean.ui.icon.SolarPanelClean

fun productIconVector(productType: ProductType): ImageVector {
    return when (productType) {
        ProductType.FireBucket -> PaintBucket
        ProductType.SolarClean -> SolarPanelClean
    }
}
