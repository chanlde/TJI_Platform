package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.model.SpeakerCommand
import org.json.JSONArray
import org.json.JSONObject

object SpeakerCommandJson {
    fun encode(
        command: SpeakerCommand,
        deviceId: String,
        timestampMs: Long = System.currentTimeMillis()
    ): JSONObject =
        when (command) {
            is SpeakerCommand.RecordDownload -> encodeRecordDownload(command, deviceId)
            else -> encodeStandard(command, deviceId, timestampMs)
        }

    private fun encodeRecordDownload(command: SpeakerCommand.RecordDownload, deviceId: String): JSONObject {
        return command.toRecordDownloadJson(deviceId)
    }

    private fun encodeStandard(command: SpeakerCommand, deviceId: String, timestampMs: Long): JSONObject {
        val native = SpeakerCoreNative.buildStandardCommandJsonOrNull(
            deviceId = deviceId,
            msgId = command.msgId,
            commandCode = command.code,
            commandName = command.commandName,
            timestampMs = timestampMs,
            paramsJson = command.paramsJsonOrEmpty(),
            extraJson = command.extraFieldsJsonOrEmpty()
        )
        return native?.let(::JSONObject) ?: command.toStandardJson(deviceId, timestampMs)
    }

    private fun SpeakerCommand.RecordDownload.toRecordDownloadJson(deviceId: String): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("deviceId", deviceId)
            put("cmdId", msgId)
            put("cmdName", commandName)
            put("recordId", recordId)
            put("storeTaskId", storeTaskId)
            put("createdAt", createdAt)
            put("name", name)
            put("recordType", recordType)
            put("downloadUrl", downloadUrl)
            put("fileSize", fileSize)
            put("crc32", crc32)
            put("durationMs", durationMs)
            put("container", container)
            put("codec", codec)
            put("sampleRate", sampleRate)
            put("channels", channels)
            put("packetMs", packetMs)
            put("bitrate", bitrate)
            if (temporary) {
                put("temporary", true)
                put("visible", visible)
                put("autoPlay", autoPlay)
                playbackVolume?.let { put("playbackVolume", it.coerceIn(0, 100)) }
            }
            if (verifyOnly) {
                put("verifyOnly", true)
                verifyKind?.let { put("verifyKind", it) }
                expectedAudioCrc32?.let { put("expectedAudioCrc32", it) }
                put("expectedFirstSamples", JSONArray(expectedFirstSamples))
            }
        }

    private fun SpeakerCommand.toStandardJson(deviceId: String, timestampMs: Long): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("deviceId", deviceId)
            put("cmdId", msgId)
            put("msgId", msgId)
            put("ts", timestampMs)
            put("cmd", code)
            put("cmdName", commandName)
            extraFields().forEach { (key, value) -> put(key, value) }
            paramsJsonOrNull()?.let { put("params", it) }
        }

    private fun SpeakerCommand.paramsJsonOrEmpty(): String =
        paramsJsonOrNull()?.toString().orEmpty()

    private fun SpeakerCommand.paramsJsonOrNull(): JSONObject? =
        when (this) {
            is SpeakerCommand.SpeakText -> JSONObject().apply {
                put("text", text)
                put("volume", volume.coerceIn(0, 100))
            }
            is SpeakerCommand.PrepareText -> JSONObject().apply {
                put("text", text)
            }
            is SpeakerCommand.PlayFile -> JSONObject().apply {
                put("file", file)
                put("volume", volume.coerceIn(0, 100))
            }
            is SpeakerCommand.SetVolume -> JSONObject().apply {
                put("volume", volume.coerceIn(0, 100))
            }
            is SpeakerCommand.SetAudioQuality -> JSONObject().apply {
                put("quality", quality)
                put("audioQuality", quality)
                put("sampleRate", sampleRate)
                put("packetMs", packetMs)
                put("frameBytes", frameBytes)
                put("samplesPerFrame", samplesPerFrame)
            }
            is SpeakerCommand.SetServoAngle -> JSONObject().apply {
                put("angle", angle.coerceIn(0, 180))
                put("speedDps", speedDps.coerceIn(1, 360))
            }
            is SpeakerCommand.ServoSweepTest -> JSONObject().apply {
                val min = minAngle.coerceIn(0, 180)
                val max = maxAngle.coerceIn(0, 180).coerceAtLeast(min + 1)
                put("minAngle", min)
                put("maxAngle", max.coerceAtMost(180))
                put("speedDps", speedDps.coerceIn(1, 360))
                put("cycles", cycles.coerceIn(0, 100))
                put("durationMs", durationMs.coerceIn(0, 5_000))
            }
            is SpeakerCommand.ServoStepTest -> JSONObject().apply {
                val min = minAngle.coerceIn(0, 180)
                val max = maxAngle.coerceIn(0, 180).coerceAtLeast(min + 1).coerceAtMost(180)
                put("minAngle", min)
                put("maxAngle", max)
                put("stepAngle", stepAngle.coerceIn(1, max - min))
                put("speedDps", speedDps.coerceIn(1, 360))
                put("intervalMs", intervalMs.coerceIn(20, 60_000))
            }
            is SpeakerCommand.SetMcuMicrophoneFeedback -> JSONObject().apply {
                put("enabled", if (enabled) 1 else 0)
                if (enabled) {
                    put("sessionId", sessionId)
                    put("talkId", talkId)
                    put("codec", codec)
                    put("sampleRate", sampleRate)
                    put("channels", channels)
                    put("packetMs", packetMs)
                    put("ttlMs", ttlMs.coerceIn(1_000L, 300_000L))
                }
            }
            is SpeakerCommand.StartRecordStore -> JSONObject().apply {
                put("recordId", recordId)
                put("storeTaskId", storeTaskId)
                put("createdAt", createdAt)
                put("name", name)
                put("codec", "opus")
                put("sampleRate", 16_000)
                put("channels", 1)
                put("packetMs", 20)
                expectedDurationMs?.let { put("expectedDurationMs", it) }
                expectedFileSize?.let { put("expectedFileSize", it) }
            }
            is SpeakerCommand.PlayRecord -> JSONObject().apply {
                put("recordId", recordId)
                put("volume", volume.coerceIn(0, 100))
            }
            is SpeakerCommand.DeleteRecord -> JSONObject().apply {
                put("recordId", recordId)
            }
            is SpeakerCommand.UpdateRecord -> JSONObject().apply {
                put("recordId", recordId)
                put("name", name)
            }
            is SpeakerCommand.GetStatus,
            is SpeakerCommand.GetStorageStatus,
            is SpeakerCommand.ListRecords,
            is SpeakerCommand.RecordDownload,
            is SpeakerCommand.Stop -> null
        }

    private fun SpeakerCommand.extraFieldsJsonOrEmpty(): String =
        extraFields().takeIf { it.isNotEmpty() }
            ?.entries
            ?.joinToString(",") { (key, value) ->
                "\"${escapeJson(key)}\":${value.toJsonLiteral()}"
            }
            .orEmpty()

    private fun SpeakerCommand.extraFields(): Map<String, Any> =
        when (this) {
            is SpeakerCommand.SetAudioQuality -> linkedMapOf(
                "quality" to quality,
                "audioQuality" to quality,
                "sampleRate" to sampleRate,
                "packetMs" to packetMs,
                "frameBytes" to frameBytes,
                "samplesPerFrame" to samplesPerFrame
            )
            is SpeakerCommand.StartRecordStore -> buildMap {
                put("recordId", recordId)
                put("storeTaskId", storeTaskId)
                put("createdAt", createdAt)
                put("name", name)
                put("codec", "opus")
                put("sampleRate", 16_000)
                put("channels", 1)
                put("packetMs", 20)
                expectedDurationMs?.let { put("expectedDurationMs", it) }
                expectedFileSize?.let { put("expectedFileSize", it) }
            }
            is SpeakerCommand.SetMcuMicrophoneFeedback -> buildMap {
                put("enabled", if (enabled) 1 else 0)
                if (enabled) {
                    put("sessionId", sessionId)
                    put("talkId", talkId)
                    put("codec", codec)
                    put("sampleRate", sampleRate)
                    put("channels", channels)
                    put("packetMs", packetMs)
                    put("ttlMs", ttlMs.coerceIn(1_000L, 300_000L))
                }
            }
            is SpeakerCommand.PlayRecord -> linkedMapOf(
                "recordId" to recordId,
                "volume" to volume.coerceIn(0, 100)
            )
            is SpeakerCommand.ListRecords -> linkedMapOf(
                "offset" to offset.coerceAtLeast(0),
                "limit" to limit.coerceIn(1, 4),
                "order" to order
            )
            is SpeakerCommand.DeleteRecord -> linkedMapOf("recordId" to recordId)
            is SpeakerCommand.UpdateRecord -> linkedMapOf("recordId" to recordId, "name" to name)
            is SpeakerCommand.SetServoAngle -> linkedMapOf(
                "angle" to angle.coerceIn(0, 180),
                "speedDps" to speedDps.coerceIn(1, 360)
            )
            is SpeakerCommand.ServoSweepTest -> buildMap {
                val min = minAngle.coerceIn(0, 180)
                val max = maxAngle.coerceIn(0, 180).coerceAtLeast(min + 1).coerceAtMost(180)
                put("minAngle", min)
                put("maxAngle", max)
                put("speedDps", speedDps.coerceIn(1, 360))
                put("cycles", cycles.coerceIn(0, 100))
                put("durationMs", durationMs.coerceIn(0, 5_000))
            }
            is SpeakerCommand.ServoStepTest -> buildMap {
                val min = minAngle.coerceIn(0, 180)
                val max = maxAngle.coerceIn(0, 180).coerceAtLeast(min + 1).coerceAtMost(180)
                put("minAngle", min)
                put("maxAngle", max)
                put("stepAngle", stepAngle.coerceIn(1, max - min))
                put("speedDps", speedDps.coerceIn(1, 360))
                put("intervalMs", intervalMs.coerceIn(20, 60_000))
            }
            else -> emptyMap()
        }

    private fun Any.toJsonLiteral(): String =
        when (this) {
            is Number, is Boolean -> toString()
            else -> "\"${escapeJson(toString())}\""
        }

    private fun escapeJson(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
}
