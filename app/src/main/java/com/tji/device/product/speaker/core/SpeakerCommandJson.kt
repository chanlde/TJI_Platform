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
        val native = SpeakerCoreNative.buildRecordDownloadCommandJsonOrNull(
            deviceId = deviceId,
            msgId = command.msgId,
            recordId = command.recordId,
            storeTaskId = command.storeTaskId,
            createdAt = command.createdAt,
            name = command.name,
            downloadUrl = command.downloadUrl,
            fileSize = command.fileSize,
            crc32 = command.crc32,
            durationMs = command.durationMs,
            codec = command.codec,
            sampleRate = command.sampleRate,
            channels = command.channels,
            packetMs = command.packetMs,
            frameBytes = command.frameBytes,
            samplesPerFrame = command.samplesPerFrame,
            verifyOnly = command.verifyOnly,
            verifyKind = command.verifyKind.orEmpty(),
            expectedAudioCrc32 = command.expectedAudioCrc32.orEmpty(),
            expectedFirstSamplesJson = JSONArray(command.expectedFirstSamples).toString(),
            temporary = command.temporary,
            visible = command.visible,
            autoPlay = command.autoPlay,
            playbackVolume = command.playbackVolume ?: 0,
            hasPlaybackVolume = command.playbackVolume != null
        )
        return native?.let(::JSONObject) ?: command.toRecordDownloadJson(deviceId)
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
            put("downloadUrl", downloadUrl)
            put("fileSize", fileSize)
            put("crc32", crc32)
            put("durationMs", durationMs)
            put("codec", codec)
            put("sampleRate", sampleRate)
            put("channels", channels)
            put("packetMs", packetMs)
            put("frameBytes", frameBytes)
            put("samplesPerFrame", samplesPerFrame)
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
                put("angle", angle.coerceIn(-90, 90))
            }
            is SpeakerCommand.StartRecordStore -> JSONObject().apply {
                put("recordId", recordId)
                put("storeTaskId", storeTaskId)
                put("createdAt", createdAt)
                put("name", name)
                put("codec", "ima_adpcm")
                put("sampleRate", 8_000)
                put("channels", 1)
                put("packetMs", 40)
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
                put("codec", "ima_adpcm")
                put("sampleRate", 8_000)
                put("channels", 1)
                put("packetMs", 40)
                expectedDurationMs?.let { put("expectedDurationMs", it) }
                expectedFileSize?.let { put("expectedFileSize", it) }
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
