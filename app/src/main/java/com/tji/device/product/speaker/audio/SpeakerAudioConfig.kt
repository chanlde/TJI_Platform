package com.tji.device.product.speaker.audio

import com.tji.device.BuildConfig
import java.util.Locale

object SpeakerAudioConfig {
    object Relay {
        // UDP relay server used by the 4G speaker audio path.
        val HOST: String = BuildConfig.TJI_SPEAKER_RELAY_HOST

        // MCU/relay UDP audio and control port.
        val PORT: Int = BuildConfig.TJI_SPEAKER_RELAY_PORT

    }

    object Gain {
        // Initial output gain shown by the App, 1.0 means maximum web-compatible gain.
        const val DEFAULT_OUTPUT_GAIN = 1f

        // Upper bound for output gain control. Keep at 1.0 unless MCU gain mapping changes.
        const val MAX_OUTPUT_GAIN = 1f

        // MCU monitor is an open acoustic loop (phone speaker -> MCU mic).
        // Keep its fixed playback gain below the loop-gain threshold.
        const val MCU_MONITOR_OUTPUT_GAIN = 0.35f

    }

    object Tts {
        // Locale used when the text contains Chinese characters.
        val CHINESE_LOCALE: Locale = Locale.CHINA

        // Locale used when the text is pure English/Latin.
        val ENGLISH_LOCALE: Locale = Locale.US

        // Android system TTS speech speed. 1.0 is the engine default.
        const val SPEECH_RATE = 1.0f

        // Android system TTS pitch. 1.0 is the engine default.
        const val PITCH = 1.0f

        // Default voice style used by the TTS panel.
        val DEFAULT_VOICE_PRESET: SpeakerTtsVoicePreset = SpeakerTtsVoicePreset.Standard

        // Voice styles shown in the App. Real voice matching depends on the phone's installed TTS engine.
        val VOICE_PRESETS: List<SpeakerTtsVoicePreset> = listOf(
            SpeakerTtsVoicePreset.Standard,
            SpeakerTtsVoicePreset.Female,
            SpeakerTtsVoicePreset.Male,
            SpeakerTtsVoicePreset.Broadcast,
            SpeakerTtsVoicePreset.Alert
        )

        // TTS sends the first packets without 40 ms spacing to prefill the MCU buffer.
        const val PREBUFFER_PACKETS = 4

        // Silence before synthesized speech so the MCU buffer is ready before the first word.
        const val LEADING_SILENCE_MS = 160

        // Converts whitespace between Chinese characters into punctuation to avoid odd word splits.
        const val NORMALIZE_CHINESE_WHITESPACE = true

        // Separator inserted when Chinese whitespace normalization is enabled.
        const val CHINESE_WHITESPACE_SEPARATOR = '，'

        // Optional voice-name keywords. Empty means use the phone system's default TTS voice.
        val PREFERRED_VOICE_NAME_KEYWORDS: List<String> = emptyList()

        // Customer-facing default voice quality. UI labels are intentionally simple:
        // low/medium/high instead of sample-rate jargon.
        val DEFAULT_TTS_QUALITY: SpeakerAudioQuality = SpeakerAudioQuality.High

        // Number of synthesized TTS PCM clips kept in memory to avoid repeated cloud/system synthesis.
        const val PCM_CACHE_MAX_ITEMS = 8

        // Total retained PCM budget. Long text can produce multi-megabyte byte arrays,
        // so item count alone is not a safe memory bound.
        const val PCM_CACHE_MAX_BYTES = 16L * 1024L * 1024L

        // Oversized clips can still be played once but are not retained in memory.
        const val PCM_CACHE_MAX_ENTRY_BYTES = 4L * 1024L * 1024L
    }

    object RecordStore {
        // Temporary record file service. It stores uploaded .opus files without a database
        // and returns short-lived download URLs for the MCU.
        val REMOTE_BASE_URL: String = BuildConfig.TJI_SPEAKER_REMOTE_BASE_URL

        // Multipart upload endpoint for temporary record files.
        const val UPLOAD_TEMP_PATH = "/api/speaker/audio/upload-temp"
    }

    object Tone {
        // Local speaker buzzer test frequency in Hz.
        const val FREQUENCY_HZ = 1_000

        // Local speaker buzzer test duration in milliseconds.
        const val DURATION_MS = 640

        // Buzzer test amplitude, 0.0 to 1.0.
        const val AMPLITUDE = 0.35f

        // Short fade-in/out to avoid a click at tone boundaries.
        const val FADE_MS = 12

        // Silence before the tone in the temporary Ogg Opus file.
        const val LEADING_SILENCE_MS = 80
    }

    object Timing {
        // Silence before TTS in file playback so the MCU buffer and amplifier can settle.
        const val TTS_FILE_LEADING_SILENCE_MS = 200

        // Silence before recorded PTT playback so the MCU buffer is ready before speech starts.
        const val RECORDED_LEADING_SILENCE_MS = 120
    }

    object DirectPtt {
        // 48 kHz speech at 32 kbps keeps full-band quality without doubling
        // the simultaneous MCU microphone uplink load.
        const val BITRATE = 32_000
    }

    object Debug {
        // Logcat tag for speaker audio RMS/peak diagnostics.
        const val AUDIO_DEBUG_TAG = "SpeakerAudioData"

        // Number of live mic frames logged at stream start.
        const val AUDIO_DEBUG_FRAME_LIMIT = 12
    }
}

enum class SpeakerTtsVoicePreset(
    val label: String,
    val speechRate: Float,
    val pitch: Float,
    val voiceNameKeywords: List<String>
) {
    Standard(
        label = "默认",
        speechRate = SpeakerAudioConfig.Tts.SPEECH_RATE,
        pitch = SpeakerAudioConfig.Tts.PITCH,
        voiceNameKeywords = emptyList()
    ),
    Female(
        label = "女声",
        speechRate = 1.0f,
        pitch = 1.08f,
        voiceNameKeywords = listOf(
            "female",
            "woman",
            "girl",
            "xiaoyan",
            "xiaomei",
            "xiaoxiao",
            "mei",
            "yan",
            "女"
        )
    ),
    Male(
        label = "男声",
        speechRate = 0.96f,
        pitch = 0.84f,
        voiceNameKeywords = listOf(
            "male",
            "man",
            "boy",
            "xiaoyu",
            "xiaogang",
            "yunxi",
            "gang",
            "yu",
            "男"
        )
    ),
    Broadcast(
        label = "播报",
        speechRate = 0.92f,
        pitch = 0.96f,
        voiceNameKeywords = listOf("news", "broadcast", "standard", "narrator", "播报")
    ),
    Alert(
        label = "警示",
        speechRate = 1.06f,
        pitch = 1.04f,
        voiceNameKeywords = listOf("clear", "bright", "assistant", "default", "警示")
    )
}

enum class SpeakerAudioQuality(
    val label: String,
    val wireName: String,
    val sampleRate: Int,
    val packetMs: Int
) {
    Low(label = "低", wireName = "low", sampleRate = 8_000, packetMs = 20),
    Medium(label = "中", wireName = "medium", sampleRate = 16_000, packetMs = 20),
    High(label = "高", wireName = "high", sampleRate = 48_000, packetMs = 20);

    val samplesPerFrame: Int
        get() = sampleRate * packetMs / 1_000

    val frameBytes: Int
        get() = samplesPerFrame *
            SpeakerMicrophoneFormat.CHANNELS *
            SpeakerMicrophoneFormat.PCM16_BYTES_PER_SAMPLE
}
