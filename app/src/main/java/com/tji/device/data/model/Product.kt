package com.tji.device.data.model

enum class ProductType {
    FireBucket,
    SolarClean
}

data class ProductDefinition(
    val type: ProductType,
    val displayName: String,
    val shortLabel: String,
    val description: String,
    val platformSubtitle: String,
    val platformValueLine: String
)

object ProductCatalog {
    val definitions: List<ProductDefinition> = listOf(
        ProductDefinition(
            type = ProductType.FireBucket,
            displayName = "消防吊桶",
            shortLabel = "FireBucket",
            description = "消防吊桶控制产品线",
            platformSubtitle = "无人机消防吊桶系统",
            platformValueLine = "高效灭火 · 快速响应 · 安全可靠"
        ),
        ProductDefinition(
            type = ProductType.SolarClean,
            displayName = "光伏清洗",
            shortLabel = "SolarClean",
            description = "光伏清洗产品线",
            platformSubtitle = "无人机光伏清洗系统",
            platformValueLine = "智能清洗 · 高效作业 · 降本增效"
        )
    )

    val allTypes: List<ProductType> = definitions.map { it.type }

    fun definitionOf(type: ProductType): ProductDefinition {
        return definitions.first { it.type == type }
    }

    fun descriptionOf(type: ProductType): String {
        return definitionOf(type).description
    }

    fun inferType(
        deviceType: String?,
        deviceModel: String?,
        deviceName: String?
    ): ProductType {
        val fingerprint = listOf(deviceType, deviceModel, deviceName)
            .joinToString(separator = " ")
            .lowercase()

        return when {
            "光伏" in fingerprint -> ProductType.SolarClean
            "solar" in fingerprint -> ProductType.SolarClean
            else -> ProductType.FireBucket
        }
    }

    fun fromBackendFields(
        productId: Int?,
        productType: String?,
        productCode: String?,
        fallbackName: String?
    ): ProductType {
        val fingerprint = listOf(productType, productCode, fallbackName)
            .joinToString(separator = " ")
            .lowercase()

        return when {
            productId == 2 -> ProductType.FireBucket
            "firebucket" in fingerprint -> ProductType.FireBucket
            "fire bucket" in fingerprint -> ProductType.FireBucket
            "水桶" in fingerprint -> ProductType.FireBucket
            "吊桶" in fingerprint -> ProductType.FireBucket
            "solarclean" in fingerprint -> ProductType.SolarClean
            "solar clean" in fingerprint -> ProductType.SolarClean
            "光伏" in fingerprint -> ProductType.SolarClean
            else -> inferType(
                deviceType = productType ?: productCode,
                deviceModel = null,
                deviceName = fallbackName
            )
        }
    }
}
