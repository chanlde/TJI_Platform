package com.tji.device.wifi

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.tji.device.data.model.WifiInfo
import java.net.Inet4Address
import java.net.NetworkInterface


class WifiData(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * 获取当前连接的 WiFi 信息
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION])
    fun getCurrentWifiInfo(): WifiInfo {
        if (!wifiManager.isWifiEnabled) {
            return WifiInfo(isConnected = false)
        }

        val wifiInfo = wifiManager.connectionInfo
        val isConnected = isWifiConnected()

        return WifiInfo(
            ssid = cleanSSID(wifiInfo.ssid),
            bssid = wifiInfo.bssid,
            ipAddress = getIPAddress(),
            macAddress = getMacAddress(),
            rssi = wifiInfo.rssi,
            signalLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5),
            linkSpeed = wifiInfo.linkSpeed,
            frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                wifiInfo.frequency
            } else 0,
            isConnected = isConnected
        )
    }

    /**
     * 检查 WiFi 是否已连接
     */
    private fun isWifiConnected(): Boolean {
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI
    }

    /**
     * 清理 SSID（去掉引号）
     */
    private fun cleanSSID(ssid: String?): String? {
        return ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" }
    }

    /**
     * 获取 IP 地址
     */
    private fun getIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 获取设备 MAC 地址
     */
    private fun getMacAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val mac = networkInterface.hardwareAddress
                    if (mac != null) {
                        val macAddress = StringBuilder()
                        for (i in mac.indices) {
                            macAddress.append(String.format("%02X:", mac[i]))
                        }
                        if (macAddress.isNotEmpty()) {
                            macAddress.deleteCharAt(macAddress.length - 1)
                        }
                        return macAddress.toString()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * WiFi 是否可用
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    /**
     * 获取信号强度描述
     */
    fun getSignalLevelDescription(rssi: Int): String {
        return when (WifiManager.calculateSignalLevel(rssi, 5)) {
            0 -> "很弱"
            1 -> "弱"
            2 -> "一般"
            3 -> "强"
            4 -> "很强"
            else -> "未知"
        }
    }
}