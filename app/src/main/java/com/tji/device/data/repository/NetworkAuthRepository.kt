package com.tji.device.data.repository

import com.tji.device.data.model.ProductCatalog
import com.tji.network.TjiApiGateway
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse

/**
 * 基于平台 REST API 的认证仓库实现。
 */
class NetworkAuthRepository : AuthRepository {

    override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {

        return TjiApiGateway.login(
            account = account,
            password = password,
            productIds = ProductCatalog.definitions.map { it.productId }
        ).also { response ->
            TjiApiGateway.authToken = response.data?.token?.takeIf { it.isNotBlank() }
        }
    }

    override suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> {
        return TjiApiGateway.updateDeviceName(id = id, productName = productName)
    }

    override suspend fun logout() {
        TjiApiGateway.clearAuthToken()
    }

}
