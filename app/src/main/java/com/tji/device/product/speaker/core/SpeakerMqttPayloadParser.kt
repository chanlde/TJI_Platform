package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.model.SpeakerAck
import com.tji.device.product.speaker.model.SpeakerAudioDiagnostics
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerRecordEvent
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import org.json.JSONArray
import org.json.JSONObject

object SpeakerMqttPayloadParser {
    fun parseState(serialNumber: String, json: JSONObject, allowOnline: Boolean): SpeakerDeviceState {
        val native = SpeakerCoreNative.parseMqttStateJsonOrNull(serialNumber, json.toString(), allowOnline)
        val parsed = native?.let { JSONObject(it).toState() } ?: parseStateFallback(serialNumber, json, allowOnline)
        return parsed.copy(audio = json.parseAudioDiagnostics() ?: native?.let { JSONObject(it).parseAudioDiagnostics() })
    }

    fun parseAck(json: JSONObject): SpeakerAck {
        val native = SpeakerCoreNative.parseMqttAckJsonOrNull(json.toString())
        return native?.let { JSONObject(it).toAck() } ?: parseAckFallback(json)
    }

    fun parseRecordList(json: JSONObject): ParsedRecordList {
        val native = SpeakerCoreNative.parseMqttRecordListJsonOrNull(json.toString())
        return native?.let { JSONObject(it).toRecordList() } ?: parseRecordListFallback(json)
    }

    fun parseStorageStatus(json: JSONObject): SpeakerStorageStatus {
        val native = SpeakerCoreNative.parseMqttStorageStatusJsonOrNull(json.toString())
        return native?.let { JSONObject(it).toStorageStatus() } ?: parseStorageStatusFallback(json)
    }

    fun parseRecordEvent(eventType: String, json: JSONObject): SpeakerRecordEvent {
        val native = SpeakerCoreNative.parseMqttRecordEventJsonOrNull(eventType, json.toString())
        return native?.let { JSONObject(it).toRecordEvent() } ?: parseRecordEventFallback(eventType, json)
    }

    private fun JSONObject.toState(): SpeakerDeviceState =
        SpeakerDeviceState(
            serialNumber = getString("serialNumber"),
            name = optString("name").ifBlank { null },
            isOnline = optBoolean("isOnline", false),
            playing = optBoolean("playing", false),
            currentFile = optString("currentFile").ifBlank { null },
            volume = optInt("volume", 35).coerceIn(0, 100),
            servoAngle = optNullableInt("servoAngle"),
            lastError = optString("lastError").ifBlank { null },
            network = optString("network").ifBlank { null },
            outputQuality = optString("outputQuality").ifBlank { null },
            audio = parseAudioDiagnostics(),
            timestamp = optNullableLong("timestamp")
        )

    private fun JSONObject.toAck(): SpeakerAck =
        SpeakerAck(
            msgId = optString("msgId"),
            ofType = optString("ofType"),
            ofCmd = optInt("ofCmd", -1),
            ok = optBoolean("ok"),
            code = optInt("code", -1),
            message = optString("message"),
            timestamp = optNullableLong("timestamp")
        )

    private fun JSONObject.toRecordList(): ParsedRecordList =
        ParsedRecordList(
            records = parseRecords(optJSONArray("records")),
            offset = optInt("offset", 0),
            limit = optInt("limit", 8).coerceIn(1, 8),
            total = optInt("total", optJSONArray("records")?.length() ?: 0),
            hasMore = optBoolean("hasMore", false),
            timestamp = optNullableLong("timestamp")
        )

    private fun JSONObject.toStorageStatus(): SpeakerStorageStatus =
        SpeakerStorageStatus(
            ok = optBoolean("ok", true),
            backend = optString("backend").ifBlank { null },
            totalBytes = optLong("totalBytes", 0L),
            freeBytes = optLong("freeBytes", 0L),
            recordCount = optInt("recordCount", 0),
            maxRecords = optInt("maxRecords", 0),
            code = optInt("code", 0),
            message = optString("message"),
            timestamp = optNullableLong("timestamp")
        )

