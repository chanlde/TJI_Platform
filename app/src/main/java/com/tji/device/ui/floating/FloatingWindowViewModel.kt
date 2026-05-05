package com.tji.device.ui.floating

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.ProductType
import com.tji.device.di.ProductFloatingQuickControl
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.util.userData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 悬浮窗视图模型
 * 
 * 负责管理悬浮窗的 UI 状态和设备控制逻辑。
 * 
 * 功能：
 * - 监听设备列表变化，自动更新 UI 状态
 * - 处理开关控制操作
 * - 管理悬浮窗的展开/收起状态
 * 
 * @param productRuntimeRegistry 统一产品运行时注册表，用于读取所有产品的设备状态
 * @param floatingQuickControlFor 按选中设备 [ProductType] 解析悬浮窗快捷开关实现
 */
class FloatingWindowViewModel(
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val floatingQuickControlFor: (ProductType) -> ProductFloatingQuickControl
) : ViewModel() {

    private val _uiState = MutableStateFlow(FloatingWindowUiState())
    val uiState: StateFlow<FloatingWindowUiState> = _uiState

    companion object {
        private const val TAG = "FloatingWindowVM"
    }
    fun debugStateHash(): Int = System.identityHashCode(_uiState)

    init {
        observeLinks()
        observePreferredProductType()
        Log.d("FloatingWindowService", "Current mode: ${uiState.value.mode}")

    }

    private fun observePreferredProductType() {
        viewModelScope.launch {
            userData.preferredProductTypeFlow.collect { preferredType ->
                _uiState.update { current ->
                    if (current.selectedLink != null) {
                        current
                    } else {
                        current.copy(preferredProductType = preferredType)
                    }
                }
            }
        }
    }

    private fun observeLinks() {
        viewModelScope.launch {
            combine(productRuntimeRegistry.deviceFlows) { deviceLists ->
                deviceLists.flatMap { it }
            }.collect { devices ->
                Log.d(TAG, "observeLinks -> 接收到设备列表 size=${devices.size}")
                _uiState.update { current ->
                    val summaries = devices.map { it.toFloatingSummary() }
                    val selectedSerial = current.selectedLinkSerial?.takeIf { serial ->
                        summaries.any { it.serialNumber == serial }
                    } ?: summaries.firstOrNull {
                        it.productType == current.preferredProductType && it.isOnline
                    }?.serialNumber
                    ?: summaries.firstOrNull {
                        it.productType == current.preferredProductType
                    }?.serialNumber

                    val selectedLink = summaries.firstOrNull { it.serialNumber == selectedSerial }
                    val shouldForceOfflineVisible =
                        selectedLink?.onlineSwitches?.isEmpty() == true &&
                                selectedLink.offlineSwitches.isNotEmpty()

                    Log.d(
                        TAG,
                        "更新 UI -> summaries=${summaries.size}, selectedSerial=$selectedSerial, stateHash=${debugStateHash()}"
                    )

                    current.copy(
                        links = summaries,
                        selectedLinkSerial = selectedSerial,
                        preferredProductType = selectedLink?.productType
                            ?: current.preferredProductType,
                        isLoading = false,
                        errorMessage = if (summaries.isEmpty()) "暂无可用设备" else null,
                        showOfflineSwitches = current.showOfflineSwitches || shouldForceOfflineVisible
                    )
                }
            }
        }
    }

    private fun ProductDeviceRuntimeSnapshot.toFloatingSummary(): FloatingLinkSummary {
        val fireBucketPayload = payload as? FireBucketLinkDevice
        return if (fireBucketPayload != null) {
            FloatingLinkSummary.fromSwitches(
                serialNumber = serialNumber,
                name = name,
                isOnline = isOnline,
                productType = productType,
                switches = fireBucketPayload.subDevices
            )
        } else {
            FloatingLinkSummary(
                serialNumber = serialNumber,
                name = name,
                isOnline = isOnline,
                productType = productType,
                onlineSwitches = emptyList(),
                offlineSwitches = emptyList()
            )
        }
    }

    fun minimize() {
        Log.d(TAG, "minimize -> 回到 ICON 模式")
        _uiState.update { it.copy(mode = FloatingWindowMode.ICON) }
    }

    fun selectLink(serialNumber: String) {
        _uiState.update { state ->
            val link = state.links.firstOrNull { it.serialNumber == serialNumber }
            if (link != null) {
                state.copy(
                    selectedLinkSerial = serialNumber,
                    preferredProductType = link.productType
                )
            } else {
                state
            }
        }
    }

    fun toggleOfflineVisibility() {
        _uiState.update { it.copy(showOfflineSwitches = !it.showOfflineSwitches) }
    }

    fun toggleSwitch(linkSerial: String, switchSerial: String, targetAngle: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            val link = state.links.firstOrNull { it.serialNumber == linkSerial }
            val productType = link?.productType ?: state.activeProductType
            floatingQuickControlFor(productType).toggleSwitch(linkSerial, switchSerial, targetAngle)
        }
    }

    fun toggleSwitch(linkSerial: String, switch: FloatingSwitchSummary, isOpen: Boolean) {
        val targetAngle = if (isOpen) 90 else 0
        Log.d(
            TAG,
            "toggleSwitch -> link=$linkSerial switch=${switch.serialNumber} isOpen=$isOpen targetAngle=$targetAngle"
        )
        toggleSwitch(linkSerial, switch.serialNumber, targetAngle)
    }
}

class FloatingWindowViewModelFactory(
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val floatingQuickControlFor: (ProductType) -> ProductFloatingQuickControl
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FloatingWindowViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FloatingWindowViewModel(
                productRuntimeRegistry = productRuntimeRegistry,
                floatingQuickControlFor = floatingQuickControlFor
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
