package com.tji.device.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.data.model.TestDeviceFallbacks
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.util.ToastUtils
import com.tji.device.util.userData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onBack: (() -> Unit)? = null,
    isFloatingWindowEnabled: Boolean = true,
    hasFloatingWindowPermission: Boolean = true,
    onFloatingWindowEnabledChange: (Boolean) -> Unit = {},
    onOpenFloatingWindowPermission: () -> Unit = {},
) {
    val mainViewModel = LocalMainViewModel.current
    val runtimeDevices by mainViewModel.runtimeDevices.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val account by mainViewModel.loginViewModel.account.collectAsStateWithLifecycle()
    var activeProductPage by remember { mutableStateOf<ProductType?>(null) }
    var selectedLinkSerial by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeviceSettings by remember { mutableStateOf(false) }
    var boundDeviceVersion by remember { mutableStateOf(0) }
    userData.preferredProductTypeFlow.collectAsStateWithLifecycle()
    val boundAccountDevices = remember(boundDeviceVersion, userData.boundAccountDevices) {
        userData.boundAccountDevices.orEmpty()
    }
    val selectedBoundDevice = remember(selectedLinkSerial, boundAccountDevices) {
        val sn = selectedLinkSerial ?: return@remember null
        boundAccountDevices.firstOrNull { it.serialNumber == sn }
            ?: TestDeviceFallbacks.dropperSixStage.takeIf {
                activeProductPage == ProductType.DropperSixStage && it.serialNumber == sn
            }
            ?: TestDeviceFallbacks.speaker.takeIf {
                activeProductPage == ProductType.Speaker && it.serialNumber == sn
            }
            ?: radioDetectionDemoDevice().takeIf {
                activeProductPage == ProductType.RadioDetection && it.serialNumber == sn
            }
    }
    val selectedRuntimeDevice = remember(runtimeDevices, selectedBoundDevice) {
        val info = selectedBoundDevice ?: return@remember null
        runtimeDevices.firstOrNull {
            it.serialNumber == info.serialNumber && it.productType == info.productType
        } ?: if (info.productType == ProductType.RadioDetection && info.serialNumber == radioDetectionDemoDevice().serialNumber) {
            ProductDeviceRuntimeSnapshot(
                serialNumber = info.serialNumber,
                name = info.name,
                productType = info.productType,
                isOnline = true,
                childCount = null
            )
        } else {
            null
        }
    }
    val selectedFireBucketLink = remember(selectedRuntimeDevice, selectedBoundDevice) {
        val info = selectedBoundDevice ?: return@remember null
        (selectedRuntimeDevice?.payload as? FireBucketLinkDevice)
            ?: if (info.productType == ProductType.FireBucket) {
                fireBucketLinkPlaceholderFromBoundAccount(info)
            } else {
                null
            }
    }

    Scaffold(
        containerColor = PlatformHomeBackground,
        topBar = {
            when {
                selectedBoundDevice != null && selectedBoundDevice.productType != ProductType.RadioDetection -> DeviceDetailTopBar(
                    title = if (showDeviceSettings) "设备设置" else selectedBoundDevice.name,
                    isOnline = selectedRuntimeDevice?.isOnline == true,
                    showSettings = !showDeviceSettings,
                    onBack = {
                        if (showDeviceSettings) {
                            showDeviceSettings = false
                        } else {
                            selectedLinkSerial = null
                            userData.selectedLinkSerial = null
                        }
                    },
                    onSettings = { showDeviceSettings = true }
                )
                activeProductPage != null && selectedBoundDevice == null -> ProductPageTopBar(
                    productType = activeProductPage,
                    onBack = { activeProductPage = null }
                )
            }
        }
    ) { padding ->
        MainScreenContent(
            modifier = Modifier.padding(padding),
            isLoading = isLoading,
            runtimeDevices = runtimeDevices,
            boundAccountDevices = boundAccountDevices,
            activeProductPage = activeProductPage,
            selectedBoundDevice = selectedBoundDevice,
            selectedFireBucketLink = selectedFireBucketLink,
            onProductSelected = {
                selectedLinkSerial = null
                userData.selectedLinkSerial = null
                showDeviceSettings = false
                activeProductPage = it
                mainViewModel.openProduct(it)
            },
            onLinkSelected = {
                selectedLinkSerial = it.serialNumber
                userData.selectedLinkSerial = it.serialNumber
                showDeviceSettings = false
                userData.preferredProductType = it.productType
                mainViewModel.openDevice(it)
            },
            onSelectedDeviceBack = {
                selectedLinkSerial = null
                userData.selectedLinkSerial = null
                showDeviceSettings = false
            },
            onSettingsClick = { showSettings = true },
            showDeviceSettings = showDeviceSettings,
            onRenameDevice = { device, newName ->
                mainViewModel.updateDeviceName(device, newName) { success, message ->
                    if (success) {
                        ToastUtils.showToast("设备名修改成功")
                        boundDeviceVersion += 1
                    } else {
                        ToastUtils.showToast(message ?: "设备名修改失败")
                    }
                }
            },
        )
    }

    if (showSettings) {
        PlatformSettingsSheet(
            account = account,
            deviceCount = boundAccountDevices.size,
            isFloatingWindowEnabled = isFloatingWindowEnabled,
            hasFloatingWindowPermission = hasFloatingWindowPermission,
            onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
            onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
            onDismiss = { showSettings = false }
        )
    }

    if (onBack != null) {
        BackHandler {
            if (showDeviceSettings) {
                showDeviceSettings = false
            } else if (selectedBoundDevice != null) {
                selectedLinkSerial = null
                userData.selectedLinkSerial = null
            } else if (activeProductPage != null) {
                activeProductPage = null
            } else {
                onBack()
            }
        }
    }
}

