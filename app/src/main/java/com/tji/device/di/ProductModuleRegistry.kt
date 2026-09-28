package com.tji.device.di

import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.mqtt.DropperSixStageMqttInbound
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import com.tji.device.product.droppersixstage.runtime.DropperSixStageRuntimeController
import com.tji.device.product.firebucket.control.FireBucketFloatingQuickControl
import com.tji.device.product.firebucket.mqtt.FireBucketMqttInbound
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository
import com.tji.device.product.firebucket.runtime.FireBucketRuntimeController
import com.tji.device.product.firegun.mqtt.FireGunMqttInbound
import com.tji.device.product.firegun.repository.FireGunRepository
import com.tji.device.product.firegun.runtime.FireGunRuntimeController
import com.tji.device.product.glassbreaker.mqtt.GlassBreakerMqttInbound
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import com.tji.device.product.glassbreaker.runtime.GlassBreakerRuntimeController
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttInbound
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.product.radiodetection.runtime.RadioDetectionRuntimeController
import com.tji.device.product.runtime.ProductRuntimeController
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttInbound
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import com.tji.device.product.solarclean.runtime.SolarCleanRuntimeController
import com.tji.device.product.speaker.mqtt.SpeakerMqttInbound
import com.tji.device.product.speaker.repository.SpeakerRepository
import com.tji.device.product.speaker.runtime.SpeakerRuntimeController
import org.json.JSONObject

interface ProductMqttEventHandler {
    val productType: ProductType

    suspend fun handleRawMessage(
        serialNumber: String,
        message: String,
        isRetained: Boolean,
        parsedJson: JSONObject? = null
    ): Boolean = false

    suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    )

    fun cleanup()
}

interface ProductModule : ProductMqttEventHandler {
    val runtimeController: ProductRuntimeController
    val floatingQuickControl: ProductFloatingQuickControl?
        get() = null
}

internal val IMPLEMENTED_PRODUCT_MODULE_TYPES: Set<ProductType> = setOf(
    ProductType.FireBucket,
    ProductType.FireGun,
    ProductType.SolarClean,
    ProductType.DropperSixStage,
    ProductType.RadioDetection,
    ProductType.Speaker,
    ProductType.BreakWindowProjectile
)

class ProductModuleRegistry(
    modules: List<ProductModule>,
    requiredProductTypes: Set<ProductType>? = null
) {
    private val moduleByType: Map<ProductType, ProductModule> =
        modules.associateByUniqueProductType("product module") { it.productType }

    init {
        requiredProductTypes?.let { required ->
            val registered = moduleByType.keys
            val missing = required - registered
            val unexpected = registered - required
            require(missing.isEmpty() && unexpected.isEmpty()) {
                buildString {
                    append("Product module registration does not match enabled catalog")
                    if (missing.isNotEmpty()) append("; missing=${missing.joinToString()}")
                    if (unexpected.isNotEmpty()) append("; unexpected=${unexpected.joinToString()}")
                }
            }
        }
    }

    val registeredProductTypes: Set<ProductType>
        get() = moduleByType.keys

    val runtimeControllers: List<ProductRuntimeController> =
        moduleByType.values.map { it.runtimeController }

    fun mqttHandlerFor(productType: ProductType): ProductMqttEventHandler? =
        moduleByType[productType]

    fun floatingQuickControlFor(productType: ProductType): ProductFloatingQuickControl =
        moduleByType[productType]?.floatingQuickControl ?: NoOpProductFloatingQuickControl

    fun cleanup() {
        moduleByType.values.forEach { it.cleanup() }
    }
}

private fun <T> List<T>.associateByUniqueProductType(
    itemName: String,
    productTypeOf: (T) -> ProductType
): Map<ProductType, T> {
    val duplicates = groupingBy(productTypeOf)
        .eachCount()
        .filterValues { it > 1 }
        .keys
    require(duplicates.isEmpty()) {
        "Duplicate $itemName registration for: ${duplicates.joinToString()}"
    }
    return associateBy(productTypeOf)
}

class FireBucketProductModule(
    linkRepository: FireBucketLinkRepository,
    switchRepository: FireBucketSwitchRepository
) : ProductModule {
    private val inbound = FireBucketMqttInbound(linkRepository)

    override val productType: ProductType = ProductType.FireBucket
    override val runtimeController: ProductRuntimeController =
        FireBucketRuntimeController(linkRepository, inbound::cleanup)
    override val floatingQuickControl: ProductFloatingQuickControl =
        FireBucketFloatingQuickControl(switchRepository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(serialNumber, eventType, json, isRetained)
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}

class FireGunProductModule(
    repository: FireGunRepository
) : ProductModule {
    private val inbound = FireGunMqttInbound(repository)

    override val productType: ProductType = ProductType.FireGun
    override val runtimeController: ProductRuntimeController = FireGunRuntimeController(repository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(serialNumber, eventType, json, isRetained)
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}

class SolarCleanProductModule(
    repository: SolarCleanRepository
) : ProductModule {
    private val inbound = SolarCleanMqttInbound(repository)

    override val productType: ProductType = ProductType.SolarClean
    override val runtimeController: ProductRuntimeController =
        SolarCleanRuntimeController(repository, inbound::cleanup)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(
            linkSn = serialNumber,
            eventType = eventType,
            json = json,
            isRetained = isRetained
        )
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}

class DropperSixStageProductModule(
    repository: DropperSixStageRepository
) : ProductModule {
    private val inbound = DropperSixStageMqttInbound(repository)

    override val productType: ProductType = ProductType.DropperSixStage
    override val runtimeController: ProductRuntimeController =
        DropperSixStageRuntimeController(repository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(
            serialNumber = serialNumber,
            eventType = eventType,
            json = json,
            isRetained = isRetained
        )
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}

class RadioDetectionProductModule(
    repository: RadioDetectionRepository,
    replayStore: RadioDetectionReplayStore
) : ProductModule {
    private val inbound = RadioDetectionMqttInbound(repository, replayStore)

    override val productType: ProductType = ProductType.RadioDetection
    override val runtimeController: ProductRuntimeController =
        RadioDetectionRuntimeController(repository)

    override suspend fun handleRawMessage(
        serialNumber: String,
        message: String,
        isRetained: Boolean,
        parsedJson: JSONObject?
    ): Boolean {
        inbound.handleMessage(
            serialNumber = serialNumber,
            message = message,
            isRetained = isRetained,
            parsedJson = parsedJson
        )
        return true
    }

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) = Unit

    override fun cleanup() {
        inbound.cleanup()
    }
}

class SpeakerProductModule(
    repository: SpeakerRepository
) : ProductModule {
    private val inbound = SpeakerMqttInbound(repository)

    override val productType: ProductType = ProductType.Speaker
    override val runtimeController: ProductRuntimeController =
        SpeakerRuntimeController(repository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(
            serialNumber = serialNumber,
            eventType = eventType,
            json = json,
            isRetained = isRetained
        )
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}

class GlassBreakerProductModule(
    repository: GlassBreakerRepository
) : ProductModule {
    private val inbound = GlassBreakerMqttInbound(repository)

    override val productType: ProductType = ProductType.BreakWindowProjectile
    override val runtimeController: ProductRuntimeController =
        GlassBreakerRuntimeController(repository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(
            serialNumber = serialNumber,
            eventType = eventType,
            json = json,
            isRetained = isRetained
        )
    }

    override fun cleanup() {
        inbound.cleanup()
    }
}
