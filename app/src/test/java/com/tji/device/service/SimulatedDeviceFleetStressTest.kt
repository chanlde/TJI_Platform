package com.tji.device.service

import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.di.DropperSixStageProductModule
import com.tji.device.di.FireBucketProductModule
import com.tji.device.di.GlassBreakerProductModule
import com.tji.device.di.ProductFloatingQuickControl
import com.tji.device.di.ProductModule
import com.tji.device.di.ProductModuleRegistry
import com.tji.device.di.SolarCleanProductModule
import com.tji.device.di.SpeakerProductModule
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import com.tji.device.product.firebucket.repository.FireBucketSwitchCommandRepository
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepo
import com.tji.device.product.ota.ProductOtaRuntimeRepo
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttInbound
import com.tji.device.product.radiodetection.repository.RadioDetectionRepo
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.speaker.repository.SpeakerRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 隔离的设备群模拟器压力测试。
 *
 * 不连接 MQTT Broker、不使用生产账号、不发布控制命令；但复用生产
 * MqttEventHandler、产品 inbound 和 repository，验证真实入站状态合并路径。
 */
class SimulatedDeviceFleetStressTest {

    @Test
    fun simulatedFleetProcessesStateAndOtaFloodWithoutLosingLatestState() = runBlocking {
        val fixture = FleetFixture()
        val devices = ProductCatalog.enabledTypes.flatMap { productType ->
            (1..DEVICES_PER_PRODUCT).map { index ->
                SimulatedDevice(productType, "SIM-${productType.name.uppercase()}-$index")
            }
        }

        try {
            coroutineScope {
                devices.forEach { device ->
                    launch(Dispatchers.Default) {
                        fixture.sendInitialIdentity(device)
                        repeat(STATE_ROUNDS) { zeroBasedRound ->
                            val round = zeroBasedRound + 1
                            fixture.sendState(device, round)
                            fixture.sendOtaProgress(device, round)
                            if (round % 25 == 0) {
                                fixture.handler.handleMessage(
                                    serialNumber = device.serialNumber,
                                    productType = device.productType,
                                    message = "{malformed-simulator-frame"
                                )
                            }
                        }
                    }
                }
            }

            assertEquals(DEVICES_PER_PRODUCT, fixture.fireBucket.links.value.size)
            assertEquals(DEVICES_PER_PRODUCT, fixture.solarClean.devices.value.size)
            assertEquals(DEVICES_PER_PRODUCT, fixture.dropper.devices.value.size)
            assertEquals(DEVICES_PER_PRODUCT, fixture.radio.devices.value.size)
            assertEquals(DEVICES_PER_PRODUCT, fixture.speaker.devices.value.size)
            assertEquals(DEVICES_PER_PRODUCT, fixture.glassBreaker.devices.value.size)

            assertTrue(fixture.fireBucket.links.value.all { it.isOnline })
            assertTrue(fixture.solarClean.devices.value.all { it.isOnline })
            assertTrue(fixture.dropper.devices.value.all { it.isOnline })
            assertTrue(fixture.radio.devices.value.all { it.isOnline && it.targets.size == 1 })
            assertTrue(fixture.speaker.devices.value.all { it.isOnline && it.volume == FINAL_VALUE })
            assertTrue(
                fixture.glassBreaker.devices.value.all {
                    it.isOnline && it.batteryPercent == FINAL_VALUE
                }
            )

            val otaStates = fixture.ota.states.value
            assertEquals(devices.size, otaStates.size)
            assertTrue(
                otaStates.all {
                    it.otaStatus?.status == "SUCCESS" &&
                        it.otaStatus?.progress == 100 &&
                        it.otaStatus?.seq == STATE_ROUNDS.toLong()
                }
            )
        } finally {
            fixture.handler.cleanup()
        }
    }

    @Test
    fun simulatedStaleAndRetainedFramesCannotOverwriteNewerDeviceState() = runBlocking {
        val fixture = FleetFixture()
        val devices = ProductCatalog.enabledTypes.map { productType ->
            SimulatedDevice(productType, "SIM-ORDER-${productType.name.uppercase()}")
        }

        try {
            devices.forEach { device ->
                fixture.sendInitialIdentity(device)
                fixture.sendState(device, round = 80)
                fixture.sendState(device, round = 40)
                fixture.handler.handleMessage(
                    serialNumber = device.serialNumber,
                    productType = device.productType,
                    message = "online",
                    isRetained = true
                )
                fixture.sendOtaProgress(device, round = 80)
                fixture.sendOtaProgress(device, round = 40)
            }

            assertEquals(80.0, fixture.solarClean.devices.value.single().batteryPercent)
            assertEquals(80, fixture.dropper.devices.value.single().batteryPercent)
            assertEquals(80, fixture.speaker.devices.value.single().volume)
            assertEquals(80, fixture.glassBreaker.devices.value.single().batteryPercent)
            assertEquals(80L, fixture.ota.states.value.first().otaStatus?.seq)
            assertTrue(fixture.ota.states.value.all { it.otaStatus?.seq == 80L })
        } finally {
            fixture.handler.cleanup()
        }
    }

    private data class SimulatedDevice(
        val productType: ProductType,
        val serialNumber: String
    )

    private class FleetFixture {
        val fireBucket = FireBucketLinkRepo()
        val solarClean = SolarCleanRepo()
        val dropper = DropperSixStageRepo()
        val radio = RadioDetectionRepo(maxTargetsPerDevice = 8)
        val speaker = SpeakerRepo()
        val glassBreaker = GlassBreakerRepo()
        val ota = ProductOtaRuntimeRepo()

