package com.tji.device.data.repository

import android.util.Log
import com.tji.network.DataReportManager
import com.tji.network.data.*

/**
 * 真实的认证仓库实现，基于 DataReportManager。
 */
class AuthRepo : AuthRepository {

    override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {

        return DataReportManager.getInstance().login(account, password)
    }

    override suspend fun logout() {
        // 假设 DataReportManager 提供了 logout 方法
        // 如果 logout 是回调风格的，也可以用 suspendCoroutine 包装
        // 示例：DataReportManager.getInstance().logout()
    }

}