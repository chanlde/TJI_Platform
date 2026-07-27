package com.tji.device.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.concurrent.LatestRequestTracker
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.session.AppSessionStore
import com.tji.device.data.session.DeviceKey
import com.tji.device.data.session.deviceKey
import com.tji.device.data.viewmodel.LoginViewModel.Companion.TAG
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import com.tji.device.service.MqttSubscriptionManager
import com.tji.device.service.SubscriptionTarget
import com.tji.device.error.toUserVisibleServerMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 主视图模型：协调设备列表、登录与按产品订阅 MQTT；产品线专属控制（如 FireBucket 开关）由各产品 UI 侧 ViewModel 承担。
 */
class MainViewModel(
    val loginViewModel: LoginViewModel,
    private val authRepository: AuthRepository,
    val sessionStore: AppSessionStore,
    productRuntimeRegistryProvider: () -> ProductRuntimeRegistry,
    mqttSubscriptionManagerProvider: () -> MqttSubscriptionManager,
    productOtaRuntimeRepositoryProvider: () -> ProductOtaRuntimeRepository
) : ViewModel() {
    private val productRuntimeRegistry by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
        productRuntimeRegistryProvider
    )
    private val mqttSubscriptionManager by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
        mqttSubscriptionManagerProvider
    )
    private val productOtaRuntimeRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
        productOtaRuntimeRepositoryProvider
    )

    val runtimeDevices: StateFlow<List<ProductDeviceRuntimeSnapshot>> by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        combine(productRuntimeRegistry.deviceFlows) { deviceLists ->
            deviceLists.flatMap { it }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
    private val productSubscriptionLoading = MutableStateFlow(false)
    private val navigationRequests = LatestRequestTracker<Unit>()
    private var productNavigationJob: Job? = null
    private val renameRequests = LatestRequestTracker<DeviceKey>()
    private val renameJobs = mutableMapOf<DeviceKey, Job>()

    init {
        viewModelScope.launch {
            var isInitialAccountValue = true
            // 监听账号变化，清理设备列表
            loginViewModel.account.collect {
                if (isInitialAccountValue) {
                    isInitialAccountValue = false
                    return@collect
                }
                cancelAccountScopedRequests()
                productRuntimeRegistry.clearAll()
                productOtaRuntimeRepository.clearAll()
            }
        }
        
        // 不再监听 selectedLinkSerial 清空运行时。
        // 统一平台里一个账号可以有多个 FireBucket Link，进入某个 Link 只是切换控制目标；
        // 运行时列表必须保留所有已订阅 Link 及其桶列表，否则悬浮窗会丢失可切换的桶。
    }

    val isLoading: StateFlow<Boolean> = combine(
        loginViewModel.uiState.map { it.isLoading },
        productSubscriptionLoading
    ) { loginLoading, productLoading ->
        loginLoading || productLoading
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun login(account: String, password: String, rememberMe: Boolean, callback: (Boolean, String?) -> Unit) {
        loginViewModel.login(account, password, rememberMe, callback)
    }

    fun updateDeviceName(
        device: BoundAccountDevice,
        newName: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val id = device.serverId
        val deviceKey = device.deviceKey
        val normalizedName = newName.trim()
        if (id == null) {
            callback(false, "当前设备缺少后台 ID，无法修改名称")
            return
        }
        if (normalizedName.isBlank()) {
            callback(false, "设备名不能为空")
            return
        }

        val requestId = renameRequests.begin(deviceKey)
        val sessionGeneration = sessionStore.currentSessionGeneration()
        renameJobs.remove(deviceKey)?.cancel()
        renameJobs[deviceKey] = viewModelScope.launch {
            try {
                val response = authRepository.updateDeviceName(id = id, productName = normalizedName)
                if (!isCurrentRename(deviceKey, requestId, sessionGeneration)) return@launch
                if (response.code == 200) {
                    sessionStore.renameDevice(device, normalizedName)
                    callback(true, null)
                } else {
                    callback(false, response.message.toUserVisibleServerMessage("修改设备名失败"))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                if (!isCurrentRename(deviceKey, requestId, sessionGeneration)) return@launch
                Log.e(TAG, "修改设备名异常: ${device.serialNumber}", e)
                callback(false, "修改设备名失败，请稍后重试")
            } finally {
                if (renameRequests.isLatest(deviceKey, requestId)) {
                    renameRequests.complete(deviceKey, requestId)
                    renameJobs.remove(deviceKey)
                }
            }
        }
    }

    private fun isCurrentRename(
        deviceKey: DeviceKey,
        requestId: Long,
        sessionGeneration: Long
    ): Boolean =
        renameRequests.isLatest(deviceKey, requestId) &&
            sessionStore.currentSessionGeneration() == sessionGeneration

    fun openProduct(
        productType: ProductType,
        callback: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        val requestId = navigationRequests.begin(Unit)
        val sessionGeneration = sessionStore.currentSessionGeneration()
        productNavigationJob?.cancel()
        productNavigationJob = viewModelScope.launch {
            productSubscriptionLoading.value = true
            try {
                if (!isCurrentNavigation(requestId, sessionGeneration)) return@launch
                val previousTargets = mqttSubscriptionManager.getSubscribedTargets()
                val targetSerials = sessionStore.state.value.boundDevices
                    .filter { it.productType == productType }
                    .map { it.serialNumber }

                val desiredTargets = targetSerials.map { SubscriptionTarget(it, productType) }
                mqttSubscriptionManager.reconcileSubscriptions(desiredTargets)
                if (!isCurrentNavigation(requestId, sessionGeneration)) return@launch
                clearRetiredProductRuntimes(previousTargets, desiredTargets)
                sessionStore.openProduct(productType)
                callback(true, null)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "打开产品失败: $productType", e)
                if (isCurrentNavigation(requestId, sessionGeneration)) {
                    callback(false, "连接设备失败，请检查网络后重试")
                }
            } finally {
                if (navigationRequests.isLatest(Unit, requestId)) {
                    productSubscriptionLoading.value = false
                    navigationRequests.complete(Unit, requestId)
                    productNavigationJob = null
                }
            }
        }
    }

    fun openDevice(
        device: BoundAccountDevice,
        callback: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        val requestId = navigationRequests.begin(Unit)
        val sessionGeneration = sessionStore.currentSessionGeneration()
        productNavigationJob?.cancel()
        productNavigationJob = viewModelScope.launch {
            productSubscriptionLoading.value = true
            try {
                if (!isCurrentNavigation(requestId, sessionGeneration)) return@launch
                val previousTargets = mqttSubscriptionManager.getSubscribedTargets()
                val desiredTargets = sessionStore.state.value.boundDevices
                    .filter { it.productType == device.productType }
                    .map { SubscriptionTarget(it.serialNumber, it.productType) }

                mqttSubscriptionManager.reconcileSubscriptions(desiredTargets)
                if (!isCurrentNavigation(requestId, sessionGeneration)) return@launch
                clearRetiredProductRuntimes(previousTargets, desiredTargets)
                sessionStore.selectDevice(device)
                callback(true, null)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "打开设备失败: ${device.serialNumber} product=${device.productType}", e)
                if (isCurrentNavigation(requestId, sessionGeneration)) {
                    callback(false, "连接设备失败，请检查网络后重试")
                }
            } finally {
                if (navigationRequests.isLatest(Unit, requestId)) {
                    productSubscriptionLoading.value = false
                    navigationRequests.complete(Unit, requestId)
                    productNavigationJob = null
                }
            }
        }
    }

    private fun isCurrentNavigation(requestId: Long, sessionGeneration: Long): Boolean =
        navigationRequests.isLatest(Unit, requestId) &&
            sessionStore.currentSessionGeneration() == sessionGeneration

    private fun clearRetiredProductRuntimes(
        previousTargets: Collection<SubscriptionTarget>,
        desiredTargets: Collection<SubscriptionTarget>
    ) {
        retiredProductTypes(previousTargets, desiredTargets)
            .forEach(productRuntimeRegistry::clear)
    }

    private fun cancelAccountScopedRequests() {
        navigationRequests.invalidateAll()
        productNavigationJob?.cancel()
        productNavigationJob = null
        productSubscriptionLoading.value = false

        renameRequests.invalidateAll()
        renameJobs.values.forEach { it.cancel() }
        renameJobs.clear()
    }
}

internal fun retiredProductTypes(
    previousTargets: Collection<SubscriptionTarget>,
    desiredTargets: Collection<SubscriptionTarget>
): Set<ProductType> =
    previousTargets.mapTo(mutableSetOf()) { it.productType } -
        desiredTargets.mapTo(mutableSetOf()) { it.productType }
