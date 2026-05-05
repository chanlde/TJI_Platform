package com.tji.network.utils


class NetWorkUtils {
    companion object {
        /**
         * HTTPS API 域名（与 Retrofit `DataReportManager` 的 baseUrl 同源）。
         */
        const val serverIp = "api.tjinnovations.cloud"

        /**
         * 必须以 `/` 结尾，便于与 `@GET("/userManager/...")` 等形式的路径正确拼接。
         */
        const val BASE_URL = "https://$serverIp/"

        /**
         * APK 更新包下载（仍走 [serverIp] 的 HTTP 端口）。
         */
        const val port4 = 81

        const val updateUrl = "http://$serverIp:$port4/apks/TJI_Platform.apk"
    }
}

data class loginResult(val success: Boolean, val message: String?)
data class authResult(val success: Boolean, val message: String?)
