package com.tji.network

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/**
 * 共享 status topic 的轻量消息分类器。
 *
 * 这里只流式读取顶层 `event_type` / `type`，避免高频遥测为了排队优先级先构造一次
 * 完整 JSON 对象，随后业务处理器又重复解析。嵌套同名字段不参与分类。
 */
object MqttStatusMessageClassifier {
    fun isPriority(message: String): Boolean {
        val eventType = readTopLevelEventType(message) ?: return false
        return !eventType.equals("state", ignoreCase = true) &&
            !eventType.equals("status", ignoreCase = true)
    }

    private fun readTopLevelEventType(message: String): String? {
        return runCatching {
            JsonReader(StringReader(message)).use { reader ->
                reader.isLenient = false
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return@use null
                reader.beginObject()
                var eventType: String? = null
                var type: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "event_type" -> eventType = reader.readScalarOrNull()
                        "type" -> type = reader.readScalarOrNull()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) return@use null
                eventType?.trim()?.takeIf { it.isNotEmpty() }
                    ?: type?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun JsonReader.readScalarOrNull(): String? {
        return when (peek()) {
            JsonToken.STRING,
            JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            JsonToken.NULL -> {
                nextNull()
                null
            }
            else -> {
                skipValue()
                null
            }
        }
    }
}
