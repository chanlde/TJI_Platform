package com.tji.device.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tji.device.concurrent.LatestRequestTracker
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.LoginUiState
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.session.AppSessionStore
import com.tji.device.service.MqttSubscriptionManager
import com.tji.device.service.mqtt.ProductMqttRouter
import com.tji.device.error.toUserVisibleMessage
import com.tji.device.error.toUserVisibleServerMessage
import com.tji.network.data.ApiResponse
import com.tji.network.data.BoundDeviceRow
import com.tji.network.data.LoginResponse
import com.tji.network.MqttManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionStore: AppSessionStore = AppSessionStore(),
    private val initializedMqttSubscriptionManager: () -> MqttSubscriptionManager? = { null },
    private val accountMqttConnector: (String, String, String) -> Unit = { account, platformClientId, radioDetectionClientId ->
        ProductMqttRouter.resetForAccount(
            account = account,
            platformClientId = platformClientId,
            radioDetectionClientId = radioDetectionClientId
        )
        Log.w(
            "LoginViewModel",
            "TJI_MQTT_DIAG login mqtt reset account=$account " +
                "platformClientId=$platformClientId radioDetectionClientId=$radioDetectionClientId"
        )
        ProductMqttRouter.platformManager().connect(
            onConnected = {
                Log.w("LoginViewModel", "TJI_MQTT_DIAG platform mqtt connected after login")
            },
            onFailed = { throwable ->
                Log.e("LoginViewModel", "TJI_MQTT_DIAG platform mqtt connect failed after login", throwable)
            }
        )
    }
) : ViewModel() {

    companion object {
         const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _account = MutableStateFlow("")  // 初始值为空字符串
    val account: StateFlow<String> = _account.asStateFlow() // 公开的只读 StateFlow
    private val loginAttempts = LatestRequestTracker<Unit>()
    private val accountOperations = LatestRequestTracker<Unit>()
    private var loginJob: Job? = null
    private var logoutJob: Job? = null

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
    fun login(account: String, password: String, rememberMe: Boolean, callback: (Boolean, String?) -> Unit) {
        val loginAttempt = loginAttempts.begin(Unit)
        loginJob?.cancel()
        loginJob = null
        validateLoginInput(account, password)?.let { validationError ->
            handleLoginFailure(message = validationError, callback = callback)
            return
        }

        val accountOperation = accountOperations.begin(Unit)
        logoutJob?.cancel()
        logoutJob = null
        loginJob = viewModelScope.launch {
            setLoginLoading()
            try {
                val response = authRepository.login(account, password)
                if (!isCurrentLogin(loginAttempt, accountOperation)) return@launch
                if (response.code == 200) {
                    handleLoginSuccess(
                        account = account,
                        response = response,
                        loginAttempt = loginAttempt,
                        accountOperation = accountOperation,
                        callback = callback
                    )
                } else {
                    handleLoginFailure(message = response.message.toUserVisibleServerMessage("登录失败，请检查账号或密码"), callback = callback)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                if (isCurrentLogin(loginAttempt, accountOperation)) {
                    handleLoginException(e, callback)
                }
            }
        }
    }

    private fun isCurrentLogin(loginAttempt: Long, accountOperation: Long): Boolean =
        loginAttempts.isLatest(Unit, loginAttempt) &&
            accountOperations.isLatest(Unit, accountOperation)

    private fun setLoginLoading() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
    }

    private fun validateLoginInput(account: String, password: String): String? {
        return when {
            account.isBlank() -> "请输入账号"
            password.isBlank() -> "请输入密码"
            else -> null
        }
    }

    private suspend fun handleLoginSuccess(
        account: String,
        response: ApiResponse<LoginResponse>,
        loginAttempt: Long,
        accountOperation: Long,
        callback: (Boolean, String?) -> Unit
    ) {
        val loginData = response.data
        val userId = loginData?.id
        Log.d(TAG, "登录成功,$userId")

        sessionStore.beginSessionTransition()
        // 先发布账号变化，让 MainViewModel 在清理 MQTT 的挂起点取消旧账号导航/改名任务。
        _account.value = account
        resetRuntimeForNewLogin()
        if (!isCurrentLogin(loginAttempt, accountOperation)) return

        val boundDevices = parseBoundDevices(loginData)
        sessionStore.startSession(userId = userId, devices = boundDevices)
        startMqttForAccount(account)
        Log.d(TAG, "登录成功，解析到 ${boundDevices.size} 个后台设备")

        updateLoginSuccessState(
            userId = userId,
            boundDevices = boundDevices
        )
        callback(true, null)
    }

    private suspend fun resetRuntimeForNewLogin() {
        val mqttSubscriptionManager = initializedMqttSubscriptionManager()
        if (mqttSubscriptionManager != null) {
            val oldSubscribedDevices = mqttSubscriptionManager.getSubscribedDevices()
            Log.d(TAG, "清理旧 MQTT 会话，设备列表: $oldSubscribedDevices")
            clearSubscriptionsBestEffort(mqttSubscriptionManager, "切换账号")
        }

        sessionStore.clearSelection()
    }

    private fun connectMqttForAccount(account: String) {
        val platformClientId = currentMqttClientId(account)
        val radioDetectionClientId = currentRadioDetectionMqttClientId(account)
        Log.w(
            TAG,
            "TJI_MQTT_DIAG start account mqtt account=$account " +
                "platformClientId=$platformClientId radioDetectionClientId=$radioDetectionClientId"
        )
        accountMqttConnector(account, platformClientId, radioDetectionClientId)
    }

    private fun startMqttForAccount(account: String) {
        runCatching {
            connectMqttForAccount(account)
        }.onFailure { throwable ->
            Log.e(TAG, "MQTT startup after login failed", throwable)
        }
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
            errorMessage = exception.toUserVisibleMessage("登录失败，请稍后重试")
        )
        callback(false, exception.toUserVisibleMessage("登录失败，请稍后重试"))
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
    fun logout() {
        loginAttempts.begin(Unit)
        loginJob?.cancel()
        loginJob = null
        sessionStore.beginSessionTransition()
        val accountOperation = accountOperations.begin(Unit)
        logoutJob?.cancel()
        logoutJob = viewModelScope.launch {
            Log.d(TAG, "用户登出，清理订阅")
            initializedMqttSubscriptionManager()?.let { manager ->
                clearSubscriptionsBestEffort(manager, "登出")
            }
            try {
                authRepository.logout()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "登出接口清理失败，继续清理本地会话", throwable)
            } finally {
                // A newer successful login owns the process now. An old logout
                // must never disconnect or erase that new account's session.
                if (accountOperations.isLatest(Unit, accountOperation)) {
                    MqttManager.disconnectAll()
                    _uiState.value = LoginUiState()
                    _account.value = ""
                    sessionStore.clear()
                    Log.d(TAG, "用户登出完成")
                }
            }
        }
    }

    private suspend fun clearSubscriptionsBestEffort(
        mqttSubscriptionManager: MqttSubscriptionManager,
        reason: String
    ) {
        try {
            mqttSubscriptionManager.clearAllSubscriptions()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "$reason 时部分 MQTT 订阅未能确认取消，继续清理会话", throwable)
        }
    }

    /**
     * 清除错误消息
     * 
     * 用于清除 UI 上显示的错误提示信息。
     */
    fun clearErrorMessage() {
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
        val dropperSixStageDevices = sixStageDropperDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }
        val speakerDevices = speakerDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }
        val breakWindowDevices = breakWindowDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }
        val searchlightDevices = searchlightDevicesResolved()
            .mapNotNull { it.toBoundAccountDevice() }

        return legacyDevices +
            solarCleanDevices +
            radioDetectionDevices +
            dropperSixStageDevices +
            speakerDevices +
            breakWindowDevices +
            searchlightDevices
    }
}
