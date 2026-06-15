package com.tji.network

import android.util.Log
import com.tji.network.data.ApiResponse
import com.tji.network.data.AppVersion
import com.tji.network.data.LoginResponse
import com.tji.network.data.OtaLatestResponse
import com.tji.network.data.mergeWith
import com.tji.network.http.NetworkHttpClient
import com.tji.network.http.NetworkResponseHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

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
        private const val OTA_TYPE_APP = 1
        private const val OTA_TYPE_FIRMWARE = 2
        private val LOGIN_PRODUCT_IDS = listOf(2, 3, 4, 5, 6, 7, 8)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var authToken: String? = null
    private val httpClient = NetworkHttpClient(tag = TAG) { authToken }
    private val responseHandler = NetworkResponseHandler(tag = TAG)

    val okHttpClient: OkHttpClient
        get() = httpClient.okHttpClient

    suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {
        authToken = null
        val responses = loginAllProducts(account = account, password = password)

        val successfulResponses = responses.filter { it.code == 200 && it.data != null }
        val mergedLoginData = successfulResponses
            .mapNotNull { it.data }
            .reduceOrNull { accumulator, item -> accumulator.mergeWith(item) }

        return if (mergedLoginData != null) {
            ApiResponse(
                code = 200,
                message = successfulResponses.firstOrNull()?.message ?: "成功",
                data = mergedLoginData
            )
        } else {
            responses.firstOrNull { it.code != 200 }
                ?: ApiResponse(code = -1, message = "登录接口未返回可用数据", data = null)
        }
    }

    private suspend fun loginAllProducts(
        account: String,
        password: String
    ): List<ApiResponse<LoginResponse>> = coroutineScope {
        LOGIN_PRODUCT_IDS.map { productId ->
            async(Dispatchers.IO) {
                loginProduct(account = account, password = password, productId = productId)
            }
        }.awaitAll()
    }

    private suspend fun loginProduct(
        account: String,
        password: String,
        productId: Int
    ): ApiResponse<LoginResponse> {
        val response = responseHandler.safeApiCall {
            httpClient.apiService.login(
                account = account,
                password = password,
                productId = productId
            )
        }
        Log.d(
            TAG,
            "登录产品线返回: productId=$productId code=${response.code} " +
                "hasData=${response.data != null}"
        )
        return response
    }

    suspend fun getProductInfo(productId: Int = 2): ApiResponse<AppVersion> {
        val response = responseHandler.safeApiCall {
            httpClient.apiService.getProductInfo(productId = productId, type = OTA_TYPE_APP)
        }
        return responseHandler.parseVersionResponse(response, AppVersion::class.java)
    }

    suspend fun getOtaLatest(productId: Int): ApiResponse<OtaLatestResponse> {
        val response = responseHandler.safeApiCall {
            httpClient.apiService.getOtaLatest(productId = productId, type = OTA_TYPE_FIRMWARE)
        }
        return responseHandler.parseVersionResponse(response, OtaLatestResponse::class.java)
    }

    suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> {
        return responseHandler.safeApiCall {
            httpClient.apiService.updateDeviceName(id = id, productName = productName)
        }
    }

    /** 登出等场景清空会话 token（与 [clearAuthInfo] 一致，对外暴露）。 */
    fun clearAuthToken() {
        clearAuthInfo()
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
