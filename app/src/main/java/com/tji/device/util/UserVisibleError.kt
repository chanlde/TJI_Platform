package com.tji.device.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

fun Throwable.toUserVisibleMessage(fallback: String = "操作失败，请稍后重试"): String {
    val raw = rootMessage().trim()
    val lower = raw.lowercase()
    return when {
        this is UnknownHostException ||
            "unknownhost" in lower ||
            "unable to resolve host" in lower ->
            "网络不可用，无法连接服务器"

        this is SocketTimeoutException ||
            this is TimeoutException ||
            "timeout" in lower ||
            "timed out" in lower ->
            "连接超时，请检查网络后重试"

        this is ConnectException ||
            "connection refused" in lower ||
            "failed to connect" in lower ->
            "连接服务器失败，请检查网络或稍后重试"

        "mqtt not connected" in lower ||
            "not connected" in lower ||
            "realtime publish dropped" in lower ->
            "MQTT 未连接，指令暂时无法发送"

        "already disconnected" in lower ->
            "连接已断开，请稍后重试"

        "not found" in lower ->
            "未找到对应数据"

        raw.isBlank() -> fallback
        raw.any { it in '\u4e00'..'\u9fff' } -> raw
        else -> fallback
    }
}

fun String.toUserVisibleDeviceMessage(fallback: String = "设备反馈异常"): String {
    val value = trim()
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        "not found" in lower -> "未找到对应数据"
        "timeout" in lower || "timed out" in lower -> "设备处理超时"
        "failed" in lower || "error" in lower -> fallback
        value.any { it in '\u4e00'..'\u9fff' } -> value
        else -> fallback
    }
}

fun String?.toUserVisibleServerMessage(fallback: String = "操作失败，请稍后重试"): String {
    val value = this?.trim().orEmpty()
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        value.any { it in '\u4e00'..'\u9fff' } -> value
        "unauthorized" in lower || "forbidden" in lower -> "登录状态已失效，请重新登录"
        "not found" in lower -> "未找到对应数据"
        "timeout" in lower || "timed out" in lower -> "请求超时，请稍后重试"
        "network" in lower || "failed to connect" in lower -> "网络连接失败，请检查网络"
        "internal server error" in lower || "server error" in lower -> "服务器异常，请稍后重试"
        "bad request" in lower -> "请求参数有误，请检查后重试"
        else -> fallback
    }
}

private fun Throwable.rootMessage(): String {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause ?: break
    }
    return current.message ?: message ?: current::class.java.simpleName
}
