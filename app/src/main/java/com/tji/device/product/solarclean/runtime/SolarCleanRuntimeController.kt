package com.tji.device.product.solarclean.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SolarCleanRuntimeController(
    private val repository: SolarCleanRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.SolarClean

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.devices.map { states ->
            states.map { state ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = state.serialNumber,
                    name = state.serialNumber,
                    productType = ProductType.SolarClean,
                    isOnline = state.isOnline,
                    childCount = null,
                    payload = state
                )
            }
        }

    override fun clear() {
        repository.clearDevices()
    }
}
