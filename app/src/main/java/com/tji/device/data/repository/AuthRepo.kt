package com.tji.device.data.repository

import com.tji.network.DataReportManager
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse

/**
 * 真实的认证仓库实现，基于 DataReportManager。
 */
class AuthRepo : AuthRepository {

    override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {

        return DataReportManager.getInstance().login(account, password)
    }

    override suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> {
        return DataReportManager.getInstance().updateDeviceName(id = id, productName = productName)
    }

    override suspend fun logout() {
        // 假设 DataReportManager 提供了 logout 方法
        // 如果 logout 是回调风格的，也可以用 suspendCoroutine 包装
        // 示例：DataReportManager.getInstance().logout()
    }

}
