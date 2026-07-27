package com.tji.device.service

import com.tji.device.data.model.ProductType
import com.tji.device.di.ProductModule
import com.tji.device.di.ProductModuleRegistry
import com.tji.device.product.ota.ProductDeviceInfo
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import com.tji.device.product.ota.ProductOtaRuntimeState
import com.tji.device.product.ota.ProductOtaStatus
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttEventHandlerTest {

    @Test
    fun dispatchesPlainOnlineOfflineLifecycleMessagesToRegisteredProductHandler() = runBlocking {
        val module = RecordingProductModule(ProductType.Speaker)
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.Speaker,
            message = "online",
            isRetained = true
        )

        val event = module.events.single()
        assertEquals(SERIAL, event.serialNumber)
        assertEquals("online", event.eventType)
        assertEquals(true, event.isRetained)
        assertEquals("online", event.json.optString("type"))
        val lifecycle = otaRepo.lifecycleUpdates.single()
        assertEquals(ProductType.Speaker, lifecycle.productType)
        assertEquals(SERIAL, lifecycle.serialNumber)
        assertEquals("online", lifecycle.eventType)
        assertEquals(true, lifecycle.isRetained)
    }

    @Test
    fun cachesJsonLifecycleOnlineForCommonOtaRuntime() = runBlocking {
        val module = RecordingProductModule(ProductType.SolarClean)
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo,
            nowMillis = { 1_800_000_000_000L }
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.SolarClean,
            message = """{"type":"online","ts":12345}"""
        )

        val lifecycle = otaRepo.lifecycleUpdates.single()
        assertEquals(ProductType.SolarClean, lifecycle.productType)
        assertEquals(SERIAL, lifecycle.serialNumber)
        assertEquals("online", lifecycle.eventType)
        assertEquals(false, lifecycle.isRetained)
        assertEquals(1_800_000_000_000L, lifecycle.timestamp)
        assertEquals("online", module.events.single().eventType)
    }

    @Test
    fun cachesTrustedJsonLifecycleTimestampForOtaOrdering() = runBlocking {
        val module = RecordingProductModule(ProductType.SolarClean)
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo,
            nowMillis = { 1_800_000_000_000L }
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.SolarClean,
            message = """{"type":"offline","ts":1700000000123}"""
        )

        assertEquals(1_700_000_000_123L, otaRepo.lifecycleUpdates.single().timestamp)
    }

    @Test
    fun cachesCommonDeviceInfoBeforeDispatchingJsonEvent() = runBlocking {
        val module = RecordingProductModule(ProductType.SolarClean)
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.SolarClean,
            message = """
                {
                  "type": "DEVICE_INFO",
                  "data": {
                    "hardwareVersion": "HW-A",
                    "firmwareVersion": "1.2.3",
                    "innerVersion": 12
                  }
                }
            """.trimIndent()
        )

        val cached = otaRepo.deviceInfoUpdates.single()
        assertEquals(ProductType.SolarClean, cached.productType)
        assertEquals(SERIAL, cached.serialNumber)
        assertEquals("HW-A", cached.deviceInfo.hardwareVersion)
        assertEquals("1.2.3", cached.deviceInfo.firmwareVersion)
        assertEquals(12, cached.deviceInfo.firmwareInnerVersion)
        assertEquals("deviceInfo", module.events.single().eventType)
    }

    @Test
    fun cachesCommonOtaStatusBeforeDispatchingJsonEvent() = runBlocking {
        val module = RecordingProductModule(ProductType.Speaker)
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.Speaker,
            message = """
                {
                  "type": "ota_status",
                  "cmdId": "ota-1",
                  "seq": 7,
                  "otaStatus": "OTA_DOWNLOADING",
                  "progress": 0.5
                }
            """.trimIndent()
        )

        val cached = otaRepo.otaStatusUpdates.single()
        assertEquals(ProductType.Speaker, cached.productType)
        assertEquals(SERIAL, cached.serialNumber)
        assertEquals("OTA_DOWNLOADING", cached.otaStatus.status)
        assertEquals("ota-1", cached.otaStatus.cmdId)
        assertEquals(7L, cached.otaStatus.seq)
        assertEquals(50, cached.otaStatus.progress)
        assertEquals("otaStatus", module.events.single().eventType)
    }

    @Test
    fun cachesCommonOtaStatusBeforeRawProductHandlerConsumesMessage() = runBlocking {
        val module = RecordingProductModule(
            productType = ProductType.RadioDetection,
            consumeRawMessages = true
        )
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module)),
            productOtaRuntimeRepository = otaRepo
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.RadioDetection,
            message = """{"type":"ota_status","cmdId":"radio-ota","otaStatus":"OTA_DOWNLOADING","progress":60}"""
        )

        val cached = otaRepo.otaStatusUpdates.single()
        assertEquals(ProductType.RadioDetection, cached.productType)
        assertEquals("radio-ota", cached.otaStatus.cmdId)
        assertEquals(60, cached.otaStatus.progress)
        assertEquals(1, module.rawMessages.size)
        assertEquals("ota_status", module.rawJsonMessages.single()?.optString("type"))
        assertEquals(emptyList<HandledEvent>(), module.events)
    }

    @Test
    fun nonJsonRawPayloadReachesProductWithoutRouterJsonParsing() = runBlocking {
        val module = RecordingProductModule(
            productType = ProductType.RadioDetection,
            consumeRawMessages = true
        )
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(listOf(module))
        )
        val hexRidPayload = "7b2274797065223a22726964227d"

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.RadioDetection,
            message = hexRidPayload
        )

        assertEquals(listOf(hexRidPayload), module.rawMessages)
        assertEquals(listOf<JSONObject?>(null), module.rawJsonMessages)
        assertEquals(emptyList<HandledEvent>(), module.events)
    }

    @Test
    fun ignoresMessagesForUnregisteredProductWithoutCachingCommonOtaPayload() = runBlocking {
        val otaRepo = RecordingOtaRuntimeRepository()
        val handler = MqttEventHandler(
            productModules = ProductModuleRegistry(emptyList()),
            productOtaRuntimeRepository = otaRepo
        )

        handler.handleMessage(
            serialNumber = SERIAL,
            productType = ProductType.Searchlight,
            message = """{"type":"ota_status","otaStatus":"OTA_DOWNLOADING","progress":75}"""
        )

        assertEquals(emptyList<DeviceInfoUpdate>(), otaRepo.deviceInfoUpdates)
        assertEquals(emptyList<OtaStatusUpdate>(), otaRepo.otaStatusUpdates)
    }

    private class RecordingProductModule(
        override val productType: ProductType,
        private val consumeRawMessages: Boolean = false
    ) : ProductModule {
        val events = mutableListOf<HandledEvent>()
        val rawMessages = mutableListOf<String>()
        val rawJsonMessages = mutableListOf<JSONObject?>()

        override val runtimeController: ProductRuntimeController =
            object : ProductRuntimeController {
                override val productType: ProductType = this@RecordingProductModule.productType
                override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = emptyFlow()
                override fun clear() = Unit
            }

        override suspend fun handleRawMessage(
            serialNumber: String,
            message: String,
            isRetained: Boolean,
            parsedJson: JSONObject?
        ): Boolean {
            rawMessages += message
            rawJsonMessages += parsedJson
            return consumeRawMessages
        }

        override suspend fun handleJsonEvent(
            serialNumber: String,
            eventType: String,
            json: JSONObject,
            isRetained: Boolean
        ) {
            events += HandledEvent(serialNumber, eventType, json, isRetained)
        }

        override fun cleanup() = Unit
    }

    private class RecordingOtaRuntimeRepository : ProductOtaRuntimeRepository {
        override val states: StateFlow<List<ProductOtaRuntimeState>> =
            MutableStateFlow(emptyList())
        val deviceInfoUpdates = mutableListOf<DeviceInfoUpdate>()
        val otaStatusUpdates = mutableListOf<OtaStatusUpdate>()
        val lifecycleUpdates = mutableListOf<LifecycleUpdate>()

        override fun updateDeviceInfo(
            productType: ProductType,
            serialNumber: String,
            deviceInfo: ProductDeviceInfo
        ) {
            deviceInfoUpdates += DeviceInfoUpdate(productType, serialNumber, deviceInfo)
        }

        override fun updateOtaStatus(
            productType: ProductType,
            serialNumber: String,
            otaStatus: ProductOtaStatus
        ) {
            otaStatusUpdates += OtaStatusUpdate(productType, serialNumber, otaStatus)
        }

        override fun updateLifecycle(
            productType: ProductType,
            serialNumber: String,
            eventType: String,
            timestamp: Long?,
            isRetained: Boolean
        ) {
            lifecycleUpdates += LifecycleUpdate(productType, serialNumber, eventType, timestamp, isRetained)
        }

        override fun clearAll() = Unit
    }

    private data class HandledEvent(
        val serialNumber: String,
        val eventType: String,
        val json: JSONObject,
        val isRetained: Boolean
    )

    private data class DeviceInfoUpdate(
        val productType: ProductType,
        val serialNumber: String,
        val deviceInfo: ProductDeviceInfo
    )

    private data class OtaStatusUpdate(
        val productType: ProductType,
        val serialNumber: String,
        val otaStatus: ProductOtaStatus
    )

    private data class LifecycleUpdate(
        val productType: ProductType,
        val serialNumber: String,
        val eventType: String,
        val timestamp: Long?,
        val isRetained: Boolean
    )

    private companion object {
        const val SERIAL = "TJI-TEST-001"
    }
}
