package com.tji.device.product.firegun.runtime

import com.tji.device.data.model.ProductType
import com.tji.device.product.firegun.repository.FireGunRepository
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FireGunRuntimeController(
    private val repository: FireGunRepository
) : ProductRuntimeController {
    override val productType: ProductType = ProductType.FireGun
    override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> =
        repository.links.map { links ->
            links.values.map { link ->
                ProductDeviceRuntimeSnapshot(
                    serialNumber = link.serialNumber,
                    name = link.name,
                    productType = ProductType.FireGun,
                    isOnline = link.isOnline,
                    payload = link
                )
            }
        }

    override fun clear() {
        repository.clear()
    }
}
