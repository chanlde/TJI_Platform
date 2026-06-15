package com.tji.device.product.solarclean.ui.control

import com.tji.device.product.solarclean.model.SolarCleanDeviceState

internal fun waterLevelText(value: Int): String {
    return when (value) {
        0 -> "低"
        1 -> "正常"
        2 -> "高"
        else -> value.toString()
    }
}

internal fun mqttStatusText(state: SolarCleanDeviceState?): String {
    return when (state?.mqttConnected) {
        true -> "正常"
        false -> state.mqttLastError?.let { "错误 $it" } ?: "断开"
        null -> "--"
    }
}
