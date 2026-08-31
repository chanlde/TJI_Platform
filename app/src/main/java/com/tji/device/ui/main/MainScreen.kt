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
import com.tji.device.data.session.DeviceKey
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.ui.AppUiNotifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun MainScreen(
    onBack: (() -> Unit)? = null,
    onLogout: () -> Unit = {},
    isFloatingWindowEnabled: Boolean = true,
    hasFloatingWindowPermission: Boolean = true,
    connectionMode: FireBucketConnectionMode = FireBucketConnectionMode.CLOUD,
    onConnectionModeChange: (FireBucketConnectionMode) -> Unit = {},
    onFloatingWindowEnabledChange: (Boolean) -> Unit = {},
    onOpenFloatingWindowPermission: () -> Unit = {},
) {
    val mainViewModel = LocalMainViewModel.current
    val runtimeDevices by mainViewModel.runtimeDevices.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val session by mainViewModel.sessionStore.state.collectAsStateWithLifecycle()
    val account = session.account
    val boundAccountDevices = session.boundDevices
    val selectedDeviceKey = session.selectedDeviceKey
    var activeProductPage by remember { mutableStateOf<ProductType?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeviceSettings by remember { mutableStateOf(false) }
    val selectedBoundDevice = remember(selectedDeviceKey, boundAccountDevices) {
        val key = selectedDeviceKey ?: return@remember null
        boundAccountDevices.firstOrNull {
            it.serialNumber == key.serialNumber && it.productType == key.productType
        }
    }
    val runtimeByKey = remember(runtimeDevices) {
        runtimeDevices.associateBy { DeviceKey(it.productType, it.serialNumber) }
    }
    val selectedRuntimeDevice = selectedDeviceKey?.let(runtimeByKey::get)
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
                            mainViewModel.sessionStore.clearSelection()
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
            selectedRuntimeDevice = selectedRuntimeDevice,
            onProductSelected = {
                showDeviceSettings = false
                mainViewModel.openProduct(it) { success, message ->
                    if (success) {
                        activeProductPage = it
                    } else {
                        AppUiNotifier.showShortMessage(message ?: "打开产品失败")
                    }
                }
            },
            onLinkSelected = {
                showDeviceSettings = false
                mainViewModel.openDevice(it) { success, message ->
                    if (!success) {
                        AppUiNotifier.showShortMessage(message ?: "打开设备失败")
                    }
                }
            },
            onSelectedDeviceBack = {
                mainViewModel.sessionStore.clearSelection()
                showDeviceSettings = false
            },
            onSettingsClick = { showSettings = true },
            showDeviceSettings = showDeviceSettings,
            onRenameDevice = { device, newName ->
                mainViewModel.updateDeviceName(device, newName) { success, message ->
                    if (success) {
                        AppUiNotifier.showShortMessage("设备名修改成功")
                    } else {
                        AppUiNotifier.showShortMessage(message ?: "设备名修改失败")
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
            connectionMode = connectionMode,
            onConnectionModeChange = onConnectionModeChange,
            onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
            onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
            onLogout = {
                showSettings = false
                onLogout()
            },
            onDismiss = { showSettings = false }
        )
    }

    if (onBack != null) {
        BackHandler {
            if (showDeviceSettings) {
                showDeviceSettings = false
            } else if (selectedBoundDevice != null) {
                mainViewModel.sessionStore.clearSelection()
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
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedRuntimeDevice: ProductDeviceRuntimeSnapshot? = null,
    onSelectedDeviceBack: () -> Unit = {},
    showDeviceSettings: Boolean = false,
    onRenameDevice: (BoundAccountDevice, String) -> Unit = { _, _ -> }
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
            runtimeDevice = selectedRuntimeDevice,
            showSettings = showDeviceSettings,
            onRenameDevice = onRenameDevice,
            onBack = onSelectedDeviceBack,
            modifier = modifier
        )
        activeProductPage != null -> {
            ProductDevicesScreen(
                productType = activeProductPage,
                runtimeDevices = runtimeDevices,
                knownLinks = boundAccountDevices,
                onLinkSelected = onLinkSelected,
                modifier = modifier
            )
        }
        else -> {
            ProductHome(
                onProductSelected = onProductSelected,
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
