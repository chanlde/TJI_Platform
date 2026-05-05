package com.tji.device.product.firebucket.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FireBucketRuntimeController(
    private val repository: FireBucketLinkRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.FireBucket

    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.links.map { links ->
            links.map { link ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = link.serial_number,
                    name = link.deviceName,
                    productType = ProductType.FireBucket,
                    isOnline = link.isOnline,
                    childCount = link.subDevices.size,
                    payload = link
                )
            }
        }

    override fun clear() {
        repository.clearLinks()
    }
}
