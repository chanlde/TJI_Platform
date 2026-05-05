package com.tji.device.product.firebucket.viewmodel

import com.tji.device.product.firebucket.model.SwitchControlParms
import com.tji.device.product.firebucket.model.SwitchUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * FireBucket 开关控制视图模型接口。
 */
interface SwitchViewModelInterface {
    val uiState: StateFlow<SwitchUiState>
    val errorMessage: StateFlow<String?>

    fun setAngle(linkSn: String, scParms: SwitchControlParms)

    fun clearErrorMessage()
}
