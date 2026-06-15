package com.tji.device.product.speaker.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class SpeakerTtsSynthesizer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun synthesizeToPcm(
        text: String,
        voicePreset: SpeakerTtsVoicePreset = SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET,
        targetSampleRate: Int = SpeakerAudioConfig.Tts.DEFAULT_TTS_QUALITY.sampleRate
    ): ByteArray {
        require(targetSampleRate > 0) { "系统 TTS 目标采样率无效: $targetSampleRate" }
        val wavFile = File(appContext.cacheDir, "speaker-tts-${UUID.randomUUID()}.wav")
        return try {
            synthesizeToWav(text, voicePreset, wavFile)
            withContext(Dispatchers.IO) {
                wavFile.readPcm16Mono(targetSampleRate)
            }
        } finally {
            wavFile.delete()
        }
    }

    suspend fun inspectChineseVoices(): SpeakerTtsVoiceInventory {
        val engine = createRawEngine()
        return try {
            val languageResult = withContext(Dispatchers.Main.immediate) {
                engine.setLanguage(SpeakerAudioConfig.Tts.CHINESE_LOCALE)
            }
            val allVoices = engine.voices.orEmpty().toList()
            val chineseVoices = allVoices
                .filter { it.isUsableChineseVoice() }
                .map { it.toSystemVoice() }
                .sortedBy { it.name }
            val availablePresets = SpeakerAudioConfig.Tts.VOICE_PRESETS.filter { preset ->
                preset == SpeakerTtsVoicePreset.Standard ||
                    allVoices.any { voice -> voice.isUsableChineseVoice() && voice.matchesPreset(preset) }
            }.ifEmpty { listOf(SpeakerTtsVoicePreset.Standard) }
            SpeakerTtsVoiceInventory(
                engineName = engine.defaultEngine ?: "unknown",
                languageResult = languageResult,
                allVoiceCount = allVoices.size,
                chineseVoices = chineseVoices,
                availablePresets = availablePresets
            ).also { inventory ->
                logVoiceInventory(inventory, allVoices)
            }
        } finally {
            engine.shutdown()
        }
    }

    private suspend fun synthesizeToWav(
        text: String,
        voicePreset: SpeakerTtsVoicePreset,
        wavFile: File
    ) {
        val locale = text.preferredTtsLocale()
        val engine = createEngine(locale, voicePreset)
        try {
            synthesizeWithEngine(engine, text.normalizedForTts(locale), wavFile)
        } finally {
            engine.shutdown()
        }
    }

    private suspend fun createEngine(locale: Locale, voicePreset: SpeakerTtsVoicePreset): TextToSpeech =
        createRawEngine().also { engine ->
            val languageResult = withContext(Dispatchers.Main.immediate) {
                engine.setLanguage(locale)
            }
            if (
                languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.shutdown()
                throw IllegalStateException("系统 TTS 不支持 ${locale.displayLanguage}")
            }
            withContext(Dispatchers.Main.immediate) {
                engine.applyConfiguredVoice(locale, voicePreset)
                engine.setSpeechRate(voicePreset.speechRate)
                engine.setPitch(voicePreset.pitch)
            }
        }

    private suspend fun createRawEngine(): TextToSpeech =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                lateinit var engine: TextToSpeech
                engine = TextToSpeech(appContext) { status ->
                    if (!continuation.isActive) {
                        engine.shutdown()
                        return@TextToSpeech
                    }
                    if (status == TextToSpeech.SUCCESS) {
                        continuation.resume(engine)
                    } else {
                        continuation.resumeWithException(IllegalStateException("系统 TTS 初始化失败"))
                    }
                }
                continuation.invokeOnCancellation { engine.shutdown() }
            }
        }

    private suspend fun synthesizeWithEngine(engine: TextToSpeech, text: String, wavFile: File) {
        val utteranceId = "speaker-tts-${UUID.randomUUID()}"
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(doneUtteranceId: String?) {
                        if (doneUtteranceId == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(errorUtteranceId: String?) {
                        if (errorUtteranceId == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("TTS 合成失败"))
                        }
                    }

                    override fun onError(errorUtteranceId: String?, errorCode: Int) {
                        if (errorUtteranceId == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("TTS 合成失败: $errorCode"))
                        }
                    }
                })
                val result = engine.synthesizeToFile(
                    text,
                    Bundle(),
                    wavFile,
                    utteranceId
                )
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("TTS 合成启动失败"))
                }
            }
        }
    }
}

