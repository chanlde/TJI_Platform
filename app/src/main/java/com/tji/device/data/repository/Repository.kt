package com.tji.device.data.repository

import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse

/**
 * 认证数据仓库接口，定义登录、认证和登出相关的方法。
 */
interface AuthRepository {

    suspend fun login(account: String, password: String): ApiResponse<LoginResponse>
    suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit>
    suspend fun logout()
}
