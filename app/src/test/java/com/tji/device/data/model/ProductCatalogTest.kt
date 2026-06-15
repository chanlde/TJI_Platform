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
        assertEquals(
            ProductType.DropperSixStage,
            ProductCatalog.fromBackendFields(
                productId = 5,
                productType = "speaker",
                productCode = null,
                fallbackName = "喊话器"
            )
        )
        assertEquals(
            ProductType.Speaker,
            ProductCatalog.fromBackendFields(
                productId = 6,
                productType = "radiodetection",
                productCode = null,
                fallbackName = "无线电检测"
            )
        )
        assertEquals(
            ProductType.BreakWindowProjectile,
            ProductCatalog.fromBackendFields(
                productId = 7,
                productType = "firebucket",
                productCode = null,
                fallbackName = "消防吊桶"
            )
        )
        assertEquals(
            ProductType.Searchlight,
            ProductCatalog.fromBackendFields(
                productId = 8,
                productType = "solarclean",
                productCode = null,
                fallbackName = "光伏清洗"
            )
        )
    }

    @Test
    fun exposesCanonicalProductIdsAndCodes() {
        assertEquals(2, ProductCatalog.backendProductIdOf(ProductType.FireBucket))
        assertEquals("FireBucket", ProductCatalog.productCodeOf(ProductType.FireBucket))
        assertEquals(3, ProductCatalog.backendProductIdOf(ProductType.SolarClean))
        assertEquals("SolarClean", ProductCatalog.productCodeOf(ProductType.SolarClean))
        assertEquals(4, ProductCatalog.backendProductIdOf(ProductType.RadioDetection))
        assertEquals("RadioDetection", ProductCatalog.productCodeOf(ProductType.RadioDetection))
        assertEquals(5, ProductCatalog.backendProductIdOf(ProductType.DropperSixStage))
        assertEquals("SixStageDropper", ProductCatalog.productCodeOf(ProductType.DropperSixStage))
        assertEquals(6, ProductCatalog.backendProductIdOf(ProductType.Speaker))
        assertEquals("Speaker", ProductCatalog.productCodeOf(ProductType.Speaker))
        assertEquals(7, ProductCatalog.backendProductIdOf(ProductType.BreakWindowProjectile))
        assertEquals("GlassBreaker", ProductCatalog.productCodeOf(ProductType.BreakWindowProjectile))
        assertEquals(8, ProductCatalog.backendProductIdOf(ProductType.Searchlight))
        assertEquals("Searchlight", ProductCatalog.productCodeOf(ProductType.Searchlight))
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
        assertEquals(
            ProductType.BreakWindowProjectile,
            ProductCatalog.fromBackendFields(
                productId = null,
                productType = null,
                productCode = "GlassBreaker",
                fallbackName = "破窗弹"
            )
        )
        assertEquals(
            ProductType.Searchlight,
            ProductCatalog.fromBackendFields(
                productId = null,
                productType = "searchlight",
                productCode = null,
                fallbackName = "探照灯"
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
