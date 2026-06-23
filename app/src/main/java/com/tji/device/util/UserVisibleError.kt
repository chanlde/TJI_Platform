package com.tji.device.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

fun Throwable.toUserVisibleMessage(fallback: String = "操作失败，请稍后重试"): String {
    val raw = rootMessage().trim()
    val mapped = raw.toFriendlyTechnicalMessage()
    if (mapped != null) return mapped
    val lower = raw.lowercase()
    return when {
        this is UnknownHostException ||
            "unknownhost" in lower ||
            "unable to resolve host" in lower ->
            "网络不可用，请检查手机网络"

        this is SocketTimeoutException ||
            this is TimeoutException ||
            "timeout" in lower ||
            "timed out" in lower ->
            "等待时间过长，请稍后重试"

        this is ConnectException ||
            "connection refused" in lower ||
            "failed to connect" in lower ->
            "连接失败，请检查网络后重试"

        "already disconnected" in lower -> "连接已断开，请稍后重试"
        "not found" in lower -> "没有找到对应内容"
        raw.isBlank() -> fallback
        raw.any { it in '\u4e00'..'\u9fff' } && !raw.hasTechnicalWords() -> raw
        else -> fallback
    }
}

fun String.toUserVisibleDeviceMessage(fallback: String = "设备处理失败，请重试"): String {
    val value = trim()
    val mapped = value.toFriendlyTechnicalMessage()
    if (mapped != null) return mapped
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        "not found" in lower -> "没有找到对应内容"
        "timeout" in lower || "timed out" in lower -> "设备响应超时，请重试"
        "failed" in lower || "error" in lower -> fallback
        value.any { it in '\u4e00'..'\u9fff' } && !value.hasTechnicalWords() -> value
        else -> fallback
    }
}

fun String?.toUserVisibleServerMessage(fallback: String = "操作失败，请稍后重试"): String {
    val value = this?.trim().orEmpty()
    val mapped = value.toFriendlyTechnicalMessage()
    if (mapped != null) return mapped
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        "unauthorized" in lower || "forbidden" in lower -> "登录已过期，请重新登录"
        "not found" in lower -> "没有找到对应内容"
        "timeout" in lower || "timed out" in lower -> "请求超时，请稍后重试"
        "network" in lower || "failed to connect" in lower -> "网络连接失败，请检查网络"
        "internal server error" in lower || "server error" in lower -> "服务暂时不可用，请稍后重试"
        "bad request" in lower -> "请求失败，请重试"
        value.any { it in '\u4e00'..'\u9fff' } && !value.hasTechnicalWords() -> value
        else -> fallback
    }
}

private fun String.toFriendlyTechnicalMessage(): String? {
    val lower = lowercase()
    return when {
        isBlank() -> null
        "mqtt" in lower ||
            "not connected" in lower ||
            "realtime publish dropped" in lower ->
            "设备暂时未连接，请稍后再试"

        "kokoro" in lower ||
            "onnx" in lower ||
            "sherpa" in lower ||
            "model.onnx" in lower ||
            "voices.bin" in lower ||
            "tokens.txt" in lower ||
            "espeak" in lower ||
            "模型" in this ||
            "资源目录" in this ->
            "语音包不完整，请安装完整版本后再试"

        "tts" in lower ->
            "语音生成失败，请重试"

        "record store active" in lower ||
            "busy" in lower ||
            "正在处理上一段" in this ->
            "设备正在处理上一段语音，请稍后再试"

        "temporary file too large" in lower ||
            "too large" in lower ||
            "413" in lower ->
            "语音太长，请分段发送"

        "crc" in lower ||
            "checksum" in lower ||
            "filesize mismatch" in lower ||
            "size mismatch" in lower ||
            "bad json" in lower ||
            "bad arg" in lower ||
            "invalid" in lower ||
            "unsupported" in lower ||
            "audio metadata" in lower ||
            "采样率" in this ||
            "重采样" in this ||
            "声道" in this ||
            "文件头" in this ||
            "魔术头" in this ||
            "帧" in this ||
            "格式" in this ||
            "音频长度" in this ->
            "语音文件处理失败，请重试"

        "download" in lower ||
            "upload" in lower ->
            "语音发送失败，请检查网络后重试"

        "storage" in lower ||
            "mount" in lower ||
            "filesystem" in lower ||
            "fatfs" in lower ||
            "nand" in lower ||
            "sdnand" in lower ->
            "设备存储暂时不可用，请稍后重试"

        "udp" in lower ||
            "sai" in lower ||
            "i2s" in lower ||
            "pcm" in lower ||
            "adpcm" in lower ||
            "hadp" in lower ||
            "buffer" in lower ->
            "语音播放失败，请重试"

        "timeout" in lower ||
            "timed out" in lower ->
            "等待时间过长，请稍后重试"

        "failed" in lower ||
            "error" in lower ||
            "exception" in lower ->
            "操作失败，请稍后重试"

        else -> null
    }
}

private fun String.hasTechnicalWords(): Boolean {
    val lower = lowercase()
    return listOf(
        "mqtt",
        "tts",
        "kokoro",
        "onnx",
        "sherpa",
        "hadp",
        "adpcm",
        "pcm",
        "udp",
        "sai",
        "i2s",
        "crc",
        "record store",
        "storage",
        "download",
        "upload",
        "buffer",
        "ram://",
        "temporary",
        "nand",
        "sdnand",
        "model.onnx",
        "voices.bin",
        "tokens.txt",
        "采样率",
        "重采样",
        "声道",
        "文件头",
        "魔术头",
        "格式",
        "帧长度"
    ).any { it in lower } ||
        "资源目录" in this ||
        "技术" in this
}

private fun Throwable.rootMessage(): String {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause ?: break
    }
    return current.message ?: message ?: current::class.java.simpleName
}
