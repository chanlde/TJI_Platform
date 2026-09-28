package com.tji.network.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiServiceOtaQueryTest {
    @Test
    fun appVersionQueryIdentifiesNewPackageWithoutChangingLegacyUrl() = runBlocking {
        var requestedUrl: okhttp3.HttpUrl? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedUrl = chain.request().url
            Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"code":200,"data":null}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        api.getProductInfo(productId = 2, type = 1, packageName = "com.tji.device")
        assertEquals("com.tji.device", requestedUrl?.queryParameter("packageName"))
        assertEquals("/api/data/appversion/getAppVersion", requestedUrl?.encodedPath)

        api.getProductInfo(productId = 2, type = 1)
        assertEquals(false, requestedUrl?.queryParameterNames?.contains("packageName"))
    }

    @Test
    fun firmwareQueryIncludesKnownHardwareAndOmitsUnknownHardware() = runBlocking {
        var requestedUrl: okhttp3.HttpUrl? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedUrl = chain.request().url
            Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"code":200,"data":{"has_update":false}}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        api.getOtaLatest(productId = 5, type = 2, hardwareVersion = "HW-A")
        assertEquals("HW-A", requestedUrl?.queryParameter("hardwareVersion"))
        assertEquals("5", requestedUrl?.queryParameter("productId"))
        assertEquals("2", requestedUrl?.queryParameter("type"))

        requestedUrl = null
        api.getOtaLatest(productId = 5, type = 2, hardwareVersion = null)
        assertEquals(false, requestedUrl?.queryParameterNames?.contains("hardwareVersion"))
    }
}
