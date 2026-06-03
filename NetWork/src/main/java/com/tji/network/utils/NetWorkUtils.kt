package com.tji.network.utils

import com.tji.network.BuildConfig


class NetWorkUtils {
    companion object {
        /**
         * HTTPS API 域名（与 Retrofit `DataReportManager` 的 baseUrl 同源）。
         */
        val serverIp: String = BuildConfig.TJI_API_BASE_URL
        val otaIp: String = BuildConfig.TJI_OTA_BASE_URL

        /**
         * 必须以 `/` 结尾，便于与 `@GET("/userManager/...")` 等形式的路径正确拼接。
         */
        val BASE_URL: String = ensureTrailingSlash(BuildConfig.TJI_API_BASE_URL)
        val OTA_URL: String = ensureTrailingSlash(BuildConfig.TJI_OTA_BASE_URL)

        /**
         * APK 更新包下载。默认仍指向线上旧地址；本地联调可通过 Gradle 属性覆盖。
         */
        const val port4 = 81

        val updateUrl: String = BuildConfig.TJI_UPDATE_URL

        private fun ensureTrailingSlash(value: String): String =
            if (value.endsWith("/")) value else "$value/"
    }
}
