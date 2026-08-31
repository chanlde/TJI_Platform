package com.tji.device.webControl

import java.net.URI

internal object DeveloperWebAccessPolicy {
    private val allowedHosts = setOf("192.168.5.1", "192.168.5.10")

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val uri = URI(url)
            uri.scheme.equals("http", ignoreCase = true) &&
                uri.host in allowedHosts &&
                (uri.port == -1 || uri.port == 80) &&
                uri.userInfo == null
        }.getOrDefault(false)
    }

    fun hostForLog(url: String?): String = runCatching {
        URI(url.orEmpty()).host?.takeIf(allowedHosts::contains) ?: "blocked"
    }.getOrDefault("blocked")
}
