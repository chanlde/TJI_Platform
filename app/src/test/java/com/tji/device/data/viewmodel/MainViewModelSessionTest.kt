package com.tji.device.data.viewmodel

import com.tji.device.MainDispatcherRule
import com.tji.device.data.model.ProductType
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.session.AppSessionStore
import com.tji.device.data.session.deviceKey
import com.tji.device.product.ota.ProductDeviceInfo
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import com.tji.device.product.ota.ProductOtaRuntimeState
import com.tji.device.product.ota.ProductOtaStatus
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeController
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.service.MqttMessageHandler
import com.tji.device.service.MqttSubscriptionManager
import com.tji.network.MqttClientGateway
import com.tji.network.MqttConnectionConfig
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSessionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun accountTransitionsClearProductRuntimeAndOtaState() = runTest {
        val sessionStore = AppSessionStore()
        val runtimeController = RecordingRuntimeController()
        val otaRepository = RecordingOtaRepository()
        var replayClearCount = 0
        MainViewModel(
            authRepository = NoOpAuthRepository(),
            sessionStore = sessionStore,
            productRuntimeRegistryProvider = {
                ProductRuntimeRegistry(listOf(runtimeController))
            },
            mqttSubscriptionManagerProvider = {
                error("Account transition must not initialize subscription manager")
            },
            productOtaRuntimeRepositoryProvider = { otaRepository },
            clearRadioDetectionReplay = { replayClearCount += 1 }
        )
        advanceUntilIdle()

        sessionStore.startSession(account = "account-a", userId = "user-a", devices = emptyList())
        advanceUntilIdle()
        assertEquals(1, runtimeController.clearCount)
        assertEquals(1, otaRepository.clearCount)
        assertEquals(1, replayClearCount)

        sessionStore.clear()
        advanceUntilIdle()
        assertEquals(2, runtimeController.clearCount)
        assertEquals(2, otaRepository.clearCount)
        assertEquals(2, replayClearCount)
    }

    @Test
    fun fireBucketNavigationKeepsStatusMqttIndependentFromControlMode() = runTest {
        val sessionStore = AppSessionStore()
        val device = BoundAccountDevice(
            serialNumber = "LINK-001",
            name = "消防吊桶",
            productType = ProductType.FireBucket
        )
        sessionStore.startSession(account = "account", userId = "user", devices = listOf(device))
        val mqttGateway = RecordingMqttGateway()
        val subscriptionManager = MqttSubscriptionManager(
            mqttEventHandler = NoOpMqttMessageHandler(),
            clientFor = { mqttGateway }
        )
        val viewModel = MainViewModel(
            authRepository = NoOpAuthRepository(),
            sessionStore = sessionStore,
            productRuntimeRegistryProvider = { ProductRuntimeRegistry(emptyList()) },
            mqttSubscriptionManagerProvider = { subscriptionManager },
            productOtaRuntimeRepositoryProvider = { RecordingOtaRepository() },
            clearRadioDetectionReplay = {}
        )

        var result: Boolean? = null
        viewModel.openDevice(device) { success, _ -> result = success }
        advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(device.deviceKey, sessionStore.state.value.selectedDeviceKey)
        assertEquals(true, mqttGateway.subscribedTopics.isNotEmpty())
        subscriptionManager.cleanup()
    }

    @Test
    fun productListOpensWhileMqttSubscriptionFails() = runTest {
        val sessionStore = AppSessionStore()
        val device = BoundAccountDevice("SPEAKER-001", "喊话器", ProductType.Speaker)
        sessionStore.startSession("account", "user", listOf(device))
        val runtimeController = RecordingRuntimeController()
        val mqttGateway = RecordingMqttGateway(failSubscribe = true)
        val subscriptionManager = MqttSubscriptionManager(
            mqttEventHandler = NoOpMqttMessageHandler(),
            clientFor = { mqttGateway }
        )
        val viewModel = MainViewModel(
            authRepository = NoOpAuthRepository(),
            sessionStore = sessionStore,
            productRuntimeRegistryProvider = { ProductRuntimeRegistry(listOf(runtimeController)) },
            mqttSubscriptionManagerProvider = { subscriptionManager },
            productOtaRuntimeRepositoryProvider = { RecordingOtaRepository() },
            clearRadioDetectionReplay = {}
        )

        var result: Boolean? = null
        viewModel.openProduct(ProductType.Speaker) { success, _ -> result = success }
        advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(ProductType.Speaker, sessionStore.state.value.preferredProductType)
        assertEquals(1, runtimeController.clearCount)
        subscriptionManager.cleanup()
    }

    @Test
    fun deviceOpensOfflineAfterMqttSubscriptionFails() = runTest {
        val sessionStore = AppSessionStore()
        val device = BoundAccountDevice("SPEAKER-001", "喊话器", ProductType.Speaker)
        sessionStore.startSession("account", "user", listOf(device))
        val runtimeController = RecordingRuntimeController()
        val mqttGateway = RecordingMqttGateway(failSubscribe = true)
        val subscriptionManager = MqttSubscriptionManager(
            mqttEventHandler = NoOpMqttMessageHandler(),
            clientFor = { mqttGateway }
        )
        val viewModel = MainViewModel(
            authRepository = NoOpAuthRepository(),
            sessionStore = sessionStore,
            productRuntimeRegistryProvider = { ProductRuntimeRegistry(listOf(runtimeController)) },
            mqttSubscriptionManagerProvider = { subscriptionManager },
            productOtaRuntimeRepositoryProvider = { RecordingOtaRepository() },
            clearRadioDetectionReplay = {}
        )

        var result: Boolean? = null
        var message: String? = null
        viewModel.openDevice(device) { success, detail ->
            result = success
            message = detail
        }
        advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(device.deviceKey, sessionStore.state.value.selectedDeviceKey)
        assertEquals("设备未连接，已打开离线页面", message)
        assertEquals(1, runtimeController.clearCount)
        subscriptionManager.cleanup()
    }

    private class RecordingRuntimeController : ProductRuntimeController {
        override val productType: ProductType = ProductType.Speaker
        override val devices: Flow<List<ProductDeviceRuntimeSnapshot>> = MutableStateFlow(emptyList())
        var clearCount = 0

        override fun clear() {
            clearCount += 1
        }
    }

    private class RecordingOtaRepository : ProductOtaRuntimeRepository {
        override val states: StateFlow<List<ProductOtaRuntimeState>> = MutableStateFlow(emptyList())
        var clearCount = 0

        override fun updateDeviceInfo(
            productType: ProductType,
            serialNumber: String,
            deviceInfo: ProductDeviceInfo
        ) = Unit

        override fun updateOtaStatus(
            productType: ProductType,
            serialNumber: String,
            otaStatus: ProductOtaStatus
        ) = Unit

        override fun updateLifecycle(
            productType: ProductType,
            serialNumber: String,
            eventType: String,
            timestamp: Long?,
            isRetained: Boolean
        ) = Unit

        override fun clearAll() {
            clearCount += 1
        }
    }

    private class NoOpAuthRepository : AuthRepository {
        override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {
            error("Not used")
        }

        override suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> =
            ApiResponse(code = 200, message = "ok", data = Unit)

        override suspend fun logout() = Unit
    }

    private class RecordingMqttGateway(
        private val failSubscribe: Boolean = false
    ) : MqttClientGateway {
        val subscribedTopics = mutableListOf<String>()

        override fun getConfig(): MqttConnectionConfig = MqttConnectionConfig.default()

        override suspend fun subscribeAwait(
            topic: String,
            qos: Int,
            onMessage: (message: String, isRetained: Boolean) -> Unit
        ): Result<Unit> {
            if (failSubscribe) return Result.failure(IllegalStateException("offline"))
            subscribedTopics += topic
            return Result.success(Unit)
        }

        override suspend fun unsubscribeAwait(topic: String): Result<Unit> = Result.success(Unit)

        override suspend fun publishAwait(
            topic: String,
            message: String,
            qos: Int,
            retain: Boolean,
            queueWhenDisconnected: Boolean
        ): Result<Unit> = Result.success(Unit)
    }

    private class NoOpMqttMessageHandler : MqttMessageHandler {
        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) = Unit

        override fun cleanup() = Unit
    }
}
