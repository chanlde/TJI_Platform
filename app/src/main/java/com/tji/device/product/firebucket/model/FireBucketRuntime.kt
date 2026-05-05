package com.tji.device.product.firebucket.model

import com.tji.device.data.model.ProductType
import com.tji.device.product.runtime.ProductRuntimePayload

/**
 * 消防吊桶 Link 运行时态，由 FireBucket MQTT lifecycle/status 更新。
 * 这是消防吊桶产品内模型，不放公共 data 层，避免其他产品误复用水桶 Switch 结构。
 */
data class FireBucketLinkDevice(
    val event_type: String,
    val serial_number: String,
    val deviceName: String,
    val deviceType: String,
    val manufacturer: String,
    val deviceModel: String,
    val isOnline: Boolean,
    val hwVersion: String,
    val swVersion: String,
    val uptime: Int,
    val deviceConfig: String,
    val subDevices: List<Switch>,
    val timestamp: String,
    val productType: ProductType = ProductType.FireBucket
) : ProductRuntimePayload

data class FireBucketLinkUiState(
    val isLoading: Boolean = false,
    val switchDevices: List<Switch> = emptyList(),
    val errorMessage: String? = null,
    val selectedDevice: FireBucketLinkDevice? = null
)
