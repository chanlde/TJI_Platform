package com.tji.network.api

import com.google.gson.JsonElement
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse
import com.tji.network.data.OtaTaskRequest
import com.tji.network.data.OtaTaskResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

internal interface ApiService {

    @GET("/userManager/user/login")
    suspend fun login(
        @Query("account") account: String,
        @Query("password") password: String,
        @Query("productId") productId: Int?,
        @Query("rcSn") rcSn: String
    ): ApiResponse<LoginResponse>

    @GET("/api/data/appversion/getAppVersion")
    suspend fun getProductInfo(
        @Query("productId") productId: Int,
        @Query("type") type: Int,
        @Query("packageName") packageName: String? = null
    ): ApiResponse<JsonElement>

    @GET("/api/data/appversion/getAppVersion")
    suspend fun getOtaLatest(
        @Query("productId") productId: Int,
        @Query("type") type: Int,
        @Query("hardwareVersion") hardwareVersion: String?
    ): ApiResponse<JsonElement>

    @GET("/api/v1/ota/tasks/active")
    suspend fun getActiveOtaTask(
        @Header("Authorization") authorization: String,
        @Query("deviceSn") deviceSn: String
    ): OtaTaskResponse

    @POST("/api/v1/ota/tasks")
    suspend fun reserveOtaTask(
        @Header("Authorization") authorization: String,
        @Body request: OtaTaskRequest
    ): OtaTaskResponse

    @GET("/api/v1/ota/tasks/{taskId}")
    suspend fun getOtaTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String
    ): OtaTaskResponse

    @PUT("/userManager/user/updatename")
    suspend fun updateDeviceName(
        @Query("id") id: Int,
        @Query("productName") productName: String
    ): ApiResponse<Unit>
}
