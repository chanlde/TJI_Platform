package com.tji.device.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.LoginUiState
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.vminterface.LoginViewModelInterface
import com.tji.device.di.AppContainer.mqttSubscriptionManager
import com.tji.device.service.mqtt.ProductMqttRouter
import com.tji.device.util.userData
import com.tji.network.data.ApiResponse
import com.tji.network.DataReportManager
import com.tji.network.data.BoundDeviceRow
import com.tji.network.data.LoginResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 登录视图模型
 * 
 * 负责处理用户登录、登出逻辑，管理登录状态和账号信息。
 * 
 * 功能：
 * - 用户登录认证
 * - 登录成功后清理旧订阅（避免切换用户时订阅残留）
 * - 保存用户设备列表（登录响应 `boundDeviceRows` / 旧字段 `bucketsns`）
 * - 用户登出时清理所有订阅和数据
 * 
 * 订阅管理：
 * - 登录前会检查并清理旧订阅（如果切换用户）
 * - 登录成功后不立即订阅，等待 FloatingWindowViewModel 根据用户选择订阅
 * - 登出时清理所有 MQTT 订阅
 * 
 * @param authRepository 认证仓库，用于执行登录/登出操作
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel(),
    LoginViewModelInterface {

    companion object {
         const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _account = MutableStateFlow("")  // 初始值为空字符串
    override val account: StateFlow<String> = _account.asStateFlow() // 公开的只读 StateFlow

    private val _needAccountDeviceSelection = MutableStateFlow<List<BoundAccountDevice>?>(null)
    override val needAccountDeviceSelection: StateFlow<List<BoundAccountDevice>?> =
        _needAccountDeviceSelection.asStateFlow()

    /** 当前选中的绑定设备 SN（用于监听变化并清理运行时列表） */
    private val _selectedLinkSerial = MutableStateFlow<String?>(null)
    override val selectedLinkSerial: StateFlow<String?> = _selectedLinkSerial.asStateFlow()

    /**
     * 执行用户登录
     * 
     * 登录流程：
     * 1. 调用认证仓库进行登录
     * 2. 登录成功后，清理旧订阅（如果切换用户）
     * 3. 保存用户 ID 和设备列表（HTTP 返回）
     * 4. 不立即订阅设备，等待 FloatingWindowViewModel 根据用户选择订阅
     * 
     * @param account 用户账号
     * @param password 用户密码
     * @param rememberMe 是否记住登录状态
     * @param callback 登录结果回调，参数：(是否成功, 错误信息)
     */
    override fun login(account: String, password: String, rememberMe: Boolean, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            setLoginLoading()
            try {
                val response = authRepository.login(account, password)
                if (response.code == 200) {
                    handleLoginSuccess(account = account, response = response, callback = callback)
                } else {
                    handleLoginFailure(message = response.message, callback = callback)
                }
            } catch (e: Exception) {
                handleLoginException(e, callback)
            }
        }
    }

    private fun setLoginLoading() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
    }

    private suspend fun handleLoginSuccess(
        account: String,
        response: ApiResponse<LoginResponse>,
        callback: (Boolean, String?) -> Unit
    ) {
        val loginData = response.data
        val userId = loginData?.id
        Log.d(TAG, "登录成功,$userId")

        saveAuthToken(loginData)
        resetRuntimeForNewLogin()
        connectMqttForAccount(account)

        val boundDevices = parseBoundDevices(loginData)
        userData.boundAccountDevices = boundDevices
        Log.d(TAG, "登录成功，解析到 ${boundDevices.size} 个后台设备")

        updateLoginSuccessState(
            userId = userId,
            boundDevices = boundDevices
        )
        callback(true, null)
    }

    private fun saveAuthToken(loginData: LoginResponse?) {
        loginData?.token?.let { token ->
            DataReportManager.getInstance().authToken = token
            Log.d(TAG, "已写入 DataReportManager token（用于绑定设备等需鉴权接口）")
        } ?: Log.w(TAG, "登录响应缺少 token，绑定设备将失败")
    }

    private suspend fun resetRuntimeForNewLogin() {
        val oldSubscribedDevices = mqttSubscriptionManager.getSubscribedDevices()
        if (oldSubscribedDevices.isNotEmpty()) {
            Log.d(TAG, "清理旧订阅，设备列表: $oldSubscribedDevices")
            mqttSubscriptionManager.clearAllSubscriptions()
        }

        userData.selectedLinkSerial = null
        _selectedLinkSerial.value = null
    }

    private fun connectMqttForAccount(account: String) {
        _account.value = account
        val platformClientId = currentMqttClientId(account)
        val radioDetectionClientId = currentRadioDetectionMqttClientId(account)
        userData.updateMqttConfig(
            username = account,
            clientId = platformClientId
        )
        ProductMqttRouter.resetForAccount(
            account = account,
            platformClientId = platformClientId,
            radioDetectionClientId = radioDetectionClientId
        )
        ProductMqttRouter.platformManager().connect()
    }

    private fun currentMqttClientId(account: String): String =
        "TJI_APP_${account}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    private fun currentRadioDetectionMqttClientId(account: String): String =
        "FC100_APP_${account}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

    private fun parseBoundDevices(loginData: LoginResponse?): List<BoundAccountDevice> {
        if (loginData == null) {
            return emptyList()
        }

        val serverBoundDevices = buildList {
            addAll(loginData.boundDevices.orEmpty().mapNotNull { it.toBoundAccountDevice() })
            addAll(loginData.toBoundAccountDevicesFromTypedRows())
        }

        return serverBoundDevices.distinctBy {
            "${it.productType.name}:${it.serialNumber}"
        }
    }

    private fun updateLoginSuccessState(
        userId: String?,
        boundDevices: List<BoundAccountDevice>
    ) {
        if (boundDevices.isEmpty()) {
            Log.w(TAG, "登录成功但无可用设备")
        } else {
            Log.d(TAG, "登录成功，共 ${boundDevices.size} 台设备，进入首页后按产品线选择")
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoggedIn = true,
            userId = userId,
            errorMessage = if (boundDevices.isEmpty()) "未找到可用设备" else null
        )
    }

    private fun handleLoginFailure(
        message: String?,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.e(TAG, "登录失败: $message")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = message
        )
        callback(false, message)
    }

    private fun handleLoginException(
        exception: Exception,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.e(TAG, "登录或认证异常: ${exception.message}")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = exception.message
        )
        callback(false, exception.message)
    }
    /**
     * 执行用户登出
     * 
     * 登出流程：
     * 1. 清理所有 MQTT 订阅（避免订阅残留）
     * 2. 调用认证仓库执行登出
     * 3. 清空登录状态和账号信息
     * 4. 清空用户设备列表
     */
    override fun logout() {
        viewModelScope.launch {
            // 登出前先取消所有订阅
            Log.d(TAG, "用户登出，清理订阅")
            mqttSubscriptionManager.clearAllSubscriptions()

            DataReportManager.getInstance().clearAuthToken()
            
            authRepository.logout()
            _uiState.value = LoginUiState()
            _account.value = ""
            userData.boundAccountDevices = null
            userData.selectedLinkSerial = null
            _selectedLinkSerial.value = null
            _needAccountDeviceSelection.value = null
            Log.d(TAG, "用户登出完成")
        }
    }

    override fun selectBoundAccountDevice(device: BoundAccountDevice) {
        viewModelScope.launch {
            Log.d(TAG, "用户选择绑定设备: ${device.serialNumber} - ${device.name}")
            userData.selectedLinkSerial = device.serialNumber
            _selectedLinkSerial.value = device.serialNumber

            _needAccountDeviceSelection.value = null
        }
    }

    /**
     * 清除错误消息
     * 
     * 用于清除 UI 上显示的错误提示信息。
     */
    override fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun BoundDeviceRow.toBoundAccountDevice(): BoundAccountDevice? {
        val serial = serialNumber?.takeIf { it.isNotBlank() }
            ?: sn1?.takeIf { it.isNotBlank() }
            ?: sn?.takeIf { it.isNotBlank() }
            ?: return null
        val displayName = productName?.takeIf { it.isNotBlank() }
            ?: deviceName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: serial
        return BoundAccountDevice(
            serialNumber = serial,
            name = displayName,
            productType = ProductCatalog.fromBackendFields(
                productId = productId,
                productType = productType,
                productCode = productCode,
                fallbackName = displayName
            ),
            serverId = id
        )
    }

    private fun LoginResponse.toBoundAccountDevicesFromTypedRows(): List<BoundAccountDevice> {
        val legacyDevices = when {
            boundDeviceRows.orEmpty().isNotEmpty() -> {
                BoundAccountDevice.parseFromLoginDeviceRows(boundDeviceRows.orEmpty())
            }

            else -> {
                BoundAccountDevice.parseFromLoginDeviceRows(
                    rows = bucketsns.orEmpty(),
                    forcedProductType = ProductType.FireBucket
                )
            }
        }

        val solarCleanDevices = cleanDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }
        val radioDetectionDevices = radioDetectionDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }

        return legacyDevices + solarCleanDevices + radioDetectionDevices
    }
}
