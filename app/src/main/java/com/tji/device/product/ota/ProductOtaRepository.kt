package com.tji.device.product.ota

import com.tji.network.TjiApiGateway
import com.tji.network.data.OtaLatestResponse
import com.tji.network.data.OtaTaskRequest
import com.tji.network.data.OtaTaskResponse

interface ProductOtaRepository {
    suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse>

    suspend fun getActiveTask(deviceSn: String): Result<OtaTaskResponse?> =
        Result.failure(UnsupportedOperationException("OTA 任务查询不可用"))

    suspend fun reserveTask(request: OtaTaskRequest): Result<OtaTaskResponse> =
        Result.failure(UnsupportedOperationException("OTA 任务预约不可用"))

    suspend fun getTask(taskId: String): Result<OtaTaskResponse> =
        Result.failure(UnsupportedOperationException("OTA 任务查询不可用"))
}

class ProductOtaRepo : ProductOtaRepository {
    override suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse> {
        val response = TjiApiGateway.getOtaLatest(productId = productId, hardwareVersion = hardwareVersion)
        val data = response.data
        return if (response.code == 200 && data != null) {
            Result.success(data)
        } else {
            Result.failure(IllegalStateException(response.message ?: "OTA 版本查询失败"))
        }
    }

    override suspend fun getActiveTask(deviceSn: String): Result<OtaTaskResponse?> =
        TjiApiGateway.getActiveOtaTask(deviceSn)

    override suspend fun reserveTask(request: OtaTaskRequest): Result<OtaTaskResponse> =
        TjiApiGateway.reserveOtaTask(request)

    override suspend fun getTask(taskId: String): Result<OtaTaskResponse> =
        TjiApiGateway.getOtaTask(taskId)
}
