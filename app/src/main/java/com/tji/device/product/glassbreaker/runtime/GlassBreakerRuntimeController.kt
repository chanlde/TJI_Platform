package com.tji.device.product.glassbreaker.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GlassBreakerRuntimeController(
    private val repository: GlassBreakerRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.BreakWindowProjectile

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.devices.map { states ->
            states.map { state ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = state.serialNumber,
                    name = state.name ?: state.serialNumber,
                    productType = ProductType.BreakWindowProjectile,
                    isOnline = state.isOnline,
                    payload = state
                )
            }
        }

    override fun clear() {
        repository.clearDevices()
    }
}
