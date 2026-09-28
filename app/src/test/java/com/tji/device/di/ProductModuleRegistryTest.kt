package com.tji.device.di

import com.tji.device.data.model.ProductType
import com.tji.device.data.model.ProductCatalog
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductModuleRegistryTest {

    @Test
    fun implementedModulesMatchEnabledProductionCatalog() {
        assertEquals(ProductCatalog.enabledTypes, IMPLEMENTED_PRODUCT_MODULE_TYPES)

        val modules = ProductCatalog.enabledTypes.map(::FakeProductModule)
        val registry = ProductModuleRegistry(
            modules = modules,
            requiredProductTypes = ProductCatalog.enabledTypes
        )

        assertEquals(ProductCatalog.enabledTypes, registry.registeredProductTypes)
    }

    @Test
    fun requiredCatalogRejectsMissingOrDisabledModules() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductModuleRegistry(
                modules = listOf(FakeProductModule(ProductType.FireBucket)),
                requiredProductTypes = ProductCatalog.enabledTypes
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductModuleRegistry(
                modules = ProductCatalog.enabledTypes.map(::FakeProductModule) +
                    FakeProductModule(ProductType.Searchlight),
                requiredProductTypes = ProductCatalog.enabledTypes
            )
        }
    }

    @Test
    fun exposesRuntimeControllersAndHandlersByProductType() {
        val fireBucket = FakeProductModule(ProductType.FireBucket)
        val speaker = FakeProductModule(ProductType.Speaker)
        val registry = ProductModuleRegistry(listOf(fireBucket, speaker))

        assertEquals(
            listOf(ProductType.FireBucket, ProductType.Speaker),
            registry.runtimeControllers.map { it.productType }
        )
        assertSame(fireBucket, registry.mqttHandlerFor(ProductType.FireBucket))
        assertSame(speaker, registry.mqttHandlerFor(ProductType.Speaker))
        assertNull(registry.mqttHandlerFor(ProductType.SolarClean))
    }

    @Test
    fun returnsNoOpFloatingControlWhenProductHasNoQuickControl() {
        val registry = ProductModuleRegistry(
            listOf(FakeProductModule(ProductType.SolarClean))
        )

        assertSame(
            NoOpProductFloatingQuickControl,
            registry.floatingQuickControlFor(ProductType.SolarClean)
        )
        assertSame(
            NoOpProductFloatingQuickControl,
            registry.floatingQuickControlFor(ProductType.FireBucket)
        )
    }

    @Test
    fun rejectsDuplicateProductModulesInsteadOfSilentlyReplacingOne() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductModuleRegistry(
                listOf(
                    FakeProductModule(ProductType.FireBucket),
                    FakeProductModule(ProductType.FireBucket)
                )
            )
        }
    }

    private class FakeProductModule(
        override val productType: ProductType,
        override val floatingQuickControl: ProductFloatingQuickControl? = null
    ) : ProductModule {
        override val runtimeController: ProductRuntimeController =
            object : ProductRuntimeController {
                override val productType: ProductType = this@FakeProductModule.productType
                override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = emptyFlow()
                override fun clear() = Unit
            }

        override suspend fun handleJsonEvent(
            serialNumber: String,
            eventType: String,
            json: JSONObject,
            isRetained: Boolean
        ) = Unit

        override fun cleanup() = Unit
    }
}
