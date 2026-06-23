package com.tji.device.product.solarclean.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.di.AppContainer
import com.tji.device.product.solarclean.model.SolarCleanControlSettings
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModel
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback

@Composable
fun SolarCleanControlScreen(
    device: BoundAccountDevice,
    onRenameDevice: (BoundAccountDevice, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val viewModel: SolarCleanControlViewModel? = if (isPreview) {
        null
    } else {
        viewModel(factory = AppContainer.solarCleanControlViewModelFactory)
    }
    val devices by viewModel?.devices?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    }
    val state = devices.firstOrNull { it.serialNumber == device.serialNumber }
    val displayState = if (isPreview) previewSolarCleanState(device.serialNumber) else state
    val commandFeedback by viewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(SolarCleanCommandFeedback()) }
    }
    val controlSettingsByDevice by viewModel?.controlSettings?.collectAsStateWithLifecycle().let {
        it ?: remember { androidx.compose.runtime.mutableStateOf(emptyMap()) }
    }
    val controlSettings = controlSettingsByDevice[device.serialNumber] ?: SolarCleanControlSettings()

    LaunchedEffect(viewModel, device.serialNumber) {
        viewModel?.requestDeviceInfo(device.serialNumber)
    }

    val controlsEnabled = viewModel != null && displayState?.isOnline == true
    SolarCleanControlPage(
        device = device,
        state = displayState,
        controlSettings = controlSettings,
        enabled = controlsEnabled,
        commandFeedback = commandFeedback,
        onPumpOn = { viewModel?.setPump(device.serialNumber, true) },
        onPumpOff = { viewModel?.setPump(device.serialNumber, false) },
        onPressureChanged = { viewModel?.setPumpPressure(device.serialNumber, it.toDouble()) },
        onSprayAngleChanged = { viewModel?.setSprayAngle(device.serialNumber, it.toDouble()) },
        onSwingSpeedChanged = { viewModel?.setSwingSpeed(device.serialNumber, it.toDouble()) },
        onSwingOn = { viewModel?.setServoSwing(device.serialNumber, true) },
        onSwingOff = { viewModel?.setServoSwing(device.serialNumber, false) },
        modifier = modifier
    )
}
