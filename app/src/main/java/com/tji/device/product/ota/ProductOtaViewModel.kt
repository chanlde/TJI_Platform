package com.tji.device.product.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.concurrent.LatestRequestTracker
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.session.DeviceKey
import com.tji.device.product.common.DeviceCommandId
import com.tji.device.error.toUserVisibleMessage
import com.tji.network.config.NetworkEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProductOtaViewModel(
    private val repository: ProductOtaRepository,
    private val commandPublisher: ProductOtaCommandPublisher
) : ViewModel() {
    private val _otaCheckState = MutableStateFlow(ProductOtaCheckState())
    val otaCheckState: StateFlow<ProductOtaCheckState> = _otaCheckState.asStateFlow()

    private val _commandFeedback = MutableStateFlow(ProductOtaCommandFeedback())
    val commandFeedback: StateFlow<ProductOtaCommandFeedback> = _commandFeedback.asStateFlow()
    @Volatile
    private var activeDeviceKey: DeviceKey? = null
    private var checkJob: Job? = null
    private val checkRequests = LatestRequestTracker<DeviceKey>()
    private val otaStartGuard = ProductOtaStartGuard()

    fun resetForDevice(serialNumber: String, productType: ProductType) {
        activeDeviceKey = DeviceKey(productType, serialNumber)
        checkJob?.cancel()
        checkJob = null
        checkRequests.invalidateAll()
        _otaCheckState.value = ProductOtaCheckState()
        _commandFeedback.value = ProductOtaCommandFeedback()
    }

    fun onOtaStatusChanged(
        serialNumber: String,
        productType: ProductType,
        status: ProductOtaStatus?
    ) {
        otaStartGuard.releaseIfRetryAllowed(
            deviceKey = DeviceKey(productType, serialNumber),
            status = status
        )
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
                    productId = ProductCatalog.backendProductIdOf(productType)
                ).fold(
                    onSuccess = { latest ->
                        if (!isCurrentCheck(deviceKey, requestId)) return@fold
                        val hasUpdate = latest.hasUpdate ?: isServerVersionNewer(
                            currentInnerVersion = deviceInfo?.firmwareInnerVersion,
                            latestInnerVersion = latest.innerVersion,
                            currentVersion = deviceInfo?.firmwareVersion,
                            latestVersion = latest.latestVersion
                        )
                        _otaCheckState.value = ProductOtaCheckState(
                            latest = latest,
                            hasUpdate = hasUpdate,
                            errorMessage = if (latest.innerVersion != null && deviceInfo?.firmwareInnerVersion == null) {
                                "未获取到设备内部版本号"
                            } else {
                                null
                            }
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
        val targetVersion = latest.latestVersion ?: return
        val downloadUrl = resolveProductOtaDownloadUrl(latest.downloadUrl) ?: run {
            _otaCheckState.value = _otaCheckState.value.copy(errorMessage = "服务器未返回固件下载地址")
            return
        }
        val fileSize = latest.fileSize ?: run {
            _otaCheckState.value = _otaCheckState.value.copy(errorMessage = "服务器未返回固件文件大小")
            return
        }
        val sha256 = latest.sha256?.takeIf { it.isNotBlank() } ?: run {
            _otaCheckState.value = _otaCheckState.value.copy(errorMessage = "服务器未返回固件 SHA256")
            return
        }
        val msgId = newMsgId("ota")
        when (val reservation = otaStartGuard.reserve(deviceKey, msgId)) {
            OtaStartReservation.Accepted -> Unit
            is OtaStartReservation.AlreadyReserved -> {
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
                packageInfo = ProductOtaPackage(
                    targetVersion = targetVersion,
                    downloadUrl = downloadUrl,
                    fileSize = fileSize,
                    sha256 = sha256,
                    targetInnerVersion = latest.innerVersion,
                    hardwareVersion = latest.hardwareVersion ?: deviceInfo?.hardwareVersion,
                    signature = latest.signature
                ),
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
            }
        }
    }

    private fun newMsgId(action: String): String = DeviceCommandId.next("ota", action)

    override fun onCleared() {
        checkJob?.cancel()
        checkRequests.invalidateAll()
        otaStartGuard.reset()
        super.onCleared()
    }
}

internal fun resolveProductOtaDownloadUrl(
    rawUrl: String?,
    baseUrl: String = NetworkEndpoints.otaBaseUrl
): String? {
    val value = rawUrl?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> baseUrl.trimEnd('/') + value
        else -> baseUrl.trimEnd('/') + "/" + value.trimStart('/')
    }
}

class ProductOtaViewModelFactory(
    private val repository: ProductOtaRepository,
    private val commandPublisher: ProductOtaCommandPublisher
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductOtaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProductOtaViewModel(
                repository = repository,
                commandPublisher = commandPublisher
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
