package com.tji.network.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("/userManager/user/login")
    suspend fun login(
        @Query("account") account: String,
        @Query("password") password: String,
        @Query("productId") productId: String = "2",
        @Query("rcSn") rcSn: String = ""
    ): ApiResponse<LoginResponse>

    /**
     * 产品版本信息。路径与历史上通过网关统一转发时的 `/productManager/product/{state}` 一致，当前默认 state=1。
     */
    @GET("/productManager/product/2")
    suspend fun getProductInfo(): ApiResponse<AppVersion>

    @GET("/api/ota/latest")
    suspend fun getOtaLatest(
        @Query("product_type") productType: String
    ): ApiResponse<OtaLatestResponse>
}
