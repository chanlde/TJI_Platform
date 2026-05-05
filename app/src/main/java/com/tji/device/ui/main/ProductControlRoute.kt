package com.tji.device.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.ui.control.FireBucketControlScreen
import com.tji.device.product.solarclean.ui.control.SolarCleanControlScreen

@Composable
fun ProductControlRoute(
    device: BoundAccountDevice,
    fireBucketLink: FireBucketLinkDevice?,
    showSettings: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (device.productType) {
        ProductType.FireBucket -> FireBucketControlScreen(
            link = fireBucketLink ?: fireBucketLinkPlaceholderFromBoundAccount(device),
            modifier = modifier
        )

        ProductType.SolarClean -> SolarCleanControlScreen(
            device = device,
            showSettings = showSettings,
            modifier = modifier
        )
    }
}
