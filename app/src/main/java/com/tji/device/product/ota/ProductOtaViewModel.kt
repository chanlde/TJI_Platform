package com.tji.device.product.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.util.toUserVisibleMessage
import com.tji.network.utils.NetWorkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductOtaViewModel(
    private val repository: ProductOtaRepository,
    private val commandPublisher: ProductOtaCommandPublisher
) : ViewModel() {
    private val _otaCheckState = MutableStateFlow(ProductOtaCheckState())
    val otaCheckState: StateFlow<ProductOtaCheckState> = _otaCheckState.asStateFlow()

    private val _commandFeedback = MutableStateFlow(ProductOtaCommandFeedback())
    val commandFeedback: StateFlow<ProductOtaCommandFeedback> = _commandFeedback.asStateFlow()

    fun resetForDevice() {
        _otaCheckState.value = ProductOtaCheckState()
        _commandFeedback.value = ProductOtaCommandFeedback()
    }

    fun requestDeviceInfo(serialNumber: String, productType: ProductType) {
        val msgId = newMsgId("device-info")
        _commandFeedback.value = ProductOtaCommandFeedback(
            msgId = msgId,
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = "刷新指令发送中"
        )
        commandPublisher.requestDeviceInfo(
            serialNumber = serialNumber,
            productType = productType,
            msgId = msgId,
            onSuccess = {
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Success,
                    text = "刷新指令已发送"
                )
            },
            onError = { throwable ->
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Failed,
                    text = throwable.toUserVisibleMessage("刷新指令发送失败")
                )
            }
        )
    }

    fun checkOta(productType: ProductType, deviceInfo: ProductDeviceInfo?) {
        viewModelScope.launch {
            _otaCheckState.value = ProductOtaCheckState(isChecking = true)
            repository.getLatestFirmware(
                productId = ProductCatalog.backendProductIdOf(productType)
            ).fold(
                onSuccess = { latest ->
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
                    _otaCheckState.value = ProductOtaCheckState(
                        errorMessage = throwable.toUserVisibleMessage("OTA 版本查询失败")
                    )
                }
            )
        }
    }

    fun startOta(
        serialNumber: String,
        productType: ProductType,
        deviceInfo: ProductDeviceInfo?
    ) {
        val latest = _otaCheckState.value.latest ?: return
        val targetVersion = latest.latestVersion ?: return
        val downloadUrl = latest.downloadUrl?.toAbsoluteDownloadUrl() ?: return
        val fileSize = latest.fileSize ?: run {
            _otaCheckState.value = _otaCheckState.value.copy(errorMessage = "服务器未返回固件文件大小")
            return
        }
        val sha256 = latest.sha256?.takeIf { it.isNotBlank() } ?: run {
            _otaCheckState.value = _otaCheckState.value.copy(errorMessage = "服务器未返回固件 SHA256")
            return
        }
        val msgId = newMsgId("ota")
        _commandFeedback.value = ProductOtaCommandFeedback(
            msgId = msgId,
            status = ProductOtaCommandFeedbackStatus.Pending,
            text = "升级指令发送中"
        )
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
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Success,
                    text = "升级指令已发送"
                )
            },
            onError = { throwable ->
                _commandFeedback.value = ProductOtaCommandFeedback(
                    msgId = msgId,
                    status = ProductOtaCommandFeedbackStatus.Failed,
                    text = throwable.toUserVisibleMessage("升级指令发送失败")
                )
            }
        )
    }

    private fun newMsgId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

    private fun String.toAbsoluteDownloadUrl(): String {
        val value = trim()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> NetWorkUtils.OTA_URL.trimEnd('/') + value
            else -> NetWorkUtils.OTA_URL + value
        }
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