        private val radioModule = SimulatedRadioProductModule(radio)
        private val modules = ProductModuleRegistry(
            modules = listOf(
                FireBucketProductModule(fireBucket, FireBucketSwitchCommandRepository()),
                SolarCleanProductModule(solarClean),
                DropperSixStageProductModule(dropper),
                radioModule,
                SpeakerProductModule(speaker),
                GlassBreakerProductModule(glassBreaker)
            ),
            requiredProductTypes = ProductCatalog.enabledTypes
        )
        val handler = MqttEventHandler(modules, ota) { 1_800_000_000_000L }

        suspend fun sendInitialIdentity(device: SimulatedDevice) {
            handler.handleMessage(
                serialNumber = device.serialNumber,
                productType = device.productType,
                message = initialPayload(device)
            )
            handler.handleMessage(
                serialNumber = device.serialNumber,
                productType = device.productType,
                message = """{"type":"deviceInfo","hardwareVersion":"SIM-HW","firmwareVersion":"1.0.0","innerVersion":1}"""
            )
        }

        suspend fun sendState(device: SimulatedDevice, round: Int) {
            handler.handleMessage(
                serialNumber = device.serialNumber,
                productType = device.productType,
                message = statePayload(device, round)
            )
        }

        suspend fun sendOtaProgress(device: SimulatedDevice, round: Int) {
            val progress = round.coerceIn(0, 100)
            val status = if (round >= STATE_ROUNDS) "SUCCESS" else "OTA_DOWNLOADING"
            handler.handleMessage(
                serialNumber = device.serialNumber,
                productType = device.productType,
                message = """
                    {"type":"otaStatus","cmdId":"ota-${device.serialNumber}","seq":$round,
                     "otaStatus":"$status","progress":$progress,"ts":${BASE_TS + round}}
                """.trimIndent()
            )
        }

        private fun initialPayload(device: SimulatedDevice): String = when (device.productType) {
            ProductType.FireBucket -> """
                {"event_type":"LinkDeviceStartup","serial_number":"${device.serialNumber}",
                 "deviceName":"模拟消防吊桶","deviceType":"Link","manufacturer":"TJI",
                 "deviceModel":"FB","isOnline":true,"hwVersion":"SIM-HW",
                 "swVersion":"1.0.0","uptime":1,"deviceConfig":"",
                 "timestamp":"${BASE_TS}","subDevices":[{
                   "serial_number":"SWITCH-${device.serialNumber}","deviceName":"模拟吊桶",
                   "deviceType":"HydroSwitch","isOnline":true,"currentAngle":10,
                   "currentCurrent":1,"inputVoltage":8,"servoMinAngle":0,
                   "servoMaxAngle":90,"uptime":1}]}
            """.trimIndent()
            ProductType.RadioDetection -> "online"
            else -> "online"
        }

        private fun statePayload(device: SimulatedDevice, round: Int): String {
            val timestamp = BASE_TS + round
            val value = round.coerceAtMost(100)
            return when (device.productType) {
                ProductType.FireBucket -> """
                    {"event_type":"LinkDeviceHeartbeat","serial_number":"${device.serialNumber}",
                     "isOnline":true,"uptime":$round,"timestamp":"$timestamp"}
                """.trimIndent()
                ProductType.SolarClean ->
                    """{"type":"state","battery":$value,"water":$value,"ts":$timestamp}"""
                ProductType.DropperSixStage ->
                    """{"type":"state","battery":$value,"stages":[{"stage":1,"open":${round % 2 == 0}}],"ts":$timestamp}"""
                ProductType.RadioDetection -> ridPayload(device.serialNumber, round)
                ProductType.Speaker ->
                    """{"type":"state","online":true,"volume":$value,"playing":false,"ts":$timestamp}"""
                ProductType.BreakWindowProjectile ->
                    """{"type":"state","online":true,"battery":$value,"lockState":"locked","selectedChannel":1,"ts":$timestamp}"""
                ProductType.Searchlight -> error("disabled product cannot be simulated")
            }
        }

        private fun ridPayload(serialNumber: String, round: Int): String {
            val json = """
                {"type":"rid","sn":"RID-$serialNumber","rssi":-${40 + round % 20},
                 "channel":6,"drone_lon":113.9,"drone_lat":22.5,"timestamp":${BASE_TS + round}}
            """.trimIndent()
            return json.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        }
    }

    private class SimulatedRadioProductModule(
        private val repository: RadioDetectionRepo
    ) : ProductModule {
        private val inbound = RadioDetectionMqttInbound(repository) { _, _ -> }

        override val productType = ProductType.RadioDetection
        override val runtimeController: ProductRuntimeController = object : ProductRuntimeController {
            override val productType = ProductType.RadioDetection
            override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = emptyFlow()
            override fun clear() = repository.clearDevices()
        }
        override val floatingQuickControl: ProductFloatingQuickControl? = null

        override suspend fun handleRawMessage(
            serialNumber: String,
            message: String,
            isRetained: Boolean,
            parsedJson: JSONObject?
        ): Boolean {
            inbound.handleMessage(serialNumber, message, isRetained, parsedJson)
            return true
        }

        override suspend fun handleJsonEvent(
            serialNumber: String,
            eventType: String,
            json: JSONObject,
            isRetained: Boolean
        ) = Unit

        override fun cleanup() = inbound.cleanup()
    }

    private companion object {
        const val DEVICES_PER_PRODUCT = 20
        const val STATE_ROUNDS = 100
        const val FINAL_VALUE = 100
        const val BASE_TS = 1_900_000_000_000L
    }
}
