package com.tji.device.ui.main

import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProductControlRouteAvailabilityTest {
    @Test
    fun controlRoutesMatchEnabledProductCatalog() {
        val routedTypes = ProductType.entries.filter(::hasProductControlRoute).toSet()

        assertEquals(ProductCatalog.enabledTypes, routedTypes)
        assertFalse(hasProductControlRoute(ProductType.Searchlight))
    }
}
