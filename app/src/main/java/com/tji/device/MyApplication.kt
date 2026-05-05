package com.tji.device

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tji.device.util.ToastUtils
import com.tji.network.DataReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MyApplication : Application(), LifecycleOwner {
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onCreate() {
        super.onCreate()

        ToastUtils.init(applicationContext)

        lifecycleScope.launch {
            performUpdateCheck()
        }

        Log.d(TAG, "Application started")
    }

    suspend fun performUpdateCheck() = coroutineScope {
        launch(Dispatchers.IO) {
            checkUpdate()
        }
    }

    private suspend fun checkUpdate() {
        try {
            val response = DataReportManager.getInstance().getProductInfo()
            if (response.code != 200) {
                Log.w(TAG, "版本检查接口非 200: code=${response.code} message=${response.message}")
                return
            }
            val appVersion = response.data
            if (appVersion == null) {
                Log.w(TAG, "版本检查: 响应 data 为空")
                return
            }
            val serverInner = appVersion.innerVersion
            if (serverInner == null) {
                Log.w(TAG, "版本检查: 服务器未返回 innerVersion，跳过强制更新（可对比 version 字符串：${appVersion.version}）")
                return
            }

            val localCode = BuildConfig.VERSION_CODE
            if (localCode < serverInner) {
                Log.d(TAG, "需要更新: 本地 versionCode=$localCode 服务器 innerVersion=$serverInner")
                ToastUtils.setNeedUpdate(true)
            } else {
                Log.d(TAG, "无需强制更新: 本地 versionCode=$localCode 服务器 innerVersion=$serverInner")
                ToastUtils.setNeedUpdate(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "版本检查异常", e)
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
