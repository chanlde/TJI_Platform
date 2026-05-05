package com.tji.device.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
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

        viewModelScope.launch {
            // 监听选中的 Link SN 变化，当变化时清理旧设备
            var previousSerial: String? = null
            loginViewModel.selectedLinkSerial.collect { currentSerial ->
                val switchedToDifferentLink =
                    previousSerial != null && currentSerial != null && previousSerial != currentSerial
                val clearedSelection = previousSerial != null && currentSerial == null

                if (switchedToDifferentLink || clearedSelection) {
                    Log.d(
                        TAG,
                        "检测到 Link 状态变化: $previousSerial -> $currentSerial，清理旧设备列表"
                    )
                    productRuntimeRegistry.clearAll()
                }

                previousSerial = currentSerial
            }
        }
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

                val desiredTargets = targetSerials.map { SubscriptionTarget(it, productType) }
                val currentTargets = mqttSubscriptionManager.getSubscribedTargets()
                val targetsToUnsubscribe = currentTargets.filter { it !in desiredTargets }
                val currentProductSerials = mqttSubscriptionManager.getSubscribedDevices(productType)
                val devicesToSubscribe = targetSerials.filter { it !in currentProductSerials }

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

                val currentTargets = mqttSubscriptionManager.getSubscribedTargets()
                val targetsToUnsubscribe = currentTargets.filter { it !in desiredTargets }
                val currentProductSerials = mqttSubscriptionManager.getSubscribedDevices(device.productType)
                val devicesToSubscribe = desiredTargets
                    .map { it.serialNumber }
                    .filter { it !in currentProductSerials }

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
