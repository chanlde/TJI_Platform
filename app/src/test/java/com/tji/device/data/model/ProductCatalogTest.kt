package com.tji.device.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {

    @Test
    fun unknownBackendProductCannotEnterFireBucketControls() {
        assertEquals(
            null,
            ProductCatalog.controlTypeForBoundDevice(
                productId = null,
                productType = "FireWaterTank",
                productCode = "FireWaterTank",
                fallbackName = "消防水箱"
            )
        )
        assertEquals(
            ProductType.FireBucket,
            ProductCatalog.controlTypeForBoundDevice(
                productId = null,
                productType = null,
                productCode = null,
                fallbackName = "旧版设备"
            )
        )
        assertEquals(
            ProductType.FireGun,
            ProductCatalog.controlTypeForBoundDevice(
                productId = 2,
                productType = "FireBucket",
                productCode = "FireBucket",
                fallbackName = "HydroGunLink_V1"
            )
        )
    }

    @Test
    fun deviceNameCannotOverrideExplicitProductCode() {
        assertEquals(
            null,
            ProductCatalog.controlTypeForBoundDevice(
                productId = null,
                productType = "FireWaterTank",
                productCode = "FireWaterTank",
                fallbackName = "HydroGunLink_V1 水箱"
            )
        )
        assertEquals(
            ProductType.SolarClean,
            ProductCatalog.controlTypeForBoundDevice(
                productId = 3,
                productType = "SolarClean",
                productCode = "SolarClean",
                fallbackName = "HydroGunLink_V1 清洗设备"
            )
        )
        assertEquals(
            null,
            ProductCatalog.controlTypeForBoundDevice(
                productId = null,
                productType = "FireWaterTank",
                productCode = null,
                fallbackName = "HydroGunLink_V1 水箱"
            )
        )
        assertEquals(
            ProductType.SolarClean,
            ProductCatalog.controlTypeForBoundDevice(
                productId = 3,
                productType = null,
                productCode = null,
                fallbackName = "HydroGunLink_V1 清洗设备"
            )
        )
    }

    @Test
    fun enablesOnlyProductsWithCompleteProductionModules() {
        assertEquals(
            setOf(
                ProductType.FireBucket,
                ProductType.FireGun,
                ProductType.SolarClean,
                ProductType.DropperSixStage,
                ProductType.RadioDetection,
                ProductType.Speaker,
                ProductType.BreakWindowProjectile
            ),
            ProductCatalog.enabledTypes
        )
        assertTrue(ProductCatalog.enabledDefinitions.all { it.enabled })
        assertFalse(ProductCatalog.isEnabled(ProductType.Searchlight))
    }

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
        assertEquals(2, ProductCatalog.backendProductIdOf(ProductType.FireGun))
        assertEquals("FireGun", ProductCatalog.productCodeOf(ProductType.FireGun))
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
            ProductType.FireGun,
            ProductCatalog.fromBackendFields(
                productId = 2,
                productType = null,
                productCode = null,
                fallbackName = "HydroGunLink_V1-9526D839"
            )
        )
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
    fun fireGunPrefixIsCaseInsensitiveButDoesNotMatchOrdinaryHydroLink() {
        assertTrue(ProductCatalog.isFireGunIdentifier("hydrogunlink_v1-test"))
        assertFalse(ProductCatalog.isFireGunIdentifier("HydroLink_V3-test"))
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
