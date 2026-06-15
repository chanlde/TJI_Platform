package com.tji.device.ui.components

import androidx.annotation.DrawableRes
import com.tji.device.R
import com.tji.device.data.model.ProductType

@DrawableRes
fun productEmptyIllustrationRes(productType: ProductType): Int {
    return when (productType) {
        ProductType.FireBucket -> R.drawable.img_empty_fire_bucket
        ProductType.SolarClean -> R.drawable.img_empty_solar_clean
        ProductType.DropperSixStage -> R.drawable.img_empty_fire_bucket
        ProductType.RadioDetection -> R.drawable.img_empty_fire_bucket
        ProductType.Speaker -> R.drawable.img_empty_fire_bucket
        ProductType.BreakWindowProjectile -> R.drawable.img_empty_fire_bucket
        ProductType.Searchlight -> R.drawable.img_empty_fire_bucket
    }
}

@DrawableRes
fun productSceneRes(productType: ProductType): Int {
    return when (productType) {
        ProductType.FireBucket -> R.drawable.img_scene_fire_bucket
        ProductType.SolarClean -> R.drawable.img_scene_solar_clean
        ProductType.DropperSixStage -> R.drawable.img_scene_fire_bucket
        ProductType.RadioDetection -> R.drawable.img_scene_radio_detection
        ProductType.Speaker -> R.drawable.img_scene_fire_bucket
        ProductType.BreakWindowProjectile -> R.drawable.img_scene_fire_bucket
        ProductType.Searchlight -> R.drawable.img_scene_radio_detection
    }
}
