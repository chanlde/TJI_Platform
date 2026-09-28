package com.tji.device.product.firegun.model

import com.tji.device.product.runtime.ProductRuntimePayload

/** Link 转发的下方喷枪实时状态。 */
data class FireGunDeviceStatus(
    val linkSerial: String,
    val serialNumber: String,
    val deviceName: String,
    val deviceType: String,
    val isOnline: Boolean,
    val currentAngle: Double?,
    val currentCurrent: Double?,
    val inputVoltage: Double?,
    val servoMinAngle: Double?,
    val servoMaxAngle: Double?,
    val uptime: Long?,
    val timestamp: Long?,
    val rssi: Int?,
    val lockState: String?,
    val actuatorState: String?
) : ProductRuntimePayload

/** 消防喷枪是一台 Link 对应一台下方设备。 */
data class FireGunLinkState(
    val serialNumber: String,
    val name: String,
    val isOnline: Boolean,
    val hwVersion: String?,
    val swVersion: String?,
    val uptime: Long?,
    val timestamp: Long?,
    val deviceStatus: FireGunDeviceStatus?
) : ProductRuntimePayload

data class FireGunLinkHeartbeat(
    val serialNumber: String,
    val isOnline: Boolean,
    val uptime: Long?,
    val timestamp: Long?
)
