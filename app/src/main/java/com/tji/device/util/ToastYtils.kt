package com.tji.device.util

import android.content.Context
import android.widget.Toast
import com.tji.device.BuildConfig
import com.tji.device.ui.floating.FloatingWindowUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ToastUtils {

    const val productId = "3"

    const val linkUrl = "http://192.168.5.1/index.html"

    const val switchUrl = "http://192.168.5.10/index.html"

    private val _needUpdate = MutableStateFlow(false)
    val needUpdate: StateFlow<Boolean> = _needUpdate

    private lateinit var appContext: Context

    // 获取版本名称（从 BuildConfig 中获取）
    private val versionName = BuildConfig.VERSION_NAME  // 从 BuildConfig 获取版本名称
    // 获取版本号（从 BuildConfig 中获取）
    private val versionCode = BuildConfig.VERSION_CODE  // 从 BuildConfig 获取版本号


    val _uiState = MutableStateFlow(FloatingWindowUiState())
    val uiState: StateFlow<FloatingWindowUiState> = _uiState
    /**
     * 初始化方法，用于设置应用上下文。
     * 需要在应用启动时调用一次，通常在 Application 类中调用。
     */
    fun init(context: Context) {
        // 使用 applicationContext，确保在应用中任何地方都能访问该上下文，而不是某个 Activity 的上下文
        appContext = context.applicationContext
    }

    /**
     * 显示短时间的 Toast 消息。
     * @param message 需要显示的消息内容
     */
    fun showToast(message: String) {
        // 使用应用上下文显示 Toast
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 获取应用的版本名称。
     * @return 返回版本名称，例如 "1.0.0"
     */
    fun getVersionName(): String {
        return versionName
    }

    /**
     * 获取应用的版本号。
     * @return 返回版本号，例如 1
     */
    fun getVersionCode(): Int {
        return versionCode
    }


    /**
     * 设置是否需要更新的状态。
     * @param need 如果需要更新则传入 true，否则传入 false
     */

    fun setNeedUpdate(need: Boolean) {
        _needUpdate.value = need
    }

    /**
     * 获取当前是否需要更新的状态。
     * @return 如果需要更新则返回 true，否则返回 false
     */
    fun getNeedUpdate(): Boolean {
        return _needUpdate.value
    }

}
