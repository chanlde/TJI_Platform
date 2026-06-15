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
        val handler = MqttEventHandler(ProductModuleRegistry(listOf(module)))

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
        override val productType: ProductType
    ) : ProductModule {
        val events = mutableListOf<HandledEvent>()

        override val runtimeController: ProductRuntimeController =
            object : ProductRuntimeController {
                override val productType: ProductType = this@RecordingProductModule.productType
                override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = emptyFlow()
                override fun clear() = Unit
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

    private companion object {
        const val SERIAL = "TJI-TEST-001"
    }
}
