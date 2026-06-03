package com.tji.device.data.model

/**
 * 登录接口下发的**账号绑定设备**一行：SN、展示名、`ProductType`（推断）。
 * 这是跨产品的导航壳层数据，不承载任何产品自己的 MQTT 运行时字段。
 */
data class BoundAccountDevice(
    val serialNumber: String,
    val name: String,
    val productType: ProductType = ProductType.FireBucket,
    /** 后台绑定设备表 id；修改设备显示名时使用，不能用 SN 代替。 */
    val serverId: Int? = null
) {
    companion object {
        /**
         * 解析登录响应中的扁平字符串列表（格式 `["SN,Name", ...]`）。
         * JSON 字段：优先使用 [com.tji.network.data.LoginResponse.boundDeviceRows]，旧版为 `bucketsns`，
         * 客户端通过 [com.tji.network.data.LoginResponse.deviceRowsResolved] 合并后再传入本方法。
         */
        fun parseFromLoginDeviceRows(
            rows: List<String>,
            forcedProductType: ProductType? = null
        ): List<BoundAccountDevice> {
            return rows.mapNotNull { item ->
                val trimmed = item.trim()
                val parts = trimmed.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    val name = parts[1]
                    BoundAccountDevice(
                        serialNumber = parts[0],
                        name = name,
                        productType = forcedProductType
                            ?: ProductCatalog.fromBackendFields(
                                productId = parts.getOrNull(2)?.toIntOrNull(),
                                productType = parts.getOrNull(2),
                                productCode = parts.getOrNull(3),
                                fallbackName = name
                            )
                    )
                } else if (parts.size == 1 && parts[0].isNotBlank()) {
                    BoundAccountDevice(
                        serialNumber = parts[0],
                        name = parts[0],
                        productType = forcedProductType
                            ?: ProductCatalog.fromBackendFields(
                                productId = null,
                                productType = null,
                                productCode = null,
                                fallbackName = parts[0]
                            )
                    )
                } else {
                    null
                }
            }
        }
    }
}
