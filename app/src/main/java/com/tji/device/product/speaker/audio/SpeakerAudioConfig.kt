package com.tji.device.product.speaker.audio

import java.util.Locale

object SpeakerAudioConfig {
    object Pcm {
        // PCM sample scale for signed 16-bit audio. Negative full-scale is -32768.
        const val PCM_I16_NEGATIVE_SCALE = 32_768f

        // PCM sample scale for signed 16-bit audio. Positive full-scale is 32767.
        const val PCM_I16_POSITIVE_SCALE = 32_767f

        // Minimum floating-point PCM value before converting back to int16.
        const val PCM_FLOAT_MIN = -1f

        // Maximum floating-point PCM value before converting back to int16.
        const val PCM_FLOAT_MAX = 1f
    }

    object Relay {
        // UDP relay server used by the 4G speaker audio path.
        const val HOST = "146.56.250.203"

        // MCU/relay UDP audio and control port.
        const val PORT = 7000

        // Send each UDP packet this many times to reduce packet-loss clicks.
        const val REDUNDANCY = 2
    }

    object Gain {
        // Initial output gain shown by the App, 1.0 means maximum web-compatible gain.
        const val DEFAULT_OUTPUT_GAIN = 1f

        // Upper bound for output gain control. Keep at 1.0 unless MCU gain mapping changes.
        const val MAX_OUTPUT_GAIN = 1f

        // Converts float gain 0.0-1.0 to MCU Q8 gain 0-256.
        const val CONTROL_GAIN_Q8_SCALE = 256f

        // Live mic is capped lower than playback to reduce feedback/howling risk.
        const val LIVE_SAFE_OUTPUT_GAIN = 0.45f
    }

    object Voice {
        // One-pole high-pass cutoff. Removes handling rumble and low-frequency feedback energy.
        const val HIGH_PASS_CUTOFF_HZ = 120f

        // Adds a small first-difference boost so speech consonants remain clear after 8 kHz encoding.
        const val PRESENCE_EDGE_GAIN = 0.18f

        // Limiter knee. Samples above this level are compressed before final ceiling.
        const val COMPRESS_THRESHOLD = 0.58f

        // Limiter ratio above the threshold. Higher values clamp peaks more aggressively.
        const val COMPRESS_RATIO = 3.2f

        // Final output ceiling. Keeps generated PCM below digital clipping.
        const val LIMITER_CEILING = 0.98f

        // Live mic target RMS. Conservative because the speaker output can feed back into the phone mic.
        const val LIVE_TARGET_RMS = 0.18f

        // Live mic maximum AGC gain. Prevents background noise and feedback from being over-amplified.
        const val LIVE_MAX_GAIN = 6f

        // Live AGC raises gain slowly so room noise and feedback are not pulled up suddenly.
        const val LIVE_AGC_GAIN_UP_SMOOTHING = 0.18f

        // Live AGC lowers gain quickly when speech gets loud to avoid blasts.
        const val LIVE_AGC_GAIN_DOWN_SMOOTHING = 0.65f

        // Live mic activity floor. Frames below this are treated as silence/noise.
        const val LIVE_MIN_ACTIVE_RMS = 0.0005f

        // Live mic peak activity floor. Used with RMS to avoid opening on tiny low-level noise.
        const val LIVE_MIN_ACTIVE_PEAK = 0.001f

        // Push-to-talk target RMS. Higher than live because playback starts after recording stops.
        const val PTT_TARGET_RMS = 0.30f

        // Push-to-talk maximum AGC gain. Allows quiet speech to become loud enough.
        const val PTT_MAX_GAIN = 28f

        // Push-to-talk activity floor. Lower than live so quiet recorded speech is still boosted.
        const val PTT_MIN_ACTIVE_RMS = 0.0003f

        // Push-to-talk peak activity floor. Avoids amplifying complete silence.
        const val PTT_MIN_ACTIVE_PEAK = 0.0008f

        // TTS playback target RMS. Kept close to PTT so text broadcast and recorded speech feel similar.
        const val TTS_TARGET_RMS = 0.28f

        // TTS playback maximum gain. Match PTT gain headroom because TTS is clean and has no mic noise.
        const val TTS_MAX_GAIN = 28f

        // TTS activity floor. Prevents normalizing generated leading silence or empty WAV data.
        const val TTS_MIN_ACTIVE_RMS = 0.0003f

        // TTS peak floor. Used with RMS to avoid boosting digital silence.
        const val TTS_MIN_ACTIVE_PEAK = 0.0008f

        // Estimate PTT noise from the quietest windows in the whole clip, so instant speech is not treated as noise.
        const val PTT_NOISE_LOW_PERCENT = 0.20f

