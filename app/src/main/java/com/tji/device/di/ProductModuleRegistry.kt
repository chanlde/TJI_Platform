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
        isRetained: Boolean
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

class ProductModuleRegistry(
    modules: List<ProductModule>
) {
    private val moduleByType: Map<ProductType, ProductModule> = modules.associateBy { it.productType }

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

class FireBucketProductModule(
    linkRepository: FireBucketLinkRepository,
    switchRepository: FireBucketSwitchRepository
) : ProductModule {
    private val inbound = FireBucketMqttInbound(linkRepository)

    override val productType: ProductType = ProductType.FireBucket
    override val runtimeController: ProductRuntimeController =
        FireBucketRuntimeController(linkRepository)
    override val floatingQuickControl: ProductFloatingQuickControl =
        FireBucketFloatingQuickControl(switchRepository)

    override suspend fun handleJsonEvent(
        serialNumber: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        inbound.handleEvent(serialNumber, eventType, json)
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
        SolarCleanRuntimeController(repository)

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
        isRetained: Boolean
    ): Boolean {
        inbound.handleMessage(
            serialNumber = serialNumber,
            message = message,
            isRetained = isRetained
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
