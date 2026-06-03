package com.tji.network.api

import com.google.gson.JsonElement
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

internal interface ApiService {

    @GET("/userManager/user/login")
    suspend fun login(
        @Query("account") account: String,
        @Query("password") password: String,
        @Query("productId") productId: Int = 2,
        @Query("rcSn") rcSn: String = ""
    ): ApiResponse<LoginResponse>

    @GET("/api/data/appversion/getAppVersion")
    suspend fun getProductInfo(
        @Query("productId") productId: Int = 2,
        @Query("type") type: Int = 1
    ): ApiResponse<JsonElement>

    @GET("/api/data/appversion/getAppVersion")
    suspend fun getOtaLatest(
        @Query("productId") productId: Int,
        @Query("type") type: Int
    ): ApiResponse<JsonElement>

    @PUT("/userManager/user/updatename")
    suspend fun updateDeviceName(
        @Query("id") id: Int,
        @Query("productName") productName: String
    ): ApiResponse<Unit>
}