        // Fallback floor when the estimated noise is extremely quiet.
        const val PTT_NOISE_FLOOR_RMS = 0.0025f

        // Audio below this multiple of the noise RMS is treated as background.
        const val PTT_NOISE_GATE_CLOSE_MULTIPLIER = 1.8f

        // Audio above this multiple of the noise RMS is treated as speech.
        const val PTT_NOISE_GATE_OPEN_MULTIPLIER = 3.2f

        // Keep a little room tone under the gate to avoid choppy hard cuts.
        const val PTT_NOISE_GATE_CLOSED_SCALE = 0.02f

        // Process PTT noise reduction in small windows so speech is not flattened sample-by-sample.
        const val PTT_NOISE_GATE_WINDOW_MS = 20

        // Smooth gate changes between windows to avoid zipper noise.
        const val PTT_NOISE_GATE_SMOOTHING = 0.55f

        // Live gate closes fully below this RMS when peak is also low.
        const val LIVE_GATE_MUTE_RMS = 0.006f

        // Live gate closes fully below this peak when RMS is also low.
        const val LIVE_GATE_MUTE_PEAK = 0.030f

        // Live gate is fully open above this RMS, or when peak indicates speech.
        const val LIVE_GATE_OPEN_RMS = 0.014f

        // Live gate is fully open above this peak.
        const val LIVE_GATE_OPEN_PEAK = 0.050f

        // Low-activity live audio is reduced to this scale instead of jumping directly to zero.
        const val LIVE_GATE_LOW_ACTIVITY_SCALE = 0.16f
    }

    object Equalizer {
        // User EQ range in decibels. Keep modest because 8 kHz ADPCM clips easily when over-boosted.
        const val MIN_DB = -6f

        // User EQ range in decibels. +6 dB is enough to hear a clear change without crushing speech.
        const val MAX_DB = 6f

        // Default low-frequency shelf. 0 dB means no bass boost/cut.
        const val DEFAULT_BASS_DB = 0f

        // Default high-frequency shelf. 0 dB means no treble boost/cut.
        const val DEFAULT_TREBLE_DB = 0f

        // Low shelf corner frequency. This affects voice body/rumble without fighting the 120 Hz high-pass.
        const val BASS_SHELF_HZ = 180f

        // High shelf corner frequency. This affects speech brightness and bite at 8 kHz sample rate.
        const val TREBLE_SHELF_HZ = 2_500f

        // Biquad shelf slope. 0.707 is a smooth, low-ringing default for speech.
        const val SHELF_SLOPE = 0.707f
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
    }

    object Tone {
        // Local speaker buzzer test frequency in Hz.
        const val FREQUENCY_HZ = 1_000

        // Buzzer test amplitude, 0.0 to 1.0.
        const val AMPLITUDE = 0.35f
    }

    object Timing {
        // Delay after stream reset before sending DSP/gain/audio packets.
        const val STREAM_RESET_DELAY_MS = 80L

        // Gain control is repeated because it is sent over UDP.
        const val GAIN_CONTROL_REPEAT = 3

        // Delay between repeated gain-control packets.
        const val GAIN_CONTROL_REPEAT_DELAY_MS = 15L

        // DSP control is repeated because it is sent over UDP.
        const val DSP_CONTROL_REPEAT = 2

        // Delay between repeated DSP-control packets.
        const val DSP_CONTROL_REPEAT_DELAY_MS = 12L

        // Local tone control is repeated because it is sent over UDP.
        const val LOCAL_TONE_CONTROL_REPEAT = 3

        // Delay between repeated local-tone packets.
        const val LOCAL_TONE_CONTROL_REPEAT_DELAY_MS = 15L

        // Mute the first live mic frames to avoid startup pops.
        const val LIVE_STARTUP_MUTE_FRAMES = 6

        // Silence before recorded PTT playback so the MCU buffer is ready before speech starts.
        const val RECORDED_LEADING_SILENCE_MS = 120

        // PTT sends the first packets quickly to prefill the MCU buffer.
        const val RECORDED_PREBUFFER_PACKETS = 4
    }

    object Debug {
        // Logcat tag for speaker audio RMS/peak diagnostics.
        const val AUDIO_DEBUG_TAG = "SpeakerAudioData"

        // Number of live mic frames logged at stream start.
        const val AUDIO_DEBUG_FRAME_LIMIT = 12
    }
}

data class SpeakerToneSettings(
    val bassDb: Float = SpeakerAudioConfig.Equalizer.DEFAULT_BASS_DB,
    val trebleDb: Float = SpeakerAudioConfig.Equalizer.DEFAULT_TREBLE_DB
) {
    fun normalized(): SpeakerToneSettings =
        copy(
            bassDb = bassDb.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB),
            trebleDb = trebleDb.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB)
        )
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
