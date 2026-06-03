package com.tji.network.http

import android.util.Log
import com.tji.network.api.ApiService
import com.tji.network.utils.NetWorkUtils.Companion.BASE_URL
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

internal class NetworkHttpClient(
    private val tag: String,
    private val tokenProvider: () -> String?
) {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(tag, message.redactSensitiveNetworkLog())
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
            val isPublicVersionCheck =
                path.contains("/api/data/appversion/getAppVersion")

            if (isPublicVersionCheck) {
                Log.d(tag, "版本查询接口无需 token")
            } else tokenProvider()?.let { token ->
                Log.d(tag, "Token 存在，准备添加认证头")

                if (request.header("token") == null) {
                    request = request.newBuilder()
                        .addHeader("token", token)
                        .build()

                    Log.d(tag, "已添加 token 认证头")
                } else {
                    Log.d(tag, "token 头已存在，跳过")
                }
            } ?: run {
                if (isUnauthenticatedLogin) {
                    Log.d(tag, "登录请求（无需预先携带 token）")
                } else {
                    Log.w(tag, "❌ Token 为空，未添加认证头")
                }
            }

            Log.d(tag, "========== 请求头信息 ==========")
            request.headers.forEach { (name, value) ->
                Log.d(tag, "$name: ${redactHeaderValue(name, value)}")
            }
            Log.d(tag, "================================")

            chain.proceed(request)
        }
    }

    private fun redactHeaderValue(name: String, value: String): String {
        return when (name.lowercase()) {
            "authorization",
            "token",
            "cookie",
            "set-cookie" -> "<redacted>"
            else -> value
        }
    }

    private fun String.redactSensitiveNetworkLog(): String {
        return replace(Regex("(?i)(password=)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("(?i)(token=)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("(?i)(\"password\"\\s*:\\s*\")[^\"]+"), "$1<redacted>")
            .replace(Regex("(?i)(\"token\"\\s*:\\s*\")[^\"]+"), "$1<redacted>")
            .replace(Regex("(?i)(token: )\\S+"), "$1<redacted>")
            .replace(Regex("(?i)(Authorization: Bearer )\\S+"), "$1<redacted>")
    }
}
