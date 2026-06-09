package com.tji.device.product.speaker.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SpeakerRecordUploadClient(
    private val baseUrl: String = SpeakerAudioConfig.RecordStore.REMOTE_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun uploadTempRecord(
        deviceId: String,
        recordId: String,
        name: String,
        hadp: SpeakerHadpFile
    ): SpeakerRecordUploadResult = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("deviceId", deviceId)
            .addFormDataPart("recordId", recordId)
            .addFormDataPart("name", name)
            .addFormDataPart("fileSize", hadp.fileSize.toString())
            .addFormDataPart("crc32", hadp.crc32)
            .addFormDataPart("durationMs", hadp.durationMs.toString())
            .addFormDataPart("codec", "ima_adpcm")
            .addFormDataPart("sampleRate", SpeakerAdpcmPacketizer.SAMPLE_RATE.toString())
            .addFormDataPart("channels", SpeakerAdpcmPacketizer.CHANNELS.toString())
            .addFormDataPart("packetMs", SpeakerAdpcmPacketizer.PACKET_MS.toString())
            .addFormDataPart(
                "file",
                "$recordId.hadp",
                hadp.data.toRequestBody(HADP_MEDIA_TYPE)
            )
            .build()

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + SpeakerAudioConfig.RecordStore.UPLOAD_TEMP_PATH)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("录音上传失败 ${response.code}: ${body.ifBlank { response.message }}")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                error("录音上传失败: ${json.optString("detail").ifBlank { body }}")
            }
            SpeakerRecordUploadResult(
                recordId = json.optString("recordId", recordId),
                downloadUrl = json.optString("downloadUrl"),
                fileSize = json.optLong("fileSize", hadp.fileSize.toLong()),
                crc32 = json.optString("crc32", hadp.crc32),
                durationMs = json.optInt("durationMs", hadp.durationMs),
                expiresAt = json.optLong("expiresAt", 0L)
            ).also {
                require(it.downloadUrl.isNotBlank()) { "服务器未返回下载链接" }
            }
        }
    }

    private companion object {
        val HADP_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

data class SpeakerRecordUploadResult(
    val recordId: String,
    val downloadUrl: String,
    val fileSize: Long,
    val crc32: String,
    val durationMs: Int,
    val expiresAt: Long
)