    private fun JSONObject.toRecordEvent(): SpeakerRecordEvent =
        SpeakerRecordEvent(
            type = optString("type"),
            recordId = optString("recordId").ifBlank { null },
            ok = optBoolean("ok", true),
            code = optInt("code", 0),
            message = optString("message"),
            progress = optInt("progress", 0),
            downloadedBytes = optLong("downloadedBytes", 0L),
            totalBytes = optLong("totalBytes", 0L),
            headerSize = optInt("headerSize", 0),
            frameBytes = optInt("frameBytes", 0),
            samplesPerFrame = optInt("samplesPerFrame", 0),
            frameCount = optInt("frameCount", 0),
            audioBytes = optLong("audioBytes", 0L),
            audioCrc32 = optString("audioCrc32").ifBlank { null },
            fileCrc32 = optString("fileCrc32").ifBlank { null },
            firstSamples = optJSONArray("firstSamples").toIntList(),
            name = optString("name").ifBlank { null },
            fileSize = optLong("fileSize", 0L),
            durationMs = optLong("durationMs", 0L),
            codec = optString("codec").ifBlank { "pcm16" },
            sampleRate = optInt("sampleRate", 8_000),
            channels = optInt("channels", 1),
            packetMs = optInt("packetMs", 40),
            crc32 = optString("crc32").ifBlank { null },
            createdAt = optString("createdAt").ifBlank { null },
            path = optString("path").ifBlank { null },
            visible = optBoolean("visible", true),
            storeTaskId = optString("storeTaskId").ifBlank { null },
            timestamp = optNullableLong("timestamp")
        )

    private fun parseStateFallback(serialNumber: String, json: JSONObject, allowOnline: Boolean): SpeakerDeviceState =
        SpeakerDeviceState(
            serialNumber = serialNumber,
            name = json.optString("name").ifBlank { null },
            isOnline = allowOnline,
            playing = json.optBoolean("playing", false),
            currentFile = json.optString("currentFile").ifBlank { null },
            volume = json.optNullableInt("volume")?.coerceIn(0, 100) ?: 35,
            servoAngle = json.optNullableInt("servoAngle"),
            lastError = json.optString("lastError").ifBlank { null },
            network = json.optString("network").ifBlank { null },
            outputQuality = json.optString("outputQuality")
                .ifBlank { json.optString("audioQuality") }
                .ifBlank { json.optString("quality") }
                .ifBlank { null },
            audio = json.parseAudioDiagnostics(),
            timestamp = json.optNullableLong("ts")
        )

    private fun JSONObject.parseAudioDiagnostics(): SpeakerAudioDiagnostics? {
        val audio = optJSONObject("audio") ?: return null
        return SpeakerAudioDiagnostics(
            packets = audio.optLong("packets", 0L),
            lostPackets = audio.optLong("lostPackets", 0L),
            badPackets = audio.optLong("badPackets", 0L),
            bufferedBytes = audio.optInt("bufferedBytes", 0),
            bufferedMinBytes = audio.optInt("bufferedMinBytes", 0),
            bufferedMaxBytes = audio.optInt("bufferedMaxBytes", 0),
            outUnderruns = audio.optLong("outUnderruns", audio.optLong("underruns", 0L)),
            saiErrors = audio.optLong("saiErrors", 0L),
            saiOvr = audio.optLong("saiOvr", 0L),
            dmaHalf = audio.optLong("dmaHalf", 0L),
            dmaFull = audio.optLong("dmaFull", 0L),
            dmaErrors = audio.optLong("dmaErrors", 0L),
            fillLate = audio.optLong("fillLate", 0L),
            peakQ15 = audio.optInt("peakQ15", 0),
            rmsQ15 = audio.optInt("rmsQ15", 0),
            clipCount = audio.optLong("clipCount", 0L),
            limiterCount = audio.optLong("limiterCount", 0L)
        )
    }

    private fun parseAckFallback(json: JSONObject): SpeakerAck =
        SpeakerAck(
            msgId = json.optString("cmdId").ifBlank { json.optString("msgId") },
            ofType = json.optString("ofType").ifBlank { json.optString("cmdName") },
            ofCmd = json.optInt("ofCmd", json.optInt("cmd", -1)),
            ok = json.optBoolean("ok"),
            code = json.optInt("code", -1),
            message = json.optString("msg"),
            timestamp = json.optNullableLong("ts")
        )

