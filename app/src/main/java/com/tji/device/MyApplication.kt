package com.tji.device

import android.app.Application
import android.util.Log
import com.tji.device.di.AppContainer
import com.tji.device.diagnostics.AppDiagnostics
import com.tji.device.ui.AppUiNotifier
import com.tji.device.update.AppUpdateValidation
import com.tji.device.update.validateAppUpdateCandidate
import com.tji.network.TjiApiGateway
import com.tji.network.config.NetworkEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AppDiagnostics.initialize(this)

        AppContainer.initialize(this)

        MapSdkInitializer.initialize(this)

        AppUiNotifier.initialize(applicationContext)

        applicationScope.launch { checkUpdate() }

        if (BuildConfig.DEBUG) Log.d(TAG, "Application started")
    }

    private suspend fun checkUpdate() {
        try {
            val productId = BuildConfig.TJI_APP_UPDATE_PRODUCT_ID
            if (productId <= 0) {
                Log.w(TAG, "未配置 TJI Platform App 更新 productId，跳过更新检查")
                AppUiNotifier.setAppUpdateCandidate(null)
                return
            }
            Log.d(
                TAG,
                "开始检查 App 更新: productId=$productId " +
                        "localVersionCode=${BuildConfig.VERSION_CODE} localVersionName=${BuildConfig.VERSION_NAME}"
            )
            val response = TjiApiGateway.getProductInfo(
                productId = productId,
                packageName = BuildConfig.APPLICATION_ID
            )
            if (response.code != 200) {
                Log.w(TAG, "版本检查接口非 200: code=${response.code} message=${response.message}")
                return
            }
            val appVersion = response.data
            if (appVersion == null) {
                Log.w(TAG, "版本检查: 响应 data 为空")
                return
            }
            when (
                val validation = validateAppUpdateCandidate(
                    response = appVersion,
                    localVersionCode = BuildConfig.VERSION_CODE,
                    expectedPackageName = BuildConfig.APPLICATION_ID,
                    expectedSignerSha256 = BuildConfig.TJI_RELEASE_SIGNER_SHA256,
                    downloadBaseUrl = NetworkEndpoints.otaBaseUrl
                )
            ) {
                is AppUpdateValidation.Available -> {
                    Log.d(TAG, "发现通过身份与完整性元数据校验的 App 更新")
                    AppUiNotifier.setAppUpdateCandidate(validation)
                }
                is AppUpdateValidation.Unavailable -> {
                    Log.w(TAG, "忽略不可安装的 App 更新元数据: ${validation.reason}")
                    AppUiNotifier.setAppUpdateCandidate(null)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Log.e(TAG, "版本检查异常", e)
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
