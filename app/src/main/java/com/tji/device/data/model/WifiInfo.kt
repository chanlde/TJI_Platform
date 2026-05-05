package com.tji.device.data.model

data class WifiInfo(
    val ssid: String? = null,           // 网络名称
    val bssid: String? = null,          // 路由器MAC地址
    val ipAddress: String? = null,      // IP地址
    val macAddress: String? = null,     // 设备MAC地址
    val rssi: Int = 0,                 // 信号强度 (dBm)
    val signalLevel: Int = 0,          // 信号等级 (0-4)
    val linkSpeed: Int = 0,            // 连接速度 (Mbps)
    val frequency: Int = 0,            // 频率 (MHz)
    val isConnected: Boolean = false    // 是否已连接
)