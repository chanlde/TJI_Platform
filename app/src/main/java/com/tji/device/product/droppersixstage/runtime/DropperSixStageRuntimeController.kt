package com.tji.device.product.droppersixstage.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DropperSixStageRuntimeController(
    private val repository: DropperSixStageRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.DropperSixStage

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.devices.map { states ->
            states.map { state ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = state.serialNumber,
                    name = state.name ?: state.serialNumber,
                    productType = ProductType.DropperSixStage,
                    isOnline = state.isOnline,
                    childCount = state.stages.size,
                    payload = state
                )
            }
        }

    override fun clear() {
        repository.clearDevices()
    }
}
