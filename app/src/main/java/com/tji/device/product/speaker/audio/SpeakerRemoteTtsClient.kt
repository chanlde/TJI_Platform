package com.tji.device.product.speaker.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SpeakerRemoteTtsClient(
    private val baseUrl: String = SpeakerAudioConfig.Tts.REMOTE_KOKORO_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun synthesizeKokoroPcm8k(
        text: String,
        settings: SpeakerKokoroTtsSettings
    ): ByteArray = withContext(Dispatchers.IO) {
        val normalizedSettings = settings.normalized()
        val payload = JSONObject()
            .put("text", text)
            .put("voice", normalizedSettings.voice.serverName)
            .put("speakerId", normalizedSettings.voice.speakerId)
            .put("speed", normalizedSettings.speed)
            .put("sampleRate", SpeakerAdpcmPacketizer.SAMPLE_RATE)
            .put("format", "pcm16")
            .toString()

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + SpeakerAudioConfig.Tts.REMOTE_KOKORO_SYNTHESIZE_PATH)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val message = bodyBytes.toString(Charsets.UTF_8).take(MAX_ERROR_MESSAGE_CHARS)
                error("云端语音服务器失败 ${response.code}: ${message.ifBlank { response.message }}")
            }
            if (bodyBytes.isEmpty()) error("云端语音服务器返回空音频")
            if (bodyBytes.size % BYTES_PER_PCM16_SAMPLE != 0) {
                error("云端语音返回的PCM长度不对: ${bodyBytes.size}")
            }
            bodyBytes
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val BYTES_PER_PCM16_SAMPLE = 2
        const val MAX_ERROR_MESSAGE_CHARS = 240
    }
}