@Composable
internal fun MainScreenContent(
    isLoading: Boolean,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    boundAccountDevices: List<BoundAccountDevice>,
    activeProductPage: ProductType?,
    selectedBoundDevice: BoundAccountDevice?,
    selectedFireBucketLink: FireBucketLinkDevice?,
    onProductSelected: (ProductType) -> Unit,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    onSelectedDeviceBack: () -> Unit = {},
    onSettingsClick: () -> Unit,
    showDeviceSettings: Boolean = false,
    onRenameDevice: (BoundAccountDevice, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> CircularProgressIndicator(
            modifier = modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
        selectedBoundDevice != null -> ProductControlRoute(
            device = selectedBoundDevice,
            fireBucketLink = selectedFireBucketLink,
            showSettings = showDeviceSettings,
            onRenameDevice = onRenameDevice,
            onBack = onSelectedDeviceBack,
            modifier = modifier
        )
        activeProductPage != null -> {
            ProductDevicesScreen(
                productType = activeProductPage,
                runtimeDevices = runtimeDevices.filter { it.productType == activeProductPage },
                knownLinks = boundAccountDevices.filter { it.productType == activeProductPage },
                onLinkSelected = onLinkSelected,
                modifier = modifier
            )
        }
        else -> {
            ProductHome(
                onProductSelected = onProductSelected,
                onLinkSelected = onLinkSelected,
                boundAccountDevices = boundAccountDevices,
                runtimeDevices = runtimeDevices,
                onSettingsClick = onSettingsClick,
                modifier = modifier
            )
        }
    }
}

/** 账号侧以登录下发的绑定设备列表为准，MQTT 上线后再用实时包替换展示。 */
internal fun fireBucketLinkPlaceholderFromBoundAccount(info: BoundAccountDevice): FireBucketLinkDevice {
    return FireBucketLinkDevice(
        event_type = "LocalPending",
        serial_number = info.serialNumber,
        deviceName = info.name.ifBlank { info.serialNumber },
        deviceType = "Link",
        manufacturer = "",
        deviceModel = "",
        isOnline = false,
        hwVersion = "",
        swVersion = "",
        uptime = 0,
        deviceConfig = "",
        subDevices = emptyList(),
        timestamp = "",
        productType = info.productType,
    )
}
