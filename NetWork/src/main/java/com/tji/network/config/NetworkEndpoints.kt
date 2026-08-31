package com.tji.network.config

import com.tji.network.BuildConfig

/**
 * 当前构建环境注入的 HTTP、固件和 App 更新端点。
 */
object NetworkEndpoints {
    /** Retrofit base URL，必须以 `/` 结尾。 */
    val apiBaseUrl: String = normalizeBaseUrl(BuildConfig.TJI_API_BASE_URL)

    /** 固件下载 base URL，必须以 `/` 结尾。 */
    val otaBaseUrl: String = normalizeBaseUrl(BuildConfig.TJI_OTA_BASE_URL)

}

internal fun normalizeBaseUrl(value: String): String =
    if (value.endsWith("/")) value else "$value/"
