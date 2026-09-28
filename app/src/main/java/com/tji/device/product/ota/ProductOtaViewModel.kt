package com.tji.device.product.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.concurrent.LatestRequestTracker
import com.tji.device.diagnostics.AppDiagnostics
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.session.DeviceKey
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.error.toUserVisibleMessage
import com.tji.network.config.NetworkEndpoints
import com.tji.network.data.OtaTaskRequest
import com.tji.network.data.OtaTaskResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProductOtaViewModel(
    private val repository: ProductOtaRepository,
    private val commandPublisher: ProductOtaCommandPublisher,
    private val otaBaseUrl: String = NetworkEndpoints.otaBaseUrl,
    private val taskStore: ProductOtaTaskStore = InMemoryProductOtaTaskStore()
) : ViewModel() {
    private val _otaCheckState = MutableStateFlow(ProductOtaCheckState())
    val otaCheckState: StateFlow<ProductOtaCheckState> = _otaCheckState.asStateFlow()

    private val _commandFeedback = MutableStateFlow(ProductOtaCommandFeedback())
    val commandFeedback: StateFlow<ProductOtaCommandFeedback> = _commandFeedback.asStateFlow()
    private val _isOtaStartReserved = MutableStateFlow(false)
    val isOtaStartReserved: StateFlow<Boolean> = _isOtaStartReserved.asStateFlow()
    private val _serverTask = MutableStateFlow<OtaTaskResponse?>(null)
    val serverTask: StateFlow<OtaTaskResponse?> = _serverTask.asStateFlow()
    private val serverTasks = mutableMapOf<DeviceKey, OtaTaskResponse>()
    @Volatile
    private var activeDeviceKey: DeviceKey? = null
    private var checkJob: Job? = null
    private val checkRequests = LatestRequestTracker<DeviceKey>()
    private val otaStartGuard = ProductOtaStartGuard()

    fun resetForDevice(
        serialNumber: String,
        productType: ProductType
    ) {
        val deviceKey = DeviceKey(productType, serialNumber)
        activeDeviceKey = deviceKey
        checkJob?.cancel()
        checkJob = null
        checkRequests.invalidateAll()
        _otaCheckState.value = ProductOtaCheckState()
        _commandFeedback.value = ProductOtaCommandFeedback()
        taskStore.get(deviceKey)?.let { saved ->
            otaStartGuard.adoptServerTask(deviceKey, saved.cmdId)
            _commandFeedback.value = ProductOtaCommandFeedback(
                msgId = saved.cmdId,
                status = ProductOtaCommandFeedbackStatus.Pending,
                text = "正在核对上次升级任务"
            )
        }
        _isOtaStartReserved.value = otaStartGuard.isReserved(deviceKey)
        _serverTask.value = serverTasks[deviceKey]
    }

    fun unbindDevice(serialNumber: String, productType: ProductType) {
        if (activeDeviceKey != DeviceKey(productType, serialNumber)) return
        activeDeviceKey = null
        checkJob?.cancel()
        checkJob = null
        checkRequests.invalidateAll()
        _otaCheckState.value = ProductOtaCheckState()
        _commandFeedback.value = ProductOtaCommandFeedback()
        _isOtaStartReserved.value = false
        _serverTask.value = null
    }

    fun onOtaStatusChanged(
        serialNumber: String,
        productType: ProductType,
        status: ProductOtaStatus?
    ) {
        val deviceKey = DeviceKey(productType, serialNumber)
        val task = serverTasks[deviceKey]
        if (task != null) {
            if (!status?.cmdId.isNullOrBlank() && status?.cmdId != task.cmdId) return
            viewModelScope.launch {
                repository.getTask(task.id).onSuccess { current ->
                    updateServerTask(deviceKey, current)
                }
            }
            return
        }
        otaStartGuard.releaseIfTerminal(
            deviceKey = deviceKey,
            status = status
        )
        if (activeDeviceKey == deviceKey) {
            _isOtaStartReserved.value = otaStartGuard.isReserved(deviceKey)
        }
    }

    fun restoreActiveTask(
        serialNumber: String,
        productType: ProductType,
        deviceInfo: ProductDeviceInfo?
    ) {
        val deviceKey = DeviceKey(productType, serialNumber)
        if (activeDeviceKey != deviceKey) return
        val saved = taskStore.get(deviceKey)
        if ((deviceInfo?.otaTaskProtocolVersion ?: 0) < 1 && saved == null) return
        viewModelScope.launch {
            repository.getActiveTask(serialNumber).fold(
                onSuccess = { active ->
                    if (active != null) {
                        updateServerTask(deviceKey, active)
                    } else {
                        val rememberedId = serverTasks[deviceKey]?.id ?: saved?.taskId
                        if (rememberedId != null) {
                            repository.getTask(rememberedId).fold(
                                onSuccess = { finished -> updateServerTask(deviceKey, finished) },
                                onFailure = { throwable -> showTaskRecoveryError(deviceKey, throwable) }
                            )
                        }
                    }
                },
                onFailure = { throwable -> showTaskRecoveryError(deviceKey, throwable) }
            )
        }
    }

    private fun showTaskRecoveryError(deviceKey: DeviceKey, throwable: Throwable) {
        if (activeDeviceKey != deviceKey) return
        _commandFeedback.value = ProductOtaCommandFeedback(
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = throwable.toUserVisibleMessage("升级任务待核对，请勿重复升级")
        )
    }

    private fun updateServerTask(deviceKey: DeviceKey, task: OtaTaskResponse) {
        if (task.status.normalizedOtaStatus() in setOf("SUCCESS", "FAILED", "ROLLED_BACK")) {
            taskStore.clear(deviceKey)
            serverTasks.remove(deviceKey)
            otaStartGuard.releaseIfTerminal(
                deviceKey,
                ProductOtaStatus(status = task.status, cmdId = task.cmdId)
            )
        } else {
            taskStore.save(deviceKey, SavedOtaTask(task.id, task.cmdId))
            serverTasks[deviceKey] = task
            otaStartGuard.adoptServerTask(deviceKey, task.cmdId)
        }
        if (activeDeviceKey == deviceKey) {
            _serverTask.value = task
            _isOtaStartReserved.value = otaStartGuard.isReserved(deviceKey)
        }
    }

    fun requestDeviceInfo(serialNumber: String, productType: ProductType) {
        val deviceKey = DeviceKey(productType, serialNumber)
        if (activeDeviceKey != deviceKey) resetForDevice(serialNumber, productType)
        val msgId = newMsgId("device-info")
        _commandFeedback.value = ProductOtaCommandFeedback(
            msgId = msgId,
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = "刷新指令发送中"
        )
        try {
            commandPublisher.requestDeviceInfo(
                serialNumber = serialNumber,
                productType = productType,
                msgId = msgId,
                onSuccess = {
                    if (activeDeviceKey != deviceKey || _commandFeedback.value.msgId != msgId) {
                        return@requestDeviceInfo
                    }
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = msgId,
                        status = ProductOtaCommandFeedbackStatus.Success,
                        text = "刷新指令已发送"
                    )
                },
                onError = { throwable ->
                    if (activeDeviceKey != deviceKey || _commandFeedback.value.msgId != msgId) {
                        return@requestDeviceInfo
                    }
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = msgId,
                        status = ProductOtaCommandFeedbackStatus.Failed,
                        text = throwable.toUserVisibleMessage("刷新指令发送失败")
                    )
                }
            )
        } catch (throwable: Exception) {
            if (activeDeviceKey == deviceKey && _commandFeedback.value.msgId == msgId) {
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Failed,
                    text = throwable.toUserVisibleMessage("刷新指令发送失败")
                )
            }
        }
    }

    fun checkOta(
        serialNumber: String,
        productType: ProductType,
        deviceInfo: ProductDeviceInfo?
    ) {
        val deviceKey = DeviceKey(productType, serialNumber)
        if (activeDeviceKey != deviceKey) resetForDevice(serialNumber, productType)
        checkJob?.cancel()
        val requestId = checkRequests.begin(deviceKey)
        checkJob = viewModelScope.launch {
            _otaCheckState.value = ProductOtaCheckState(isChecking = true)
            try {
                repository.getLatestFirmware(
                    productId = ProductCatalog.backendProductIdOf(productType),
                    hardwareVersion = deviceInfo?.hardwareVersion?.trim()?.takeIf { it.isNotEmpty() }
                ).fold(
                    onSuccess = { latest ->
                        if (!isCurrentCheck(deviceKey, requestId)) return@fold
                        val versionIsNewer = isServerVersionNewer(
                            currentInnerVersion = deviceInfo?.firmwareInnerVersion,
                            latestInnerVersion = latest.innerVersion,
                            currentVersion = deviceInfo?.firmwareVersion,
                            latestVersion = latest.latestVersion
                        )
                        val hasUpdate = latest.hasUpdate != false && versionIsNewer
                        _otaCheckState.value = ProductOtaCheckState(
                            latest = latest,
                            hasUpdate = hasUpdate,
                            errorMessage = if (latest.innerVersion != null && deviceInfo?.firmwareInnerVersion == null) {
                                "未获取到设备内部版本号"
                            } else {
                                null
                            }
                        )
                        AppDiagnostics.record(
                            "ota_check",
                            mapOf(
                                "product" to productType.name,
                                "device" to AppDiagnostics.deviceRef(serialNumber),
                                "hasUpdate" to hasUpdate
                            )
                        )
                    },
                    onFailure = { throwable ->
                        if (!isCurrentCheck(deviceKey, requestId)) return@fold
                        _otaCheckState.value = ProductOtaCheckState(
                            errorMessage = throwable.toUserVisibleMessage("OTA 版本查询失败")
                        )
                    }
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isCurrentCheck(deviceKey, requestId)) {
                    _otaCheckState.value = ProductOtaCheckState(
                        errorMessage = throwable.toUserVisibleMessage("OTA 版本查询失败")
                    )
                }
            } finally {
                checkRequests.complete(deviceKey, requestId)
            }
        }
    }

    private fun isCurrentCheck(deviceKey: DeviceKey, requestId: Long): Boolean =
        activeDeviceKey == deviceKey && checkRequests.isLatest(deviceKey, requestId)

    @Suppress("LongMethod") // Keep validation and one-command reservation in the same transition.
    fun startOta(
        serialNumber: String,
        productType: ProductType,
        deviceInfo: ProductDeviceInfo?
    ) {
        val deviceKey = DeviceKey(productType, serialNumber)
        if (activeDeviceKey != deviceKey) return
        val checkState = _otaCheckState.value
        if (!checkState.hasUpdate) {
            _otaCheckState.value = checkState.copy(errorMessage = "当前设备没有可安装的新版本")
            return
        }
        val latest = checkState.latest ?: return
        val packageInfo = when (
            val validation = validateProductOtaPackage(
                productType = productType,
                deviceInfo = deviceInfo,
                latest = latest,
                otaBaseUrl = otaBaseUrl,
                requireSignature = false
            )
        ) {
            is ProductOtaPackageValidation.Valid -> validation.packageInfo
            is ProductOtaPackageValidation.Invalid -> {
                _otaCheckState.value = _otaCheckState.value.copy(errorMessage = validation.message)
                return
            }
        }
        val backendFirmwareId = latest.backendFirmwareId?.trim()?.takeIf { it.isNotEmpty() }
        if ((deviceInfo?.otaTaskProtocolVersion ?: 0) >= 1) {
            if (backendFirmwareId == null) {
                _otaCheckState.value = _otaCheckState.value.copy(
                    errorMessage = "设备支持任务式升级，但服务器未返回固件任务 ID"
                )
                return
            }
            startTaskBackedOta(deviceKey, packageInfo, backendFirmwareId)
            return
        }
        val msgId = newMsgId("ota")
        when (val reservation = otaStartGuard.reserve(deviceKey, msgId)) {
            OtaStartReservation.Accepted -> _isOtaStartReserved.value = true
            is OtaStartReservation.AlreadyReserved -> {
                _isOtaStartReserved.value = true
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = reservation.activeMsgId,
                    status = ProductOtaCommandFeedbackStatus.Success,
                    text = "升级指令已发送，等待设备响应"
                )
                return
            }
        }
        _commandFeedback.value = ProductOtaCommandFeedback(
            msgId = msgId,
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = "升级指令发送中"
        )
        try {
            commandPublisher.startOta(
                serialNumber = serialNumber,
                productType = productType,
                msgId = msgId,
                packageInfo = packageInfo,
                onSuccess = {
                    if (activeDeviceKey != deviceKey ||
                        !otaStartGuard.isActive(deviceKey, msgId) ||
                        _commandFeedback.value.msgId != msgId
                    ) {
                        return@startOta
                    }
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = msgId,
                        status = ProductOtaCommandFeedbackStatus.Success,
                        text = "升级指令已发送，等待设备响应"
                    )
                    AppDiagnostics.record(
                        "ota_start_published",
                        mapOf(
                            "product" to productType.name,
                            "device" to AppDiagnostics.deviceRef(serialNumber)
                        )
                    )
                },
                onError = { throwable ->
                    if (!otaStartGuard.releaseAfterPublishFailure(deviceKey, msgId) ||
                        activeDeviceKey != deviceKey ||
                        _commandFeedback.value.msgId != msgId
                    ) {
                        return@startOta
                    }
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = msgId,
                        status = ProductOtaCommandFeedbackStatus.Failed,
                        text = throwable.toUserVisibleMessage("升级指令发送失败")
                    )
                    _isOtaStartReserved.value = false
                }
            )
        } catch (throwable: Exception) {
            if (otaStartGuard.releaseAfterPublishFailure(deviceKey, msgId) &&
                activeDeviceKey == deviceKey &&
                _commandFeedback.value.msgId == msgId
            ) {
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Failed,
                    text = throwable.toUserVisibleMessage("升级指令发送失败")
                )
                _isOtaStartReserved.value = false
            }
        }
    }

    @Suppress("LongMethod") // Reservation, local persistence and publication share one task identity.
    private fun startTaskBackedOta(
        deviceKey: DeviceKey,
        packageInfo: ProductOtaPackage,
        firmwarePackageId: String
    ) {
        val clientRequestId = newMsgId("ota-request")
        when (val reservation = otaStartGuard.reserve(deviceKey, clientRequestId)) {
            OtaStartReservation.Accepted -> _isOtaStartReserved.value = true
            is OtaStartReservation.AlreadyReserved -> {
                _isOtaStartReserved.value = true
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = reservation.activeMsgId,
                    status = ProductOtaCommandFeedbackStatus.Pending,
                    text = "已有升级任务，正在等待设备确认"
                )
                return
            }
        }
        _commandFeedback.value = ProductOtaCommandFeedback(
            msgId = clientRequestId,
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = "正在核对设备升级任务"
        )
        viewModelScope.launch {
            try {
                val activeResult = repository.getActiveTask(deviceKey.serialNumber)
                if (activeResult.isFailure) {
                    throw activeResult.exceptionOrNull() ?: IllegalStateException("任务查询失败")
                }
                val active = activeResult.getOrNull()
                if (active != null) {
                    updateServerTask(deviceKey, active)
                    if (activeDeviceKey == deviceKey) {
                        _commandFeedback.value = ProductOtaCommandFeedback(
                            msgId = active.cmdId,
                            status = ProductOtaCommandFeedbackStatus.Pending,
                            text = "已有升级任务，正在核对设备结果"
                        )
                    }
                    return@launch
                }

                val reserveResult = repository.reserveTask(
                    OtaTaskRequest(
                        deviceSn = deviceKey.serialNumber,
                        firmwarePackageId = firmwarePackageId,
                        clientRequestId = clientRequestId
                    )
                )
                if (reserveResult.isFailure) {
                    val concurrent = repository.getActiveTask(deviceKey.serialNumber).getOrNull()
                    if (concurrent != null) {
                        updateServerTask(deviceKey, concurrent)
                        if (activeDeviceKey == deviceKey) {
                            _commandFeedback.value = ProductOtaCommandFeedback(
                                msgId = concurrent.cmdId,
                                status = ProductOtaCommandFeedbackStatus.Pending,
                                text = "设备已有升级任务，正在核对结果"
                            )
                        }
                        return@launch
                    }
                    throw reserveResult.exceptionOrNull() ?: IllegalStateException("任务预约失败")
                }
                val task = reserveResult.getOrThrow()
                updateServerTask(deviceKey, task)
                if (!task.matchesPackage(deviceKey.serialNumber, firmwarePackageId, packageInfo, otaBaseUrl)) {
                    if (activeDeviceKey == deviceKey) {
                        _commandFeedback.value = ProductOtaCommandFeedback(
                            msgId = task.cmdId,
                            status = ProductOtaCommandFeedbackStatus.Failed,
                            text = "后台任务与固件候选不一致，请联系管理员核对"
                        )
                    }
                    return@launch
                }
                if (activeDeviceKey == deviceKey) {
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = task.cmdId,
                        status = ProductOtaCommandFeedbackStatus.Pending,
                        text = "升级指令发送中"
                    )
                }
                commandPublisher.startOta(
                    serialNumber = deviceKey.serialNumber,
                    productType = deviceKey.productType,
                    msgId = task.cmdId,
                    packageInfo = packageInfo.copy(taskId = task.id),
                    onSuccess = {
                        if (activeDeviceKey == deviceKey && otaStartGuard.isActive(deviceKey, task.cmdId)) {
                            _commandFeedback.value = ProductOtaCommandFeedback(
                                msgId = task.cmdId,
                                status = ProductOtaCommandFeedbackStatus.Success,
                                text = "指令已送达消息服务，等待设备确认"
                            )
                        }
                    },
                    onError = { throwable ->
                        if (activeDeviceKey == deviceKey) {
                            _commandFeedback.value = ProductOtaCommandFeedback(
                                msgId = task.cmdId,
                                status = ProductOtaCommandFeedbackStatus.Pending,
                                text = throwable.toUserVisibleMessage("发送结果待核对，请勿重复升级")
                            )
                        }
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (otaStartGuard.releaseAfterPublishFailure(deviceKey, clientRequestId) &&
                    activeDeviceKey == deviceKey
                ) {
                    _isOtaStartReserved.value = false
                    _commandFeedback.value = ProductOtaCommandFeedback(
                        msgId = clientRequestId,
                        status = ProductOtaCommandFeedbackStatus.Failed,
                        text = throwable.toUserVisibleMessage("升级任务核对失败")
                    )
                }
            }
        }
    }

    private fun OtaTaskResponse.matchesPackage(
        serialNumber: String,
        firmwarePackageId: String,
        packageInfo: ProductOtaPackage,
        baseUrl: String
    ): Boolean =
        deviceSn == serialNumber &&
            this.firmwarePackageId == firmwarePackageId &&
            targetVersion == packageInfo.targetVersion &&
            targetInnerVersion == packageInfo.targetInnerVersion &&
            hardwareVersion == packageInfo.hardwareVersion &&
            targetSha256.equals(packageInfo.sha256, ignoreCase = true) &&
            fileSize == packageInfo.fileSize &&
            resolveSecureProductOtaDownloadUrl(downloadUrl, baseUrl) == packageInfo.downloadUrl

    private fun newMsgId(action: String): String = DeviceCommandId.next("ota", action)

    override fun onCleared() {
        checkJob?.cancel()
        checkRequests.invalidateAll()
        otaStartGuard.reset()
        _isOtaStartReserved.value = false
        super.onCleared()
    }
}

