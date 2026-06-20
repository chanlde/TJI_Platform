package com.tji.device.product.runtime

import com.tji.device.data.model.ProductType
import kotlinx.coroutines.flow.Flow

/**
 * 产品运行时 payload 标记接口。
 *
 * 具体字段必须定义在各产品包内；公共层只拿它做路由，不读取产品私有字段。
 */
interface ProductRuntimePayload

data class ProductDeviceRuntimeSnapshot(
    val serialNumber: String,
    val name: String,
    val productType: ProductType,
    val isOnline: Boolean,
    val childCount: Int? = null,
    val payload: ProductRuntimePayload? = null
) {
    val deviceId: String
        get() = serialNumber
}

interface ProductRuntimeController {
    val productType: ProductType
    val devices: Flow<List<ProductDeviceRuntimeSnapshot>>

    fun clear()
}

class ProductRuntimeRegistry(
    controllers: List<ProductRuntimeController>
) {
    private val controllerByType = controllers.associateBy { it.productType }

    val deviceFlows: List<Flow<List<ProductDeviceRuntimeSnapshot>>> =
        controllerByType.values.map { it.devices }

    fun clearAll() {
        controllerByType.values.forEach { it.clear() }
    }

    fun clear(productType: ProductType) {
        controllerByType[productType]?.clear()
    }
}
