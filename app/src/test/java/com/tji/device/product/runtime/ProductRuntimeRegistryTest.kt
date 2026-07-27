package com.tji.device.product.runtime

import com.tji.device.data.model.ProductType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRuntimeRegistryTest {
    @Test
    fun selectedDeviceFlowsExcludeUnrelatedHighFrequencyProducts() {
        val registry = ProductRuntimeRegistry(
            listOf(
                FakeController(ProductType.SolarClean),
                FakeController(ProductType.RadioDetection),
                FakeController(ProductType.FireBucket)
            )
        )

        val selected = registry.deviceFlowsFor(
            setOf(ProductType.SolarClean, ProductType.FireBucket)
        )

        assertEquals(2, selected.size)
    }

    private class FakeController(
        override val productType: ProductType
    ) : ProductRuntimeController {
        override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = flowOf(emptyList())
        override fun clear() = Unit
    }
}
