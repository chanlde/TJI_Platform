package com.tji.device.ui

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级、无页面归属的轻量 UI 通知状态。
 *
 * 仅负责短提示和 App 更新提示；具体产品页面状态不应放在这里。
 */
object AppUiNotifier {
    private val _appUpdateAvailable = MutableStateFlow(false)
    val appUpdateAvailable = _appUpdateAvailable.asStateFlow()

    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun showShortMessage(message: String) {
        check(::applicationContext.isInitialized) {
            "AppUiNotifier.initialize must be called before showing messages"
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    fun setAppUpdateAvailable(available: Boolean) {
        _appUpdateAvailable.value = available
    }
}
