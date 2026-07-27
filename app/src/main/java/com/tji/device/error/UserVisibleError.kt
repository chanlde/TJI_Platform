package com.tji.device.error

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Converts infrastructure and server failures into messages suitable for every
 * product. Product-specific protocol terms belong in that product's error mapper.
 */
fun Throwable.toUserVisibleMessage(fallback: String = "操作失败，请稍后重试"): String {
    val root = rootCause()
    val raw = (root.message ?: message ?: root::class.java.simpleName).trim()
    val lower = raw.lowercase()
    return when {
        root is UnknownHostException ||
            "unknownhost" in lower ||
            "unable to resolve host" in lower ->
            "网络不可用，请检查手机网络"

        root is SocketTimeoutException ||
            root is TimeoutException ||
            "timeout" in lower ||
            "timed out" in lower ->
            "等待时间过长，请稍后重试"

        root is ConnectException ||
            "connection refused" in lower ||
            "failed to connect" in lower ->
            "连接失败，请检查网络后重试"

        "mqtt" in lower ||
            "not connected" in lower ||
            "realtime publish dropped" in lower ->
            "设备暂时未连接，请稍后再试"

        "already disconnected" in lower -> "连接已断开，请稍后重试"
        "not found" in lower -> "没有找到对应内容"
        raw.isBlank() -> fallback
        raw.containsChinese() && !raw.hasInfrastructureTerms() -> raw
        else -> fallback
    }
}

fun String?.toUserVisibleServerMessage(fallback: String = "操作失败，请稍后重试"): String {
    val value = this?.trim().orEmpty()
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        "unauthorized" in lower || "forbidden" in lower -> "登录已过期，请重新登录"
        "not found" in lower -> "没有找到对应内容"
        "timeout" in lower || "timed out" in lower -> "请求超时，请稍后重试"
        "network" in lower || "failed to connect" in lower -> "网络连接失败，请检查网络"
        "internal server error" in lower || "server error" in lower -> "服务暂时不可用，请稍后重试"
        "bad request" in lower -> "请求失败，请重试"
        value.containsChinese() && !value.hasInfrastructureTerms() -> value
        else -> fallback
    }
}

internal fun Throwable.rootCauseMessage(): String {
    val root = rootCause()
    return root.message ?: message ?: root::class.java.simpleName
}

internal fun String.containsChinese(): Boolean =
    any { it in '\u4e00'..'\u9fff' }

private fun Throwable.rootCause(): Throwable {
    var current = this
    val visited = mutableSetOf<Throwable>()
    while (current.cause != null && visited.add(current)) {
        val next = current.cause ?: break
        if (next === current) break
        current = next
    }
    return current
}

private fun String.hasInfrastructureTerms(): Boolean {
    val lower = lowercase()
    return listOf(
        "mqtt",
        "timeout",
        "exception",
        "failed",
        "error",
        "network",
        "connection",
        "http"
    ).any { it in lower }
}
