package com.tji.device.product.firebucket.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.di.AppContainer
import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.model.FireBucketSwitchControlParams
import com.tji.device.product.firebucket.model.FireBucketSwitchUiState
import com.tji.device.product.firebucket.viewmodel.FireBucketSwitchViewModel

@Composable
fun SwitchItemComposable(
    linkSn: String,
    linkOnline: Boolean,
    switch: FireBucketSwitchState
) {
    val controlParams = remember(switch.serialNumber) {
        FireBucketSwitchControlParams(
            sn = switch.serialNumber,
            angle = switch.currentAngle.toInt(),
            speed = 100,
            mode = ControlMode.ABSOLUTE
        )
    }
    val isPreview = LocalInspectionMode.current
    val switchVm: FireBucketSwitchViewModel? =
        if (isPreview) {
            null
        } else {
            viewModel(
                key = "firebucket-switch:$linkSn:${switch.serialNumber}",
                factory = AppContainer.fireBucketSwitchViewModelFactory
            )
        }
    val uiState by if (switchVm == null) {
        remember { mutableStateOf(FireBucketSwitchUiState()) }
    } else {
        switchVm.uiState.collectAsStateWithLifecycle()
    }

    SwitchItem(
        switch = switch,
        controlEnabled = canControlFireBucketSwitch(linkOnline),
        controlParams = controlParams,
        errorMessage = uiState.errorMessage,
        onControl = { updatedParms ->
            if (!isPreview) switchVm?.setAngle(linkSn, updatedParms)
        }
    )
}

/**
 * 旧 HydroSwitch 只有收到控制后才会上报在线状态，因此桶的 isOnline 不能作为首条命令门禁。
 * Link 在线代表 MQTT 控制通道可用；Link 离线时仍禁止发送。
 */
internal fun canControlFireBucketSwitch(linkOnline: Boolean): Boolean = linkOnline
