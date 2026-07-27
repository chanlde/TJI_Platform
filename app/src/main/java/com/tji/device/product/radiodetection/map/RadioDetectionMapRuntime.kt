package com.tji.device.product.radiodetection.map

import android.os.Build
import com.tji.device.BuildConfig

object RadioDetectionMapRuntime {
    // 无地图客户包不携带 AMap SDK，统一显示已有的示意地图，避免运行期类缺失。
    fun shouldUseGaodeMap(): Boolean = BuildConfig.ENABLE_AMAP && !isProbablyEmulator()

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") && device.startsWith("generic") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }
}
