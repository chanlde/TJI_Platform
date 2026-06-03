package com.tji.device.product.radiodetection.model

enum class RadioRgbMode(
    val wireValue: String,
    val label: String
) {
    Steady("steady", "常亮"),
    Breath("breath", "呼吸"),
    Strobe("strobe", "爆闪");

    companion object {
        fun fromWireValue(value: String): RadioRgbMode =
            values().firstOrNull { it.wireValue == value } ?: Steady
    }
}

enum class RadioRgbColor(
    val wireValue: String,
    val label: String
) {
    Red("red", "红色"),
    Green("green", "绿色"),
    Blue("blue", "蓝色"),
    RedBlue("red_blue", "红蓝");

    fun supportedBy(mode: RadioRgbMode): Boolean =
        this != RedBlue || mode == RadioRgbMode.Strobe
}

data class RadioRgbCommand(
    val msgId: String,
    val mode: RadioRgbMode,
    val color: RadioRgbColor,
    val brightness: Int,
    val speed: Int?,
    val save: Boolean
)

data class RadioRgbAck(
    val msgId: String,
    val ok: Boolean,
    val code: Int,
    val message: String,
    val timestamp: Long?
) {
    val statusText: String
        get() = when {
            ok -> message.toRgbSuccessText()
            else -> message.toRgbErrorText(code)
        }

    private fun String.toRgbSuccessText(): String =
        when (this) {
            "rgb preview applied" -> "灯语预览已应用"
            "rgb config saved" -> "默认灯语已保存"
            "rgb config unchanged" -> "灯语配置未变化，已确认"
            else -> ifBlank { "灯语指令已确认" }
        }

    private fun String.toRgbErrorText(errorCode: Int): String =
        when (errorCode) {
            99 -> "设备不支持该 RGB 模块或动作"
            120 -> "灯语参数缺失，请重新选择后发送"
            121 -> "灯语参数非法或超出范围"
            122 -> "设备 Flash 配置读写失败"
            123 -> "设备正在升级，灯语控制暂不可用"
            124 -> "已有保存请求处理中，请稍后再试"
            else -> ifBlank { "灯语指令失败，错误码 $errorCode" }
        }
}

data class RadioRgbCommandFeedback(
    val msgId: String,
    val text: String,
    val pending: Boolean,
    val success: Boolean?
)
