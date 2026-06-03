package com.tji.device.data.model

enum class ProductType {
    FireBucket,
    SolarClean,
    DropperSixStage,
    RadioDetection,
    Speaker
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
            platformValueLine = "高效灭火 · 快速响应"
        ),
        ProductDefinition(
            type = ProductType.SolarClean,
            displayName = "光伏清洗",
            shortLabel = "SolarClean",
            description = "光伏清洗产品线",
            platformSubtitle = "无人机光伏清洗系统",
            platformValueLine = "智能清洗 · 高效作业"
        ),
        ProductDefinition(
            type = ProductType.DropperSixStage,
            displayName = "六段抛投",
            shortLabel = "Dropper",
            description = "六段抛投控制产品线",
            platformSubtitle = "无人机六路抛投系统",
            platformValueLine = "六路控制 · 分段抛投"
        ),
        ProductDefinition(
            type = ProductType.RadioDetection,
            displayName = "无线电检测",
            shortLabel = "Radio",
            description = "无线电检测产品线",
            platformSubtitle = "无人机无线电检测系统",
            platformValueLine = "频谱识别 · 实时态势"
        ),
        ProductDefinition(
            type = ProductType.Speaker,
            displayName = "喊话器",
            shortLabel = "Speaker",
            description = "无人机喊话器产品线",
            platformSubtitle = "无人机喊话广播系统",
            platformValueLine = "实时喊话 · 低延迟广播"
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
            "抛投" in fingerprint -> ProductType.DropperSixStage
            "dropper" in fingerprint -> ProductType.DropperSixStage
            "无线电" in fingerprint -> ProductType.RadioDetection
            "频谱" in fingerprint -> ProductType.RadioDetection
            "radio" in fingerprint -> ProductType.RadioDetection
            "spectrum" in fingerprint -> ProductType.RadioDetection
            "rid" in fingerprint -> ProductType.RadioDetection
            "喊话" in fingerprint -> ProductType.Speaker
            "speaker" in fingerprint -> ProductType.Speaker
            "broadcast" in fingerprint -> ProductType.Speaker
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
            productId == 3 -> ProductType.SolarClean
            productId == 4 -> ProductType.RadioDetection
            "firebucket" in fingerprint -> ProductType.FireBucket
            "fire bucket" in fingerprint -> ProductType.FireBucket
            "水桶" in fingerprint -> ProductType.FireBucket
            "吊桶" in fingerprint -> ProductType.FireBucket
            "solarclean" in fingerprint -> ProductType.SolarClean
            "solar clean" in fingerprint -> ProductType.SolarClean
            "光伏" in fingerprint -> ProductType.SolarClean
            "droppersixstage" in fingerprint -> ProductType.DropperSixStage
            "dropper six" in fingerprint -> ProductType.DropperSixStage
            "dropper" in fingerprint -> ProductType.DropperSixStage
            "六段抛投" in fingerprint -> ProductType.DropperSixStage
            "抛投" in fingerprint -> ProductType.DropperSixStage
            "radiodetection" in fingerprint -> ProductType.RadioDetection
            "radio detection" in fingerprint -> ProductType.RadioDetection
            "无线电" in fingerprint -> ProductType.RadioDetection
            "频谱" in fingerprint -> ProductType.RadioDetection
            "spectrum" in fingerprint -> ProductType.RadioDetection
            "rid" in fingerprint -> ProductType.RadioDetection
            "speaker" in fingerprint -> ProductType.Speaker
            "喊话" in fingerprint -> ProductType.Speaker
            "broadcast" in fingerprint -> ProductType.Speaker
            else -> inferType(
                deviceType = productType ?: productCode,
                deviceModel = null,
                deviceName = fallbackName
            )
        }
    }
}
