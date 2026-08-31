package com.tji.device.data.session

import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

data class DeviceKey(
    val productType: ProductType,
    val serialNumber: String
)

val BoundAccountDevice.deviceKey: DeviceKey
    get() = DeviceKey(productType = productType, serialNumber = serialNumber)

data class AppSessionState(
    val account: String = "",
    val userId: String? = null,
    val boundDevices: List<BoundAccountDevice> = emptyList(),
    val selectedDeviceKey: DeviceKey? = null,
    val preferredProductType: ProductType = ProductType.FireBucket
)

/**
 * 当前登录会话的唯一状态源。
 *
 * 这里只保存跨页面共享的账号设备和选择状态；页面展开、弹窗等临时 UI 状态仍由对应界面管理。
 * 账号、设备与选择必须作为一个整体更新，避免观察者收到“新设备 + 旧产品类型”等中间状态。
 */
class AppSessionStore {
    private val _state = MutableStateFlow(AppSessionState())
    val state: StateFlow<AppSessionState> = _state.asStateFlow()
    private val sessionGeneration = AtomicLong(0L)

    fun startSession(account: String, userId: String?, devices: List<BoundAccountDevice>) {
        val uniqueDevices = devices.distinctBy { it.deviceKey }
        sessionGeneration.incrementAndGet()
        _state.value = AppSessionState(
            account = account,
            userId = userId,
            boundDevices = uniqueDevices,
            preferredProductType =
                uniqueDevices.firstOrNull()?.productType ?: ProductType.FireBucket
        )
    }

    /**
     * 在异步清理旧账号资源之前立即使旧请求令牌失效；状态本身会由随后
     * 的 [startSession] 或 [clear] 一次性替换，避免发布半完成会话。
     */
    fun beginSessionTransition() {
        sessionGeneration.incrementAndGet()
    }

    fun selectDevice(device: BoundAccountDevice?) {
        selectDeviceKey(device?.deviceKey)
    }

    fun selectDeviceKey(key: DeviceKey?) {
        _state.update { current ->
            val validKey = key?.takeIf { requested ->
                current.boundDevices.any { it.deviceKey == requested }
            }
            current.copy(
                selectedDeviceKey = validKey,
                preferredProductType = validKey?.productType ?: current.preferredProductType
            )
        }
    }

    fun openProduct(productType: ProductType) {
        _state.update {
            it.copy(
                selectedDeviceKey = null,
                preferredProductType = productType
            )
        }
    }

    fun renameDevice(device: BoundAccountDevice, newName: String) {
        _state.update { session ->
            session.copy(
                boundDevices = session.boundDevices.map { current ->
                    if (current.deviceKey == device.deviceKey && current.serverId == device.serverId) {
                        current.copy(name = newName)
                    } else {
                        current
                    }
                }
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedDeviceKey = null) }
    }

    fun clear() {
        sessionGeneration.incrementAndGet()
        _state.value = AppSessionState()
    }

    /**
     * 每次会话切换开始、登录会话建立或清空时递增。异步业务提交结果前应校验该值，
     * 避免旧账号请求写入恰好拥有相同设备 key 的新账号。
     */
    fun currentSessionGeneration(): Long = sessionGeneration.get()
}
