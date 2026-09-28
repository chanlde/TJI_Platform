package com.tji.network

import android.util.Log
import com.tji.network.data.ApiResponse
import com.tji.network.data.AppVersion
import com.tji.network.data.LoginResponse
import com.tji.network.data.OtaLatestResponse
import com.tji.network.data.OtaTaskRequest
import com.tji.network.data.OtaTaskResponse
import com.tji.network.data.mergeWith
import com.tji.network.http.NetworkHttpClient
import com.tji.network.http.NetworkResponseHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

@Suppress("TooManyFunctions") // One facade keeps login, version and OTA calls on the same configured client.
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
        val completeResponse = loginProduct(
            account = account,
            password = password,
            productId = null
        )
        if (completeResponse.code == 200 && completeResponse.data?.boundDevicesComplete == true) {
            return completeResponse
        }

        // Older servers have no complete-catalog marker. Keep their per-product login flow.
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
        productId: Int?
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

    suspend fun getProductInfo(
        productId: Int = 2,
        packageName: String? = null
    ): ApiResponse<AppVersion> {
        val response = responseHandler.safeApiCall {
            httpClient.apiService.getProductInfo(
                productId = productId,
                type = OTA_TYPE_APP,
                packageName = packageName
            )
        }
        return responseHandler.parseVersionResponse(response, AppVersion::class.java)
    }

    suspend fun getOtaLatest(productId: Int, hardwareVersion: String?): ApiResponse<OtaLatestResponse> {
        val response = responseHandler.safeApiCall {
            httpClient.apiService.getOtaLatest(
                productId = productId,
                type = OTA_TYPE_FIRMWARE,
                hardwareVersion = hardwareVersion
            )
        }
        return responseHandler.parseVersionResponse(response, OtaLatestResponse::class.java)
    }

    suspend fun getActiveOtaTask(deviceSn: String): Result<OtaTaskResponse?> = try {
        Result.success(httpClient.apiService.getActiveOtaTask(bearerToken(), deviceSn))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (notFound: HttpException) {
        if (notFound.code() == 404) Result.success(null) else Result.failure(notFound)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun reserveOtaTask(request: OtaTaskRequest): Result<OtaTaskResponse> = try {
        Result.success(httpClient.apiService.reserveOtaTask(bearerToken(), request))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getOtaTask(taskId: String): Result<OtaTaskResponse> = try {
        Result.success(httpClient.apiService.getOtaTask(bearerToken(), taskId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun bearerToken(): String = authToken
        ?.takeIf { it.isNotBlank() }
        ?.let { "Bearer $it" }
        ?: throw IllegalStateException("登录状态已失效，请重新登录")

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
