package com.tji.device.ui.components

import com.tji.device.data.local.RememberedLoginCredentials

internal data class LoginFormDraft(
    val account: String = "",
    val password: String = "",
    val rememberMe: Boolean = false
)

/**
 * 后台偏好加载完成时，只有尚未被用户编辑的空白表单可以接收恢复值。
 */
internal fun restoreRememberedLogin(
    current: LoginFormDraft,
    remembered: RememberedLoginCredentials?,
    userHasEdited: Boolean
): LoginFormDraft {
    if (userHasEdited || remembered == null) return current
    return LoginFormDraft(
        account = remembered.account,
        password = remembered.password,
        rememberMe = true
    )
}
