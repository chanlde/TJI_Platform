package com.tji.device.product.runtime

import com.tji.device.data.model.ProductType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRuntimeRegistryTest {

    @Test
    fun exposesOneDeviceFlowPerProductTypeAndKeepsLastDuplicateController() {
        val fireBucketA = RecordingRuntimeController(ProductType.FireBucket)
        val fireBucketB = RecordingRuntimeController(ProductType.FireBucket)
        val speaker = RecordingRuntimeController(ProductType.Speaker)

        val registry = ProductRuntimeRegistry(listOf(fireBucketA, speaker, fireBucketB))

        assertEquals(2, registry.deviceFlows.size)
        registry.clear(ProductType.FireBucket)

        assertEquals(0, fireBucketA.clearCount)
        assertEquals(1, fireBucketB.clearCount)
        assertEquals(0, speaker.clearCount)
    }

    @Test
    fun clearAllClearsEveryRegisteredRuntimeControllerOnce() {
        val fireBucket = RecordingRuntimeController(ProductType.FireBucket)
        val solarClean = RecordingRuntimeController(ProductType.SolarClean)
        val speaker = RecordingRuntimeController(ProductType.Speaker)
        val registry = ProductRuntimeRegistry(listOf(fireBucket, solarClean, speaker))

        registry.clearAll()

        assertEquals(1, fireBucket.clearCount)
        assertEquals(1, solarClean.clearCount)
        assertEquals(1, speaker.clearCount)
    }

    @Test
    fun clearUnknownProductTypeIsNoOp() {
        val fireBucket = RecordingRuntimeController(ProductType.FireBucket)
        val registry = ProductRuntimeRegistry(listOf(fireBucket))

        registry.clear(ProductType.Searchlight)

        assertEquals(0, fireBucket.clearCount)
    }

    private class RecordingRuntimeController(
        override val productType: ProductType
    ) : ProductRuntimeController {
        override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
            MutableStateFlow(emptyList())
        var clearCount: Int = 0
            private set

        override fun clear() {
            clearCount += 1
        }
    }
}
