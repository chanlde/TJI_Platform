package com.tji.device.product.speaker.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import com.tji.device.product.speaker.repository.SpeakerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SpeakerRuntimeController(
    private val repository: SpeakerRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.Speaker

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.devices.map { states ->
            states.map { state ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = state.serialNumber,
                    name = state.name ?: state.serialNumber,
                    productType = ProductType.Speaker,
                    isOnline = state.isOnline,
                    payload = state
                )
            }
        }

    override fun clear() {
        repository.clearDevices()
    }
}
