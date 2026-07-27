package com.tji.device.product.speaker.error

import com.tji.device.error.containsChinese
import com.tji.device.error.rootCauseMessage
import com.tji.device.error.toUserVisibleMessage

fun Throwable.toSpeakerUserVisibleMessage(
    fallback: String = "语音操作失败，请稍后重试"
): String {
    val raw = rootCauseMessage().trim()
    return raw.toSpeakerTechnicalMessage()
        ?: toUserVisibleMessage(fallback)
}

fun String.toSpeakerDeviceMessage(fallback: String = "设备处理失败，请重试"): String {
    val value = trim()
    value.toSpeakerTechnicalMessage()?.let { return it }
    val lower = value.lowercase()
    return when {
        value.isBlank() -> fallback
        "not found" in lower -> "没有找到对应内容"
        "timeout" in lower || "timed out" in lower -> "设备响应超时，请重试"
        "failed" in lower || "error" in lower -> fallback
        value.containsChinese() && !value.hasSpeakerTechnicalTerms() -> value
        else -> fallback
    }
}

private fun String.toSpeakerTechnicalMessage(): String? {
    val lower = lowercase()
    return when {
        isBlank() -> null
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

        "tts" in lower -> "语音生成失败，请重试"

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

        else -> null
    }
}

private fun String.hasSpeakerTechnicalTerms(): Boolean {
    val lower = lowercase()
    return listOf(
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
