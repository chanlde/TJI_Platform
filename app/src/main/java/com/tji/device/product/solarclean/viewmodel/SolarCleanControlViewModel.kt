package com.tji.device.product.solarclean.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.ProductType
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import com.tji.device.product.solarclean.model.SolarCleanOtaPackage
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanOtaRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import com.tji.network.data.OtaLatestResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SolarCleanControlViewModel(
    private val stateRepository: SolarCleanRepository,
    private val controlRepository: SolarCleanControlRepository,
    private val otaRepository: SolarCleanOtaRepository
) : ViewModel() {
    val devices: StateFlow<List<SolarCleanDeviceState>> = stateRepository.devices

    private val _otaCheckState = MutableStateFlow(SolarCleanOtaCheckState())
    val otaCheckState: StateFlow<SolarCleanOtaCheckState> = _otaCheckState.asStateFlow()

    fun ping(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.Ping(newMsgId("ping")))
    }

    fun setPump(serialNumber: String, on: Boolean) {
        send(serialNumber, SolarCleanCommand.PumpSwitch(newMsgId("pump"), on))
    }

    fun setPumpPressure(serialNumber: String, percent: Double) {
        send(serialNumber, SolarCleanCommand.PumpPressure(newMsgId("pressure"), percent))
    }

    fun setSprayAngle(serialNumber: String, angleDeg: Double) {
        send(serialNumber, SolarCleanCommand.SprayAngle(newMsgId("angle"), angleDeg))
    }

    fun setServoSwing(serialNumber: String, on: Boolean) {
        send(serialNumber, SolarCleanCommand.ServoSwing(newMsgId("swing"), on))
    }

    fun setSwingSpeed(serialNumber: String, speedPercent: Double) {
        send(serialNumber, SolarCleanCommand.SwingSpeed(newMsgId("swing-speed"), speedPercent))
    }

    fun requestDeviceInfo(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.GetDeviceInfo(newMsgId("device-info")))
    }

    fun checkOta(serialNumber: String, deviceInfo: SolarCleanDeviceInfo?) {
        val hardwareVersion = deviceInfo?.hardwareVersion
        Log.d(
            TAG,
            "检测更新点击: sn=$serialNumber, currentFirmware=${deviceInfo?.firmwareVersion}, hardwareVersion=$hardwareVersion"
        )

        viewModelScope.launch {
            Log.d(TAG, "开始请求 OTA 最新版本: sn=$serialNumber")
            _otaCheckState.value = SolarCleanOtaCheckState(isChecking = true)
            otaRepository.getLatestFirmware(
                productType = ProductType.SolarClean.name
            ).fold(
                onSuccess = { latest ->
                    _otaCheckState.value = SolarCleanOtaCheckState(
                        latest = latest,
                        hasUpdate = latest.hasUpdate ?: isServerVersionNewer(
                            current = deviceInfo?.firmwareVersion,
                            latest = latest.latestVersion
                        )
                    )
                },
                onFailure = { throwable ->
                    _otaCheckState.value = SolarCleanOtaCheckState(errorMessage = throwable.message)
                }
            )
        }
    }

    fun startOta(serialNumber: String, deviceInfo: SolarCleanDeviceInfo?) {
        val latest = _otaCheckState.value.latest ?: return
        val hardwareVersion = latest.hardwareVersion ?: deviceInfo?.hardwareVersion ?: return
        val targetVersion = latest.latestVersion ?: return
        val fileSize = latest.fileSize ?: return
        val sha256 = latest.sha256 ?: return
        val downloadUrl = latest.downloadUrl ?: return

        send(
            serialNumber,
            SolarCleanCommand.StartOta(
                msgId = newMsgId("ota"),
                packageInfo = SolarCleanOtaPackage(
                    targetVersion = targetVersion,
                    hardwareVersion = hardwareVersion,
                    fileSize = fileSize,
                    sha256 = sha256,
                    signature = latest.signature,
                    downloadUrl = downloadUrl
                )
            )
        )
    }

    fun requestRouteList(serialNumber: String) {
        send(serialNumber, SolarCleanCommand.RouteList(newMsgId("routes")))
    }

    fun executeSlot(serialNumber: String, slot: Int) {
        send(serialNumber, SolarCleanCommand.ExecuteSlot(newMsgId("execute"), slot))
    }

    fun deleteSlot(serialNumber: String, slot: Int) {
        send(serialNumber, SolarCleanCommand.RouteDelete(newMsgId("delete"), slot))
    }

    fun cancelDownload(serialNumber: String, slot: Int? = null) {
        send(serialNumber, SolarCleanCommand.RouteDownloadCancel(newMsgId("cancel"), slot))
    }

    private fun send(serialNumber: String, command: SolarCleanCommand) {
        viewModelScope.launch {
            controlRepository.sendCommand(serialNumber, command)
        }
    }

    private fun newMsgId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

    private companion object {
        const val TAG = "SolarCleanControlVM"
    }
}

class SolarCleanControlViewModelFactory(
    private val stateRepository: SolarCleanRepository,
    private val controlRepository: SolarCleanControlRepository,
    private val otaRepository: SolarCleanOtaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SolarCleanControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SolarCleanControlViewModel(
                stateRepository = stateRepository,
                controlRepository = controlRepository,
                otaRepository = otaRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class SolarCleanOtaCheckState(
    val isChecking: Boolean = false,
    val latest: OtaLatestResponse? = null,
    val hasUpdate: Boolean = false,
    val errorMessage: String? = null
)

private fun isServerVersionNewer(current: String?, latest: String?): Boolean {
    if (current.isNullOrBlank() || latest.isNullOrBlank()) return false
    val currentParts = current.versionParts()
    val latestParts = latest.versionParts()
    val maxSize = maxOf(currentParts.size, latestParts.size)
    repeat(maxSize) { index ->
        val currentPart = currentParts.getOrNull(index) ?: 0
        val latestPart = latestParts.getOrNull(index) ?: 0
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun String.versionParts(): List<Int> =
    trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { it.toIntOrNull() }
