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
                    // 六路是同一台抛投设备内部的控制通道，不是六台子设备。
                    childCount = null,
                    payload = state
                )
            }
        }

    override fun clear() {
        repository.clearDevices()
    }
}
