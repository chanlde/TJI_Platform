package com.tji.network

import android.util.Log
import com.tji.network.utils.NetWorkUtils.Companion.BASE_URL
import com.tji.network.data.ApiResponse
import com.tji.network.data.ApiService
import com.tji.network.data.AppVersion
import com.tji.network.data.LoginResponse
import com.tji.network.data.OtaLatestResponse
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DataReportManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: DataReportManager? = null

        fun getInstance(): DataReportManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataReportManager().also { INSTANCE = it }
            }
        }
        private const val TAG = "DataReportManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var authToken: String? = null

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            var request = chain.request()
            val path = request.url.encodedPath
            val isUnauthenticatedLogin =
                path.contains("/userManager/user/login") || path.endsWith("/login")

            authToken?.let { token ->
                Log.d(TAG, "🔑 Token 存在: ${token.take(12)}…")

                if (request.header("Authorization") == null) {
                    request = request.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("token", token)
                        .build()

                    Log.d(TAG, "✅ 已添加 Authorization 头")
                } else {
                    Log.d(TAG, "⚠️ Authorization 头已存在，跳过")
                }
            } ?: run {
                if (isUnauthenticatedLogin) {
                    Log.d(TAG, "登录请求（无需预先携带 token）")
                } else {
                    Log.w(TAG, "❌ Token 为空，未添加认证头")
                }
            }

            Log.d(TAG, "========== 请求头信息 ==========")
            request.headers.forEach { (name, value) ->
                Log.d(TAG, "$name: $value")
            }
            Log.d(TAG, "================================")

            chain.proceed(request)
        }
    }

    private fun handleException(e: Exception): String {
        Log.d(TAG, "网络异常1111111111111111111111: ")
        return when (e) {
            is java.net.UnknownHostException -> "网络连接失败，请检查网络"
            is java.net.SocketTimeoutException -> "请求超时，请重试"
            is javax.net.ssl.SSLException -> "安全连接失败"
            else -> "网络异常"
        }
    }

    suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {
        authToken = null
        return safeApiCall {
            apiService.login(account, password)
        }
    }

    suspend fun getProductInfo(): ApiResponse<AppVersion> {
        return safeApiCall {
            apiService.getProductInfo()
        }
    }

    suspend fun getOtaLatest(productType: String): ApiResponse<OtaLatestResponse> {
        return safeApiCall {
            apiService.getOtaLatest(productType = productType)
        }
    }

    /** 登出等场景清空会话 token（与 [clearAuthInfo] 一致，对外暴露）。 */
    fun clearAuthToken() {
        clearAuthInfo()
    }

    private suspend fun <T> safeApiCall(call: suspend () -> ApiResponse<T>): ApiResponse<T> {
        return try {
            val response = call.invoke()

            if (response.code == 200) {
                response
            } else {
                ApiResponse(code = response.code, message = "${response.message}", data = null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "API调用异常", e)
            ApiResponse(code = -1, message = handleException(e), data = null)
        }
    }

    private fun clearAuthInfo() {
        authToken = null
        Log.d(TAG, "认证信息已清除")
    }

    fun destroy() {
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
        clearAuthInfo()
        INSTANCE = null
    }
}
