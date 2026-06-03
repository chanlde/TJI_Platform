package com.tji.device.product.radiodetection.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.model.RadioDetectionRuntime
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RadioDetectionRuntimeController(
    private val repository: RadioDetectionRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.RadioDetection

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = repository.devices.map { states ->
        states.map { state ->
            ProductDeviceRuntimeSnapshot(
                serialNumber = state.serialNumber,
                name = state.displayName,
                productType = ProductType.RadioDetection,
                isOnline = state.isOnline,
                childCount = null,
                payload = RadioDetectionRuntime(
                    serialNumber = state.serialNumber,
                    displayName = state.displayName,
                    isOnline = state.isOnline,
                    targetCount = state.targets.size,
                    lastUpdateText = state.payloadStatus
                )
            )
        }
    }

    override fun clear() {
        repository.clearDevices()
    }
}
