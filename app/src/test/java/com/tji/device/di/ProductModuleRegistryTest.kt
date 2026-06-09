package com.tji.device.di

import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProductModuleRegistryTest {

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