internal fun resolveProductOtaDownloadUrl(
    rawUrl: String?,
    baseUrl: String = NetworkEndpoints.otaBaseUrl
): String? {
    return resolveSecureProductOtaDownloadUrl(rawUrl, baseUrl)
}

class ProductOtaViewModelFactory(
    private val repository: ProductOtaRepository,
    private val commandPublisher: ProductOtaCommandPublisher,
    private val taskStore: ProductOtaTaskStore = InMemoryProductOtaTaskStore()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductOtaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProductOtaViewModel(
                repository = repository,
                commandPublisher = commandPublisher,
                taskStore = taskStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun isServerVersionNewer(
    currentInnerVersion: Int?,
    latestInnerVersion: Int?,
    currentVersion: String?,
    latestVersion: String?
): Boolean {
    if (currentInnerVersion != null && latestInnerVersion != null) {
        return latestInnerVersion > currentInnerVersion
    }
    if (latestInnerVersion != null) return false
    if (currentVersion.isNullOrBlank() || latestVersion.isNullOrBlank()) return false
    val currentParts = currentVersion.versionParts()
    val latestParts = latestVersion.versionParts()
    val maxSize = maxOf(currentParts.size, latestParts.size)
    repeat(maxSize) { index ->
        val currentPart = currentParts.getOrNull(index) ?: 0
        val latestPart = latestParts.getOrNull(index) ?: 0
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun String.versionParts(): List<Int> =
    split('.', '-', '_')
        .mapNotNull { it.toIntOrNull() }
