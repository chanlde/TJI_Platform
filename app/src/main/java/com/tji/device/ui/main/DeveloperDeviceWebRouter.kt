package com.tji.device.ui.main

/**
 * 根据当前连接的设备 Wi-Fi，选择开发者调试页面。
 *
 * 无法识别 SSID 时使用 Switch 页面，避免把未知网络误判为 Link 设备。
 */
internal object DeveloperDeviceWebRouter {
    private const val LINK_NETWORK_NAME = "HydroLink"
    private const val LINK_DEVICE_URL = "http://192.168.5.1/index.html"
    private const val SWITCH_DEVICE_URL = "http://192.168.5.10/index.html"

    fun urlForSsid(ssid: String?): String =
        if (ssid?.contains(LINK_NETWORK_NAME, ignoreCase = true) == true) {
            LINK_DEVICE_URL
        } else {
            SWITCH_DEVICE_URL
        }
}
