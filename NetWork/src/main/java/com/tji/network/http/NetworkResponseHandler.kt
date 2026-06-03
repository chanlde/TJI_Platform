package com.tji.network.http

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.tji.network.data.ApiResponse
import retrofit2.HttpException

internal class NetworkResponseHandler(
    private val tag: String,
    private val gson: Gson = Gson()
) {
    suspend fun <T> safeApiCall(call: suspend () -> ApiResponse<T>): ApiResponse<T> {
        return try {
            val response = call.invoke()

            if (response.code == 200) {
                response
            } else {
                ApiResponse(code = response.code, message = "${response.message}", data = null)
            }
        } catch (e: Exception) {
            Log.e(tag, "API调用异常", e)
            ApiResponse(code = -1, message = handleException(e), data = null)
        }
    }

    fun <T> parseVersionResponse(
        response: ApiResponse<JsonElement>,
        clazz: Class<T>
    ): ApiResponse<T> {
        if (response.code != 200) {
            return ApiResponse(code = response.code, message = response.message, data = null)
        }

        val data = response.data
            ?: return ApiResponse(code = -1, message = "版本接口返回 data 为空", data = null)

        if (!data.isJsonObject) {
            val rawData = data.toString()
            val message = if (rawData.contains("redirect", ignoreCase = true)) {
                "版本接口被重定向到登录，请后端放行 getAppVersion"
            } else {
                "版本接口 data 格式错误: $rawData"
            }
            Log.w(tag, message)
            return ApiResponse(code = -1, message = message, data = null)
        }

        return try {
            ApiResponse(
                code = response.code,
                message = response.message,
                data = gson.fromJson(data, clazz)
            )
        } catch (e: Exception) {
            Log.e(tag, "版本接口 data 解析失败", e)
            ApiResponse(code = -1, message = "版本接口 data 解析失败", data = null)
        }
    }

    private fun handleException(e: Exception): String {
        return when (e) {
            is HttpException -> {
                when (e.code()) {
                    401, 403 -> "登录状态已失效，请重新登录"
                    404 -> "接口不存在，请检查服务器版本"
                    in 500..599 -> "服务器异常(${e.code()})，请检查后台接口"
                    else -> "请求失败(${e.code()})"
                }
            }
            is java.net.UnknownHostException -> "网络连接失败，请检查网络"
            is java.net.SocketTimeoutException -> "请求超时，请重试"
            is javax.net.ssl.SSLException -> "安全连接失败"
            else -> "网络异常"
        }
    }
}
