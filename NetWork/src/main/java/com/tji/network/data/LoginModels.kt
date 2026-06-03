package com.tji.network.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class BoundDeviceRow(
    val id: Int? = null,
    val sn: String? = null,
    val sn1: String? = null,
    val serialNumber: String? = null,
    val name: String? = null,
    val deviceName: String? = null,
    val productName: String? = null,
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
    /**
     * 光伏清洗绑定设备；兼容旧版 `["SN"]` 和新版 `[{ id, sn1, productName }]`。
     * 用 JsonElement 是为了避免后端灰度返回对象时 Gson 按 String 解析崩溃。
     */
    @SerializedName("cleansns")
    val cleansns: List<JsonElement>? = null,
    /**
     * 无线电检测绑定设备；服务器返回示例：
     * `[{ "id": 188, "sn1": "1111122223333", "productName": "无线电检测盒子" }]`。
     */
    @SerializedName("radiodetectionsns")
    val radiodetectionsns: List<JsonElement>? = null,
    /** 多产品推荐结构化字段；优先级高于字符串行，避免客户端靠名称推断产品类型。 */
    @SerializedName("boundDevices")
    val boundDevices: List<BoundDeviceRow>? = null,
) {
    /** 优先 [boundDeviceRows]，为空则使用 [bucketsns]。 */
    fun deviceRowsResolved(): List<String> =
        boundDeviceRows.orEmpty().ifEmpty { bucketsns.orEmpty() }

    fun cleanDevicesResolved(): List<BoundDeviceRow> {
        return cleansns.toDeviceRows(
            productType = "SolarClean",
            productCode = "SolarClean"
        )
    }

    fun radioDetectionDevicesResolved(): List<BoundDeviceRow> {
        return radiodetectionsns.toDeviceRows(
            productType = "RadioDetection",
            productCode = "RadioDetection"
        )
    }

    private fun List<JsonElement>?.toDeviceRows(
        productType: String,
        productCode: String
    ): List<BoundDeviceRow> {
        return orEmpty().mapNotNull { item ->
            when {
                item.isJsonPrimitive -> {
                    val serial = item.asString.trim()
                    if (serial.isBlank()) null else BoundDeviceRow(
                        sn = serial,
                        productType = productType,
                        productCode = productCode
                    )
                }

                item.isJsonObject -> {
                    val obj = item.asJsonObject
                    val serial = listOf("sn1", "serialNumber", "sn")
                        .firstNotNullOfOrNull { key ->
                            obj.get(key)?.takeIf { !it.isJsonNull }?.asString?.trim()
                        }
                        .orEmpty()
                    if (serial.isBlank()) null else BoundDeviceRow(
                        id = obj.get("id")?.takeIf { !it.isJsonNull }?.asInt,
                        sn1 = serial,
                        productName = obj.get("productName")?.takeIf { !it.isJsonNull }?.asString?.trim(),
                        productId = obj.get("productId")?.takeIf { !it.isJsonNull }?.asInt,
                        productType = obj.get("productType")?.takeIf { !it.isJsonNull }?.asString?.trim()
                            ?: productType,
                        productCode = obj.get("productCode")?.takeIf { !it.isJsonNull }?.asString?.trim()
                            ?: productCode
                    )
                }

                else -> null
            }
        }
    }
}

fun LoginResponse.mergeWith(other: LoginResponse): LoginResponse {
    return copy(
        id = id ?: other.id,
        token = other.token.ifBlank { token },
        boundDeviceRows = (boundDeviceRows.orEmpty() + other.boundDeviceRows.orEmpty())
            .distinct(),
        bucketsns = (bucketsns.orEmpty() + other.bucketsns.orEmpty())
            .distinct(),
        cleansns = (cleansns.orEmpty() + other.cleansns.orEmpty())
            .distinctBy { it.toString() },
        radiodetectionsns = (radiodetectionsns.orEmpty() + other.radiodetectionsns.orEmpty())
            .distinctBy { it.toString() },
        boundDevices = (boundDevices.orEmpty() + other.boundDevices.orEmpty())
            .distinctBy { row ->
                listOf(row.productType, row.productCode, row.productId, row.sn1, row.serialNumber, row.sn)
                    .joinToString(":")
            }
    )
}
