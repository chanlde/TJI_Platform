package com.tji.device.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductCatalogTest {

    @Test
    fun mapsKnownBackendProductIdsBeforeNameFallback() {
        assertEquals(
            ProductType.FireBucket,
            ProductCatalog.fromBackendFields(
                productId = 2,
                productType = "solarclean",
                productCode = null,
                fallbackName = "光伏设备"
            )
        )
        assertEquals(
            ProductType.SolarClean,
            ProductCatalog.fromBackendFields(
                productId = 3,
                productType = "firebucket",
                productCode = null,
                fallbackName = "消防吊桶"
            )
        )
        assertEquals(
            ProductType.RadioDetection,
            ProductCatalog.fromBackendFields(
                productId = 4,
                productType = null,
                productCode = null,
                fallbackName = "普通设备"
            )
        )
    }

    @Test
    fun infersSupportedProductsFromBackendText() {
        assertEquals(
            ProductType.DropperSixStage,
            ProductCatalog.fromBackendFields(
                productId = null,
                productType = null,
                productCode = "FC100_FireDrop",
                fallbackName = null
            )
        )
        assertEquals(
            ProductType.Speaker,
            ProductCatalog.fromBackendFields(
                productId = null,
                productType = "speaker",
                productCode = null,
                fallbackName = "喊话器"
            )
        )
        assertEquals(
            ProductType.RadioDetection,
            ProductCatalog.inferType(
                deviceType = null,
                deviceModel = "RID spectrum",
                deviceName = "无线电检测"
            )
        )
    }

    @Test
    fun fallsBackToFireBucketForUnknownLegacyDevices() {
        assertEquals(
            ProductType.FireBucket,
            ProductCatalog.fromBackendFields(
                productId = null,
                productType = null,
                productCode = null,
                fallbackName = "HydroLink_V3-001"
            )
        )
    }
}
