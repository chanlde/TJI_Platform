package com.tji.network

import android.util.Log
import com.tji.network.data.ApiResponse
import com.tji.network.data.AppVersion
import com.tji.network.data.LoginResponse
import com.tji.network.data.OtaLatestResponse
import com.tji.network.data.mergeWith
import com.tji.network.http.NetworkHttpClient
import com.tji.network.http.NetworkResponseHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object TjiApiGateway {

    private const val TAG = "TjiApiGateway"
    private const val OTA_TYPE_APP = 1
    private const val OTA_TYPE_FIRMWARE = 2

    var authToken: String? = null
    private val httpClient = NetworkHttpClient(tag = TAG) { authToken }
    private val responseHandler = NetworkResponseHandler(tag = TAG)

    suspend fun login(
        account: String,
        password: String,
        productIds: Collection<Int>
    ): ApiResponse<LoginResponse> {
        authToken = null
        val uniqueProductIds = productIds.distinct()
        require(uniqueProductIds.isNotEmpty()) { "At least one login product ID is required" }
        val responses = loginAllProducts(
            account = account,
            password = password,
            productIds = uniqueProductIds
        )

        val successfulResponses = responses.filter { it.code == 200 && it.data != null }
        val failedProductIds = uniqueProductIds.zip(responses)
            .filter { (_, response) -> response.code != 200 || response.data == null }
            .map { (productId, _) -> productId }
        val mergedLoginData = successfulResponses
            .mapNotNull { it.data }
            .reduceOrNull { accumulator, item -> accumulator.mergeWith(item) }

        return if (mergedLoginData != null) {
            ApiResponse(
                code = 200,
                message = if (failedProductIds.isEmpty()) {
                    successfulResponses.firstOrNull()?.message ?: "成功"
                } else {
                    "部分产品数据获取失败：${failedProductIds.joinToString()}"
                },
                data = mergedLoginData
            )
        } else {
            responses.firstOrNull { it.code != 200 }
                ?: ApiResponse(code = -1, message = "登录接口未返回可用数据", data = null)
        }
    }

    private suspend fun loginAllProducts(
        account: String,
        password: String,
        productIds: Collection<Int>
    ): List<ApiResponse<LoginResponse>> = coroutineScope {
        productIds.map { productId ->
            async {
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
                productId = productId,
                rcSn = ""
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "登录产品线返回: productId=$productId code=${response.code} " +
                    "hasData=${response.data != null}"
            )
        }
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

}
