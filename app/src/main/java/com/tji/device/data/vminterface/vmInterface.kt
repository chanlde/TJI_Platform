package com.tji.device.data.vminterface

import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.LoginUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录视图模型接口，定义登录相关状态和操作。
 */
interface LoginViewModelInterface {
    val uiState: StateFlow<LoginUiState>
    val account: StateFlow<String>
    /** 登录后若需用户在多台绑定设备中选一台（与 FireBucket「Link」产品语义无关）。 */
    val needAccountDeviceSelection: StateFlow<List<BoundAccountDevice>?>
    val selectedLinkSerial: StateFlow<String?>

    /**
     * 执行登录操作。
     * @param account 账号
     * @param password 密码
     * @param rememberMe 是否记住登录状态
     */
    fun login(account: String, password: String, rememberMe: Boolean,callback: (Boolean, String?) -> Unit)

    /**
     * 执行登出操作。
     */
    fun logout()

    /**
     * 用户在多台绑定设备中选择其一（当有多个 SN 时）。
     */
    fun selectBoundAccountDevice(device: BoundAccountDevice)

    /**
     * 清除错误消息。
     */
    fun clearErrorMessage()
}

