package com.tji.device.ui.icon.product

import androidx.compose.ui.graphics.vector.ImageVector
import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.ui.icon.SixStageDropper
import com.tji.device.product.firebucket.ui.icon.PaintBucket
import com.tji.device.product.firegun.ui.icon.FireGun
import com.tji.device.product.radiodetection.ui.icon.RadioDetectionRadar
import com.tji.device.product.speaker.ui.icon.SpeakerHorn
import com.tji.device.product.solarclean.ui.icon.SolarPanelClean

fun productIconVector(productType: ProductType): ImageVector {
    return when (productType) {
        ProductType.FireBucket -> PaintBucket
        ProductType.FireGun -> FireGun
        ProductType.SolarClean -> SolarPanelClean
        ProductType.DropperSixStage -> SixStageDropper
        ProductType.RadioDetection -> RadioDetectionRadar
        ProductType.Speaker -> SpeakerHorn
        ProductType.BreakWindowProjectile -> SixStageDropper
        ProductType.Searchlight -> RadioDetectionRadar
    }
}
