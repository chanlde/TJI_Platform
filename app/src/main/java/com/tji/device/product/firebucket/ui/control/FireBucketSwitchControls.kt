package com.tji.device.product.firebucket.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.di.AppContainer
import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.firebucket.model.SwitchControlParms
import com.tji.device.product.firebucket.viewmodel.SwitchViewModel

@Composable
fun SwitchItemComposable(linkSn: String, switch: Switch) {
    var scParms by remember {
        mutableStateOf(
            SwitchControlParms(
                sn = switch.serialNumber,
                angle = switch.currentAngle.toInt(),
                speed = 10,
                mode = ControlMode.ABSOLUTE
            )
        )
    }
    val isPreview = LocalInspectionMode.current
    val switchVm: SwitchViewModel? =
        if (isPreview) null else viewModel(factory = AppContainer.fireBucketSwitchViewModelFactory)

    SwitchItem(
        linkSn = linkSn,
        switch = switch,
        scParms = scParms,
        onControl = { updatedParms ->
            if (!isPreview) switchVm?.setAngle(linkSn, updatedParms)
        },
        onAngleChange = { newAngle ->
            val updatedParms = scParms.copy(angle = newAngle)
            scParms = updatedParms
            if (!isPreview) switchVm?.setAngle(linkSn, updatedParms)
        }
    )
}
