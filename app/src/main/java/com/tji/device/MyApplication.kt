package com.tji.device

import android.app.Application
import android.util.Log
import com.tji.device.di.AppContainer
import com.tji.device.ui.AppUiNotifier
import com.tji.network.TjiApiGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AppContainer.initialize(this)

        MapSdkInitializer.initialize(this)

        AppUiNotifier.initialize(applicationContext)

        applicationScope.launch { checkUpdate() }

        if (BuildConfig.DEBUG) Log.d(TAG, "Application started")
    }

    private suspend fun checkUpdate() {
        try {
            Log.d(
                TAG,
                "开始检查 App 更新: productId=$APP_UPDATE_PRODUCT_ID " +
                        "localVersionCode=${BuildConfig.VERSION_CODE} localVersionName=${BuildConfig.VERSION_NAME}"
            )
            val response = TjiApiGateway.getProductInfo(APP_UPDATE_PRODUCT_ID)
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
                AppUiNotifier.setAppUpdateAvailable(true)
            } else {
                Log.d(TAG, "无需强制更新: 本地 versionCode=$localCode 服务器 innerVersion=$serverInner")
                AppUiNotifier.setAppUpdateAvailable(false)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "版本检查异常", e)
        }
    }

    companion object {
        private const val TAG = "MyApplication"
        private const val APP_UPDATE_PRODUCT_ID = 2
    }
}
