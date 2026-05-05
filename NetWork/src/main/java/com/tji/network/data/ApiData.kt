package com.tji.network.data

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)
data class DataItem(
    val id: Int,                 // 唯一标识
    val userId: Int,             // 用户 ID
    val sn: String,              // SN 码
    val sn2: String?,            // 第二个 SN 码（可为 null）
    val sn3: String?,            // 第三个 SN 码（可为 null）
    val typeCode: Int,           // 类型代码
    val state: Int,              // 状态
    val validityDateTime: String, // 有效日期时间
    val password: String?,       // 密码（可为 null）
    val productId: Int           // 产品 ID
)

data class UserInfo(
    val id: String?,
    val account: String?,
    val name: String?
)

data class ProductRelation(
    val sn: String?
)

data class BoundDeviceRow(
    val sn: String? = null,
    val serialNumber: String? = null,
    val name: String? = null,
    val deviceName: String? = null,
    val productId: Int? = null,
    val productType: String? = null,
    val productCode: String? = null
)

data class LoginResponse(
    val id: String? = null,
    val token: String,
    /**
     * 推荐 JSON 字段（多产品中性命名）。格式：`["SN,Name", ...]`，与 [bucketsns] 相同形态。
     */
    @SerializedName("boundDeviceRows")
    val boundDeviceRows: List<String>? = null,
    /** 旧字段名；若仅返回此项，[deviceRowsResolved] 仍会使用该列表。 */
    @SerializedName("bucketsns")
    val bucketsns: List<String>? = null,
    /** 光伏清洗 SN 列表；后端新字段，格式可为 `["SN"]` 或 `["SN,Name"]`。 */
    @SerializedName("cleansns")
    val cleansns: List<String>? = null,
    /** 多产品推荐结构化字段；优先级高于字符串行，避免客户端靠名称推断产品类型。 */
    @SerializedName("boundDevices")
    val boundDevices: List<BoundDeviceRow>? = null,
) {
    /** 优先 [boundDeviceRows]，为空则使用 [bucketsns]。 */
    fun deviceRowsResolved(): List<String> =
        boundDeviceRows.orEmpty().ifEmpty { bucketsns.orEmpty() }
}

data class AppVersion(
    val version: String?,
    val innerVersion: Int?
)

data class OtaLatestResponse(
    @SerializedName("has_update")
    val hasUpdate: Boolean? = null,
    @SerializedName(value = "latest_version", alternate = ["version"])
    val latestVersion: String? = null,
    @SerializedName(value = "hardware_version", alternate = ["hardware"])
    val hardwareVersion: String? = null,
    @SerializedName(value = "file_size", alternate = ["size"])
    val fileSize: Long? = null,
    val sha256: String? = null,
    val signature: String? = null,
    val force: Boolean? = null,
    @SerializedName("min_battery")
    val minBattery: Int? = null,
    @SerializedName(value = "download_url", alternate = ["downloadUrl"])
    val downloadUrl: String? = null,
    @SerializedName(value = "release_note", alternate = ["releaseNote"])
    val releaseNote: String? = null
)
