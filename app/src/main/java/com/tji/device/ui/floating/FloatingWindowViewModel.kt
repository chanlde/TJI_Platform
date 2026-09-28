package com.tji.device.ui.floating

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.BuildConfig
import com.tji.device.data.model.ProductType
import com.tji.device.data.session.AppSessionState
import com.tji.device.data.session.AppSessionStore
import com.tji.device.di.ProductFloatingQuickControl
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.transport.DIRECT_FIRE_BUCKET_LINK_ID
import com.tji.device.product.firebucket.transport.DIRECT_FIRE_BUCKET_LINK_NAME
import com.tji.device.product.firebucket.transport.DirectFireBucketState
import com.tji.device.product.firebucket.transport.DirectFireBucketStateStore
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.product.firebucket.transport.FireBucketConnectionModeStore
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.error.toUserVisibleMessage
import com.tji.device.ui.AppUiNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * 
 * @param productRuntimeRegistry 统一产品运行时注册表，用于读取所有产品的设备状态
 * @param floatingQuickControlFor 按选中设备 [ProductType] 解析悬浮窗快捷开关实现
 */
class FloatingWindowViewModel(
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val floatingQuickControlFor: (ProductType) -> ProductFloatingQuickControl,
    private val sessionStore: AppSessionStore,
    private val directFireBucketStateStore: DirectFireBucketStateStore,
    private val connectionModeStore: FireBucketConnectionModeStore,
    private val commandErrorReporter: (String) -> Unit = AppUiNotifier::showShortMessage
) : ViewModel() {

    private val _uiState = MutableStateFlow(FloatingWindowUiState())
    val uiState: StateFlow<FloatingWindowUiState> = _uiState

    companion object {
        private const val TAG = "FloatingWindowVM"
    }

    init {
        observeSessionAndLinks()
    }

    private fun observeSessionAndLinks() {
        viewModelScope.launch {
            val supportedProductTypes = ProductType.values()
                .filter { it.supportsFloatingWindow() }
                .toSet()
            val linkSummaries = combine(
                productRuntimeRegistry.deviceFlowsFor(supportedProductTypes)
            ) { deviceLists ->
                deviceLists
                    .flatMap { it }
                    .map { it.toFloatingSummary() }
            }.distinctUntilChanged()
            combine(
                linkSummaries,
                sessionStore.state,
                directFireBucketStateStore.state,
                connectionModeStore.mode
            ) { summaries, session, directState, connectionMode ->
                FloatingWindowSources(summaries, session, directState, connectionMode)
            }.collect { sources ->
                _uiState.update { current ->
                    if (sources.connectionMode == FireBucketConnectionMode.DIRECT_LINK) {
                        val directLink = sources.directState.toFloatingLinkSummary()
                        return@update current.copy(
                            links = listOfNotNull(directLink),
                            selectedLinkSerial = directLink?.serialNumber,
                            selectedLinkName = directLink?.name,
                            preferredProductType = ProductType.FireBucket
                        )
                    }
                    val summaries = sources.linkSummaries
                    val selectedKey = sources.session.selectedDeviceKey
                    val selectedBoundDevice = selectedKey?.let { key ->
                        sources.session.boundDevices.firstOrNull {
                            it.productType == key.productType && it.serialNumber == key.serialNumber
                        }
                    } ?: sources.session.boundDevices.firstOrNull {
                        it.productType == sources.session.preferredProductType
                    }
                    val explicitlySelected = selectedKey?.let { key ->
                        summaries.firstOrNull {
                            it.productType == key.productType && it.serialNumber == key.serialNumber
                        }
                    }
                    val selectedLink = explicitlySelected
                        ?: summaries.firstOrNull {
                            it.productType == sources.session.preferredProductType && it.isOnline
                        }
                        ?: summaries.firstOrNull {
                            it.productType == sources.session.preferredProductType
                        }
                    val selectedProductType =
                        selectedLink?.productType
                            ?: selectedBoundDevice?.productType
                            ?: sources.session.preferredProductType
                    val selectedSerial = when {
                        !selectedProductType.supportsFloatingWindow() -> null
                        selectedKey != null -> selectedKey.serialNumber
                        else -> selectedLink?.serialNumber ?: selectedBoundDevice?.serialNumber
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "更新 UI -> summaries=${summaries.size}, selectedSerial=$selectedSerial"
                        )
                    }

                    current.copy(
                        links = summaries,
                        selectedLinkSerial = selectedSerial,
                        selectedLinkName = selectedLink?.name ?: selectedBoundDevice?.name,
                        preferredProductType = selectedProductType.floatingFallback()
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

    private fun toggleSwitch(
        link: FloatingLinkSummary,
        switch: FloatingSwitchSummary,
        targetAngle: Int
    ) {
        viewModelScope.launch {
            executeFloatingQuickToggle(
                control = floatingQuickControlFor(link.productType),
                linkSerial = link.serialNumber,
                switchSerial = switch.serialNumber,
                targetAngle = targetAngle,
                onFailure = { throwable ->
                    Log.e(
                        TAG,
                        "悬浮窗控制失败: product=${link.productType}",
                        throwable
                    )
                    commandErrorReporter(
                        throwable.toUserVisibleMessage("控制失败，请检查设备和网络")
                    )
                }
            )
        }
    }

    fun toggleSwitch(linkSerial: String, switch: FloatingSwitchSummary, isOpen: Boolean) {
        val latestLink = _uiState.value.selectedLink
            ?.takeIf { it.serialNumber == linkSerial }
        val latestSwitch = latestLink
            ?.allSwitches
            ?.firstOrNull { it.serialNumber == switch.serialNumber }
        if (latestLink == null || latestSwitch == null ||
            !canToggleFloatingSwitch(latestLink, latestSwitch)
        ) {
            Log.w(
                TAG,
                "忽略失效的悬浮窗控制"
            )
            return
        }
        val targetAngle = if (isOpen) 90 else 0
        Log.d(
            TAG,
            "toggleSwitch -> link=$linkSerial switch=${switch.serialNumber} isOpen=$isOpen targetAngle=$targetAngle"
        )
        toggleSwitch(latestLink, latestSwitch, targetAngle)
    }
}

internal fun canToggleFloatingSwitch(
    link: FloatingLinkSummary?,
    switch: FloatingSwitchSummary?
): Boolean =
    link?.isOnline == true && switch != null

private fun ProductType.supportsFloatingWindow(): Boolean =
    this != ProductType.RadioDetection

private fun ProductType.floatingFallback(): ProductType =
    if (supportsFloatingWindow()) this else ProductType.FireBucket

class FloatingWindowViewModelFactory(
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val floatingQuickControlFor: (ProductType) -> ProductFloatingQuickControl,
    private val sessionStore: AppSessionStore,
    private val directFireBucketStateStore: DirectFireBucketStateStore,
    private val connectionModeStore: FireBucketConnectionModeStore,
    private val commandErrorReporter: (String) -> Unit = AppUiNotifier::showShortMessage
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FloatingWindowViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FloatingWindowViewModel(
                productRuntimeRegistry = productRuntimeRegistry,
                floatingQuickControlFor = floatingQuickControlFor,
                sessionStore = sessionStore,
                directFireBucketStateStore = directFireBucketStateStore,
                connectionModeStore = connectionModeStore,
                commandErrorReporter = commandErrorReporter
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private data class FloatingWindowSources(
    val linkSummaries: List<FloatingLinkSummary>,
    val session: AppSessionState,
    val directState: DirectFireBucketState,
    val connectionMode: FireBucketConnectionMode
)

internal fun DirectFireBucketState.toFloatingLinkSummary(): FloatingLinkSummary? {
    if (buckets.isEmpty()) return null
    return FloatingLinkSummary.fromSwitches(
        serialNumber = DIRECT_FIRE_BUCKET_LINK_ID,
        name = DIRECT_FIRE_BUCKET_LINK_NAME,
        isOnline = isConnected,
        productType = ProductType.FireBucket,
        switches = buckets
    )
}

internal suspend fun executeFloatingQuickToggle(
    control: ProductFloatingQuickControl,
    linkSerial: String,
    switchSerial: String,
    targetAngle: Int,
    onFailure: (Throwable) -> Unit
) {
    try {
        control.toggleSwitch(linkSerial, switchSerial, targetAngle)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Exception) {
        onFailure(throwable)
    }
}