    private fun parseRecordListFallback(json: JSONObject): ParsedRecordList {
        val records = parseRecords(json.optJSONArray("items") ?: json.optJSONArray("records"))
        return ParsedRecordList(
            records = records,
            offset = json.optInt("offset", 0),
            limit = json.optInt("limit", 8).coerceIn(1, 8),
            total = json.optInt("total", json.optInt("count", records.size)),
            hasMore = json.optBoolean("hasMore", false),
            timestamp = json.optNullableLong("ts")
        )
    }

    private fun parseStorageStatusFallback(json: JSONObject): SpeakerStorageStatus =
        SpeakerStorageStatus(
            ok = json.optBoolean("ok", true),
            backend = json.optString("backend").ifBlank { null },
            totalBytes = json.optLong("totalBytes", 0L),
            freeBytes = json.optLong("freeBytes", 0L),
            recordCount = json.optInt("recordCount", 0),
            maxRecords = json.optInt("maxRecords", 0),
            code = json.optInt("code", 0),
            message = json.optString("msg"),
            timestamp = json.optNullableLong("ts")
        )

    private fun parseRecordEventFallback(eventType: String, json: JSONObject): SpeakerRecordEvent =
        SpeakerRecordEvent(
            type = eventType,
            recordId = json.optString("recordId").ifBlank { null },
            ok = json.optBoolean("ok", eventType != "record_failed"),
            code = json.optInt("code", 0),
            message = json.optString("msg"),
            progress = json.optInt("progress", 0),
            downloadedBytes = json.optLong("downloadedBytes", 0L),
            totalBytes = json.optLong("totalBytes", 0L),
            headerSize = json.optInt("headerSize", json.optInt("headerBytes", 0)),
            frameBytes = json.optInt("frameBytes", 0),
            samplesPerFrame = json.optInt("samplesPerFrame", 0),
            frameCount = json.optInt("frameCount", 0),
            audioBytes = json.optLong("audioBytes", 0L),
            audioCrc32 = json.optString("audioCrc32").ifBlank { null },
            fileCrc32 = json.optString("fileCrc32").ifBlank { json.optString("crc32").ifBlank { null } },
            firstSamples = json.optJSONArray("firstSamples").toIntList(),
            name = json.optString("name").ifBlank { null },
            fileSize = json.optLong("fileSize", 0L),
            durationMs = json.optLong("durationMs", 0L),
            codec = json.optString("codec").ifBlank { "pcm16" },
            sampleRate = json.optInt("sampleRate", 8_000),
            channels = json.optInt("channels", 1),
            packetMs = json.optInt("packetMs", 40),
            crc32 = json.optString("crc32").ifBlank { null },
            createdAt = json.optString("createdAt").ifBlank { null },
            path = json.optString("path").ifBlank { null },
            visible = json.optBoolean("visible", true),
            storeTaskId = json.optString("storeTaskId").ifBlank { null },
            timestamp = json.optNullableLong("ts")
        )

    private fun parseRecords(array: JSONArray?): List<SpeakerRecord> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val recordId = item.optString("recordId")
                if (recordId.isBlank()) continue
                add(
                    SpeakerRecord(
                        recordId = recordId,
                        name = item.optString("name").ifBlank { recordId },
                        fileSize = item.optLong("fileSize", 0L),
                        durationMs = item.optLong("durationMs", 0L),
                        codec = item.optString("codec").ifBlank { "ima_adpcm" },
                        sampleRate = item.optInt("sampleRate", 8_000),
                        channels = item.optInt("channels", 1),
                        packetMs = item.optInt("packetMs", 40),
                        crc32 = item.optString("crc32").ifBlank { null },
                        createdAt = item.optString("createdAt").ifBlank { null },
                        createdMs = item.optNullableLong("createdMs"),
                        path = item.optString("path").ifBlank { null }
                    )
                )
            }
        }
    }
}

data class ParsedRecordList(
    val records: List<SpeakerRecord>,
    val offset: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean,
    val timestamp: Long?
)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optInt(index))
        }
    }
}