private fun String.preferredTtsLocale(): Locale {
    val hasCjk = any { it.code in 0x4E00..0x9FFF }
    val hasLatin = any { it in 'A'..'Z' || it in 'a'..'z' }
    return when {
        hasLatin && !hasCjk -> SpeakerAudioConfig.Tts.ENGLISH_LOCALE
        else -> SpeakerAudioConfig.Tts.CHINESE_LOCALE
    }
}

private fun String.normalizedForTts(locale: Locale): String {
    if (locale.language != Locale.CHINESE.language) return trim()
    val trimmed = trim()
    if (trimmed.isEmpty() || !SpeakerAudioConfig.Tts.NORMALIZE_CHINESE_WHITESPACE) return trimmed

    val out = StringBuilder(trimmed.length)
    var pendingWhitespace = false
    for (ch in trimmed) {
        if (ch.isWhitespace()) {
            pendingWhitespace = true
            continue
        }
        if (pendingWhitespace && out.isNotEmpty()) {
            val previous = out.last()
            if (previous.isCjk() && ch.isCjk()) {
                if (!previous.isChineseSeparator()) out.append(SpeakerAudioConfig.Tts.CHINESE_WHITESPACE_SEPARATOR)
            } else {
                out.append(' ')
            }
        }
        out.append(ch)
        pendingWhitespace = false
    }
    return out.toString()
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF

private fun Char.isChineseSeparator(): Boolean =
    this == '，' || this == '。' || this == '、' || this == '；' || this == '！' || this == '？'

private fun TextToSpeech.applyConfiguredVoice(locale: Locale, voicePreset: SpeakerTtsVoicePreset) {
    val keywords = SpeakerAudioConfig.Tts.PREFERRED_VOICE_NAME_KEYWORDS + voicePreset.voiceNameKeywords
    if (keywords.isEmpty()) return
    val preferredVoice = voices.orEmpty().firstOrNull { voice ->
        voice.locale.language == locale.language &&
            (locale.country.isBlank() || voice.locale.country.isBlank() || voice.locale.country == locale.country) &&
            !voice.features.orEmpty().contains("notInstalled") &&
            keywords.any { keyword -> voice.name.contains(keyword, ignoreCase = true) }
    }
    if (preferredVoice != null) {
        voice = preferredVoice
    }
}

private fun File.readPcm16Mono(targetRate: Int): ByteArray {
    require(targetRate > 0) { "TTS 目标采样率无效: $targetRate" }
    val bytes = readBytes()
    require(bytes.size >= 44) { "TTS 音频为空" }
    require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "TTS 音频格式不是 WAV" }
    require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "TTS 音频格式不是 WAVE" }

    var channels = 1
    var sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
    var bitsPerSample = 16
    var dataOffset = -1
    var dataSize = 0
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
        val chunkSize = bytes.readLe32(offset + 4)
        val chunkData = offset + 8
        if (chunkData + chunkSize > bytes.size) break
        when (chunkId) {
            "fmt " -> {
                val audioFormat = bytes.readLe16(chunkData)
                require(audioFormat == 1) { "TTS WAV 不是 PCM 格式" }
                channels = bytes.readLe16(chunkData + 2).coerceAtLeast(1)
                sampleRate = bytes.readLe32(chunkData + 4).coerceAtLeast(1)
                bitsPerSample = bytes.readLe16(chunkData + 14)
            }
            "data" -> {
                dataOffset = chunkData
                dataSize = chunkSize
                break
            }
        }
        offset = chunkData + chunkSize + (chunkSize and 1)
    }
    require(dataOffset >= 0 && dataSize > 0) { "TTS WAV 没有音频数据" }
    require(bitsPerSample == 16) { "TTS WAV 不是 16bit PCM" }

    val inputFrames = dataSize / (channels * 2)
    if (inputFrames <= 0) return ByteArray(0)
    val samples = ShortArray(inputFrames)
    var cursor = dataOffset
    for (frame in 0 until inputFrames) {
        var mixed = 0
        repeat(channels) {
            mixed += bytes.readLeI16(cursor)
            cursor += 2
        }
        samples[frame] = (mixed / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    val outFrames = (inputFrames.toLong() * targetRate / sampleRate)
        .toInt()
        .coerceAtLeast(1)
    val output = ByteArray(outFrames * 2)
    if (sampleRate == targetRate) {
        for (i in 0 until outFrames) {
            val value = samples[i.coerceAtMost(samples.lastIndex)].toInt()
            output.writeLeI16(i * 2, value)
        }
    } else if (sampleRate > targetRate) {
        val ratio = sampleRate.toFloat() / targetRate.toFloat()
        var sourceIndex = 0
        for (i in 0 until outFrames) {
            val lowerBound = (sourceIndex + 1).coerceAtMost(samples.size)
            val nextIndex = ((i + 1) * ratio).roundToInt().coerceIn(lowerBound, samples.size)
            var sum = 0L
            var count = 0
            while (sourceIndex < nextIndex) {
                sum += samples[sourceIndex].toLong()
                sourceIndex += 1
                count += 1
            }
            val value = if (count > 0) {
                (sum / count).toInt()
            } else {
                samples[sourceIndex.coerceIn(0, samples.lastIndex)].toInt()
            }
            output.writeLeI16(i * 2, value)
        }
    } else {
        for (i in 0 until outFrames) {
            val sourcePos = i.toFloat() * sampleRate.toFloat() / targetRate.toFloat()
            val base = sourcePos.toInt().coerceIn(0, samples.lastIndex)
            val next = (base + 1).coerceAtMost(samples.lastIndex)
            val frac = sourcePos - base
            val value = (samples[base] + (samples[next] - samples[base]) * frac)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.writeLeI16(i * 2, value)
        }
    }
    return output
}

private fun ByteArray.writeLeI16(offset: Int, value: Int) {
    val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    this[offset] = (clamped and 0xFF).toByte()
    this[offset + 1] = ((clamped shr 8) and 0xFF).toByte()
}

private fun ByteArray.readLe16(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

private fun ByteArray.readLeI16(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

private fun ByteArray.readLe32(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

private const val BYTES_PER_PCM16_SAMPLE = 2

data class SpeakerTtsVoiceInventory(
    val engineName: String,
    val languageResult: Int,
    val allVoiceCount: Int,
    val chineseVoices: List<SpeakerTtsSystemVoice>,
    val availablePresets: List<SpeakerTtsVoicePreset>
)

data class SpeakerTtsSystemVoice(
    val name: String,
    val localeTag: String,
    val quality: Int,
    val latency: Int,
    val features: Set<String>
)

private fun Voice.isUsableChineseVoice(): Boolean =
    !features.orEmpty().contains("notInstalled") &&
        (
            locale.language.equals("zh", ignoreCase = true) ||
                locale.language.equals("cmn", ignoreCase = true) ||
                locale.country.equals("CN", ignoreCase = true) ||
                locale.toLanguageTag().contains("Hans", ignoreCase = true)
            )

private fun Voice.matchesPreset(preset: SpeakerTtsVoicePreset): Boolean =
    preset.voiceNameKeywords.any { keyword ->
        name.contains(keyword, ignoreCase = true) ||
            locale.toLanguageTag().contains(keyword, ignoreCase = true) ||
            features.orEmpty().any { feature -> feature.contains(keyword, ignoreCase = true) }
    }

private fun Voice.toSystemVoice(): SpeakerTtsSystemVoice =
    SpeakerTtsSystemVoice(
        name = name,
        localeTag = locale.toLanguageTag(),
        quality = quality,
        latency = latency,
        features = features.orEmpty()
    )

private fun logVoiceInventory(inventory: SpeakerTtsVoiceInventory, allVoices: List<Voice>) {
    Log.d(
        TTS_VOICE_DEBUG_TAG,
        "engine=${inventory.engineName}, languageResult=${inventory.languageResult}, " +
            "allVoiceCount=${inventory.allVoiceCount}, chineseVoiceCount=${inventory.chineseVoices.size}, " +
            "availablePresets=${inventory.availablePresets.joinToString { it.label }}"
    )
    allVoices.sortedBy { it.name }.forEach { voice ->
        Log.d(
            TTS_VOICE_DEBUG_TAG,
            "voice name=${voice.name}, locale=${voice.locale.toLanguageTag()}, " +
                "quality=${voice.quality}, latency=${voice.latency}, features=${voice.features.orEmpty()}"
        )
    }
    SpeakerAudioConfig.Tts.VOICE_PRESETS
        .filter { it != SpeakerTtsVoicePreset.Standard }
        .forEach { preset ->
            val matches = allVoices
                .filter { it.isUsableChineseVoice() && it.matchesPreset(preset) }
                .joinToString { it.name }
                .ifBlank { "none" }
            Log.d(TTS_VOICE_DEBUG_TAG, "preset ${preset.label} matches: $matches")
        }
}

private const val TTS_VOICE_DEBUG_TAG = "SpeakerTtsVoices"
