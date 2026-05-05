package com.tji.device.product.solarclean.repository

import com.tji.network.DataReportManager
import com.tji.network.data.OtaLatestResponse

interface SolarCleanOtaRepository {
    suspend fun getLatestFirmware(productType: String): Result<OtaLatestResponse>
}

class SolarCleanOtaRepo : SolarCleanOtaRepository {
    override suspend fun getLatestFirmware(productType: String): Result<OtaLatestResponse> {
        val response = DataReportManager.getInstance().getOtaLatest(productType = productType)
        val data = response.data
        return if (response.code == 200 && data != null) {
            Result.success(data)
        } else {
            Result.failure(IllegalStateException(response.message ?: "OTA 版本查询失败"))
        }
    }
}
