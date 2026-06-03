package com.tji.device.product.radiodetection.mqtt

import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbMode
import org.json.JSONObject

object RadioRgbAckParser {
    fun parse(message: String): RadioRgbAck? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("{")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            if (
                json.optString("type") != "ack" ||
                json.optString("module") != "rgb" ||
                json.optString("action") != "set"
            ) {
                return null
            }

            RadioRgbAck(
                msgId = json.optString("msgId"),
                ok = json.optBoolean("ok", false),
                code = json.optInt("code", -1),
                message = json.optString("msg"),
                timestamp = if (json.has("ts")) json.optLong("ts") else null
            )
        }.getOrNull()
    }
}

fun RadioRgbCommand.toMqttJson(): JSONObject =
    JSONObject().apply {
        put("v", 1)
        put("msgId", msgId)
        put("module", "rgb")
        put("action", "set")
        put("mode", mode.wireValue)
        put("color", color.wireValue)
        put("brightness", brightness.coerceIn(0, 100))
        if (mode == RadioRgbMode.Breath) {
            put("speed", speed?.coerceIn(0, 100) ?: 50)
        }
        put("save", save)
    }
