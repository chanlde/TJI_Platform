package com.tji.device.wifi

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo as AndroidWifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
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

        val wifiInfo = currentAndroidWifiInfo()
        val isConnected = isWifiConnected()

        return WifiInfo(
            ssid = cleanSSID(wifiInfo?.ssid),
            bssid = wifiInfo?.bssid,
            ipAddress = getIPAddress(),
            macAddress = getMacAddress(),
            rssi = wifiInfo?.rssi ?: 0,
            signalLevel = calculateSignalLevel(wifiInfo?.rssi ?: Int.MIN_VALUE),
            linkSpeed = wifiInfo?.linkSpeed ?: 0,
            frequency = wifiInfo?.frequency ?: 0,
            isConnected = isConnected
        )
    }

    /**
     * 检查 WiFi 是否已连接
     */
    private fun isWifiConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    private fun currentAndroidWifiInfo(): AndroidWifiInfo? {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return capabilities?.transportInfo as? AndroidWifiInfo
        }
        return wifiManager.connectionInfo
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
            Log.w(TAG, "获取 WiFi IP 地址失败", e)
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
        return when (calculateSignalLevel(rssi)) {
            0 -> "很弱"
            1 -> "弱"
            2 -> "一般"
            3 -> "强"
            4 -> "很强"
            else -> "未知"
        }
    }

    @Suppress("DEPRECATION")
    private fun calculateSignalLevel(rssi: Int): Int {
        return WifiManager.calculateSignalLevel(rssi, 5)
    }

    private companion object {
        const val TAG = "WifiData"
    }
}
