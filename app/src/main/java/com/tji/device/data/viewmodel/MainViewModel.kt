package com.tji.device.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.BuildConfig
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.data.model.TestDeviceFallbacks
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.viewmodel.LoginViewModel.Companion.TAG
import com.tji.device.data.vminterface.LoginViewModelInterface
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.service.MqttSubscriptionManager
import com.tji.device.service.SubscriptionTarget
import com.tji.device.util.userData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主视图模型：协调设备列表、登录与按产品订阅 MQTT；产品线专属控制（如 FireBucket 开关）由各产品 UI 侧 ViewModel 承担。
 */
class MainViewModel(
    val loginViewModel: LoginViewModelInterface,
    private val authRepository: AuthRepository,
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val mqttSubscriptionManager: MqttSubscriptionManager
) : ViewModel() {

    val runtimeDevices: StateFlow<List<ProductDeviceRuntimeSnapshot>> =
        combine(productRuntimeRegistry.deviceFlows) { deviceLists ->
            deviceLists.flatMap { it }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    private val productSubscriptionLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // 监听账号变化，清理设备列表
            loginViewModel.account.collect {
                productRuntimeRegistry.clearAll()
            }
        }
        
        viewModelScope.launch {
            // 当需要用户重新选择 Link 时（多个设备），立即清理旧设备列表
            loginViewModel.needAccountDeviceSelection.collect { linkList ->
                if (linkList != null) {
                    Log.d(TAG, "需要重新选择绑定设备，清理运行时设备列表")
                    productRuntimeRegistry.clearAll()
                }
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
        val normalizedName = newName.trim()
        if (id == null) {
            callback(false, "当前设备缺少后台 ID，无法修改名称")
            return
        }
        if (normalizedName.isBlank()) {
            callback(false, "设备名不能为空")
            return
        }

        viewModelScope.launch {
            try {
                val response = authRepository.updateDeviceName(id = id, productName = normalizedName)
                if (response.code == 200) {
                    userData.boundAccountDevices = userData.boundAccountDevices.orEmpty().map {
                        if (
                            it.serverId == id &&
                            it.productType == device.productType &&
                            it.serialNumber == device.serialNumber
                        ) {
                            it.copy(name = normalizedName)
                        } else {
                            it
                        }
                    }
                    callback(true, null)
                } else {
                    callback(false, response.message ?: "修改设备名失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "修改设备名异常: ${device.serialNumber}", e)
                callback(false, "修改设备名失败，请稍后重试")
            }
        }
    }

    fun openProduct(productType: ProductType) {
        viewModelScope.launch {
            productSubscriptionLoading.value = true
            try {
                userData.preferredProductType = productType
                userData.selectedLinkSerial = null
                productRuntimeRegistry.clearAll()

                val targetSerials = userData.boundAccountDevices
                    .orEmpty()
                    .filter { it.productType == productType }
                    .map { it.serialNumber }
                    .ifEmpty {
                        if (BuildConfig.TJI_ENABLE_LOCAL_DEMO_DEVICES) {
                            TestDeviceFallbacks.demoDeviceFor(productType)?.let { listOf(it.serialNumber) }.orEmpty()
                        } else {
                            emptyList()
                        }
                    }

                val desiredTargets = targetSerials.map { SubscriptionTarget(it, productType) }
                val currentTargets = mqttSubscriptionManager.getSubscribedTargets()
                val targetsToUnsubscribe = currentTargets.filter { it !in desiredTargets }
                val currentProductSerials = mqttSubscriptionManager.getSubscribedDevices(productType)
                val devicesToSubscribe = targetSerials.filter { it !in currentProductSerials }

                Log.d(
                    TAG,
                    "打开产品 MQTT 订阅检查: product=$productType targetSerials=$targetSerials " +
                        "current=$currentProductSerials subscribe=$devicesToSubscribe unsubscribe=$targetsToUnsubscribe"
                )
                if (targetsToUnsubscribe.isNotEmpty()) {
                    mqttSubscriptionManager.unsubscribeFromTargets(targetsToUnsubscribe)
                }
                if (devicesToSubscribe.isNotEmpty()) {
                    mqttSubscriptionManager.subscribeToDevices(devicesToSubscribe, productType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "打开产品失败: $productType", e)
            } finally {
                productSubscriptionLoading.value = false
            }
        }
    }

    fun openDevice(device: BoundAccountDevice) {
        viewModelScope.launch {
            productSubscriptionLoading.value = true
            try {
                loginViewModel.selectBoundAccountDevice(device)
                userData.preferredProductType = device.productType

                val desiredTargets = userData.boundAccountDevices
                    .orEmpty()
                    .filter { it.productType == device.productType }
                    .map { SubscriptionTarget(it.serialNumber, it.productType) }
                    .ifEmpty {
                        when (device.productType) {
                            ProductType.DropperSixStage,
                            ProductType.RadioDetection,
                            ProductType.Speaker,
                            ProductType.BreakWindowProjectile,
                            ProductType.Searchlight -> listOf(SubscriptionTarget(device.serialNumber, device.productType))
                            else -> emptyList()
                        }
                    }

                val currentTargets = mqttSubscriptionManager.getSubscribedTargets()
                val targetsToUnsubscribe = currentTargets.filter { it !in desiredTargets }
                val currentProductSerials = mqttSubscriptionManager.getSubscribedDevices(device.productType)
                val devicesToSubscribe = desiredTargets
                    .map { it.serialNumber }
                    .filter { it !in currentProductSerials }

                Log.d(
                    TAG,
                    "打开设备 MQTT 订阅检查: product=${device.productType} selected=${device.serialNumber} " +
                        "desired=$desiredTargets current=$currentProductSerials subscribe=$devicesToSubscribe unsubscribe=$targetsToUnsubscribe"
                )
                if (targetsToUnsubscribe.isNotEmpty()) {
                    mqttSubscriptionManager.unsubscribeFromTargets(targetsToUnsubscribe)
                }
                if (devicesToSubscribe.isNotEmpty()) {
                    mqttSubscriptionManager.subscribeToDevices(devicesToSubscribe, device.productType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "打开设备失败: ${device.serialNumber} product=${device.productType}", e)
            } finally {
                productSubscriptionLoading.value = false
            }
        }
    }
}
