package com.tji.device.data.viewmodel

import com.tji.device.data.model.ProductType
import com.tji.device.service.SubscriptionTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRuntimeRetirementTest {
    @Test
    fun switchingProductRetiresOnlyOldProductRuntime() {
        val previous = listOf(
            SubscriptionTarget("FIRE-1", ProductType.FireBucket),
            SubscriptionTarget("FIRE-2", ProductType.FireBucket)
        )
        val desired = listOf(
            SubscriptionTarget("SPEAKER-1", ProductType.Speaker)
        )

        assertEquals(
            setOf(ProductType.FireBucket),
            retiredProductTypes(previous, desired)
        )
    }

    @Test
    fun navigatingWithinSameProductKeepsFreshRuntime() {
        val previous = listOf(
            SubscriptionTarget("SPEAKER-1", ProductType.Speaker)
        )
        val desired = listOf(
            SubscriptionTarget("SPEAKER-1", ProductType.Speaker),
            SubscriptionTarget("SPEAKER-2", ProductType.Speaker)
        )

        assertEquals(emptySet<ProductType>(), retiredProductTypes(previous, desired))
    }

    @Test
    fun leavingAllProductsRetiresEveryPreviouslySubscribedType() {
        val previous = listOf(
            SubscriptionTarget("RADIO-1", ProductType.RadioDetection),
            SubscriptionTarget("SOLAR-1", ProductType.SolarClean)
        )

        assertEquals(
            setOf(ProductType.RadioDetection, ProductType.SolarClean),
            retiredProductTypes(previous, emptyList())
        )
    }
}
