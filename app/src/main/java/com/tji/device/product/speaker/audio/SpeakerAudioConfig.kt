package com.tji.device.product.speaker.audio

import com.tji.device.BuildConfig
import java.util.Locale

object SpeakerAudioConfig {
    object Codec {
        // Keep the IMA ADPCM step index continuous across 40 ms frames.
        // Resetting it every frame creates an audible periodic "tuk-tuk" artifact.
        const val RESET_ADPCM_STEP_INDEX_EACH_FRAME = false

        // Current non-realtime playback/storage format. PCM16 trades a little bandwidth for stable audio.
        val DEFAULT_HADP_CODEC: SpeakerHadpCodec = SpeakerHadpCodec.Pcm16
    }

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
        val HOST: String = BuildConfig.TJI_SPEAKER_RELAY_HOST

        // MCU/relay UDP audio and control port.
        val PORT: Int = BuildConfig.TJI_SPEAKER_RELAY_PORT

        // Current PTT send/save flows send each UDP packet once; record-store reliability
        // depends on seq/lastPacket validation instead of blind duplicate packets.
        const val REDUNDANCY = 1
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

        // Push-to-talk target RMS. Keep below TTS so mic noise is not lifted into ADPCM hiss.
        const val PTT_TARGET_RMS = 0.22f

        // Push-to-talk maximum AGC gain. A conservative cap avoids boosting room noise and handling clicks.
        const val PTT_MAX_GAIN = 12f

        // Push-to-talk activity floor. Lower than live so quiet recorded speech is still boosted.
        const val PTT_MIN_ACTIVE_RMS = 0.0003f

        // Push-to-talk peak activity floor. Avoids amplifying complete silence.
        const val PTT_MIN_ACTIVE_PEAK = 0.0008f

        // TTS playback target RMS. Keep max loudness aligned with PTT; only the first edge is softened.
        const val TTS_TARGET_RMS = 0.28f

        // TTS playback maximum gain. Keep full headroom so max volume stays unchanged.
        const val TTS_MAX_GAIN = 28f

        // TTS activity floor. Prevents normalizing generated leading silence or empty WAV data.
        const val TTS_MIN_ACTIVE_RMS = 0.0003f

        // TTS peak floor. Used with RMS to avoid boosting digital silence.
        const val TTS_MIN_ACTIVE_PEAK = 0.0008f

        // TTS low-pass cutoff before 8 kHz ADPCM playback. Text speech has wideband
        // consonants that can become "zizi" artifacts on the speaker path.
        const val TTS_LOW_PASS_CUTOFF_HZ = 3_000f

        // Apply the low-pass more than once for TTS because a one-pole filter is gentle.
        const val TTS_LOW_PASS_PASSES = 2

        // TTS now uses the stable HADP file path; do not fade from the first speech
        // sample, otherwise the first Chinese syllable can sound swallowed.
        const val TTS_HEAD_FADE_MS = 0

        // First-speech detector used before applying TTS fade-in.
        const val TTS_HEAD_FADE_START_PEAK = 0.002f

        // Kept disabled for file-based TTS playback; leading silence handles output settling.
        const val TTS_HEAD_LIMIT_MS = 0

        // Soft ceiling for the first speech edge to suppress clicks without lowering the whole clip.
        const val TTS_HEAD_LIMIT_CEILING = 0.50f

        // Fade out synthesized speech so playback does not end at a non-zero waveform.
        const val TTS_TAIL_FADE_MS = 40

        // Extra silence after TTS playback to let the MCU/audio output settle cleanly.
        const val TTS_TRAILING_SILENCE_MS = 500

        // Silence before TTS in the file-based playback path. This gives the MCU
        // playback buffer and amplifier a short settle period without lowering
        // the first spoken syllable.
        const val TTS_FILE_LEADING_SILENCE_MS = 200

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

        // Require several speech-like windows before sending PTT; this prevents pure room noise from being amplified.
        const val PTT_MIN_SPEECH_WINDOWS = 4

        // Low-pass recorded microphone speech before ADPCM/UDP playback to reduce sharp "zizi" artifacts.
        const val PTT_LOW_PASS_CUTOFF_HZ = 3_200f

        // One pass is enough for recorded speech; more passes can make speech dull.
        const val PTT_LOW_PASS_PASSES = 1

        // Drop the very end of push-to-talk recordings because button release/AudioRecord stop can create a click.
        const val PTT_RELEASE_GUARD_MS = 250

        // Trim long silence after the last detected speech before applying fade-out.
        const val PTT_END_SILENCE_TRIM_WINDOW_MS = 20

        // Tail windows quieter than this RMS are treated as post-speech silence.
        const val PTT_END_SILENCE_RMS = 0.010f

        // Tail windows below this peak are treated as post-speech silence.
        const val PTT_END_SILENCE_PEAK = 0.045f

        // Keep this much natural room tail after the last detected speech window.
        const val PTT_END_KEEP_AFTER_SPEECH_MS = 120

        // Fade the end of recorded push-to-talk clips so the amplifier does not snap from voice to zero.
        const val PTT_TAIL_FADE_MS = 40

        // Extra silence after push-to-talk playback to let ADPCM/MCU/audio output settle cleanly.
        const val PTT_TRAILING_SILENCE_MS = 100

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

        // Default TTS engine. System TTS is the only engine wired to playback today.
        val DEFAULT_ENGINE: SpeakerTtsEngine = SpeakerTtsEngine.System

        // Customer builds do not package Kokoro/onnxruntime native libraries or model assets.
        val AVAILABLE_ENGINES: List<SpeakerTtsEngine> = listOf(SpeakerTtsEngine.System)

        // Asset directory for the offline Kokoro model. Put model.onnx, voices.bin,
        // tokens.txt, lexicons, rule FSTs, and espeak-ng-data under this folder.
        const val LOCAL_KOKORO_MODEL_DIR = "kokoro-multi-lang-v1_0"

        // Kokoro multi-language model file name inside LOCAL_KOKORO_MODEL_DIR.
        const val LOCAL_KOKORO_MODEL_NAME = "model.onnx"

        // Kokoro speaker embedding file name inside LOCAL_KOKORO_MODEL_DIR.
        const val LOCAL_KOKORO_VOICES = "voices.bin"

        // Chinese and English lexicons used by Kokoro multi-language.
        const val LOCAL_KOKORO_LEXICON =
            "kokoro-multi-lang-v1_0/lexicon-us-en.txt,kokoro-multi-lang-v1_0/lexicon-zh.txt"

        // Chinese text normalization rules used by Kokoro multi-language.
        const val LOCAL_KOKORO_RULE_FSTS =
            "kokoro-multi-lang-v1_0/phone-zh.fst,kokoro-multi-lang-v1_0/date-zh.fst,kokoro-multi-lang-v1_0/number-zh.fst"

        // Customer-facing default voice quality. UI labels are intentionally simple:
        // low/medium/high instead of sample-rate jargon.
        val DEFAULT_TTS_QUALITY: SpeakerAudioQuality = SpeakerAudioQuality.Medium

        // Legacy default used by older local Kokoro calls; normal playback should use DEFAULT_TTS_QUALITY.
        const val LOCAL_KOKORO_TARGET_SAMPLE_RATE = 8_000

        // Default Kokoro speaker for the offline TTS prototype.
        val DEFAULT_KOKORO_VOICE: SpeakerKokoroVoice = SpeakerKokoroVoice.ZmYunxi

        // Kokoro speed lower bound. 1.0 is the model default.
        const val KOKORO_MIN_SPEED = 0.75f

        // Kokoro speed upper bound. Higher values speak faster.
        const val KOKORO_MAX_SPEED = 1.25f

        // Kokoro default speed. Used when switching to the offline prototype.
        const val KOKORO_DEFAULT_SPEED = 1.0f

        // Number of synthesized TTS PCM clips kept in memory to avoid repeated cloud/system synthesis.
        const val PCM_CACHE_MAX_ITEMS = 8
    }

    object RecordStore {
        // Temporary record file service. It stores uploaded .hadp files without a database
        // and returns short-lived download URLs for the MCU.
        val REMOTE_BASE_URL: String = BuildConfig.TJI_SPEAKER_REMOTE_BASE_URL

        // Multipart upload endpoint for temporary record files.
        const val UPLOAD_TEMP_PATH = "/api/speaker/records/upload-temp"
    }

    object Tone {
        // Local speaker buzzer test frequency in Hz.
        const val FREQUENCY_HZ = 1_000

        // Local speaker buzzer test duration in milliseconds, aligned to the 40 ms ADPCM packet period.
        const val DURATION_MS = 640

        // Buzzer test amplitude, 0.0 to 1.0.
        const val AMPLITUDE = 0.35f

        // Short fade-in/out to avoid a click at tone boundaries.
        const val FADE_MS = 12

        // Prefill the MCU playback buffer before pacing tone packets.
        const val PREBUFFER_PACKETS = 2

        // Small leading silence to let the playback path settle before tone starts.
        const val LEADING_SILENCE_MS = 80
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

        // Diagnostic silent HADP duration. If this plays with clicks, the issue is in frame playback/decoding.
        const val SILENCE_TEST_DURATION_MS = 1_200

        // Number of live mic frames logged at stream start.
        const val AUDIO_DEBUG_FRAME_LIMIT = 12
    }
}

data class SpeakerToneSettings(
    val preset: SpeakerTonePreset = SpeakerTonePreset.Standard,
    val clarity: Int = SpeakerTonePreset.Standard.clarity,
    val noiseReduction: Int = SpeakerTonePreset.Standard.noiseReduction,
    val loudness: Int = SpeakerTonePreset.Standard.loudness,
    val lowCut: Int = SpeakerTonePreset.Standard.lowCut,
    val protection: SpeakerLimiterProtection = SpeakerTonePreset.Standard.protection,
    val bassDb: Float = SpeakerAudioConfig.Equalizer.DEFAULT_BASS_DB,
    val trebleDb: Float = SpeakerAudioConfig.Equalizer.DEFAULT_TREBLE_DB
) {
    fun normalized(): SpeakerToneSettings =
        copy(
            clarity = clarity.coerceIn(0, 100),
            noiseReduction = noiseReduction.coerceIn(0, 100),
            loudness = loudness.coerceIn(0, 100),
            lowCut = lowCut.coerceIn(0, 100),
            bassDb = bassDb.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB),
            trebleDb = trebleDb.coerceIn(SpeakerAudioConfig.Equalizer.MIN_DB, SpeakerAudioConfig.Equalizer.MAX_DB)
        )

    val requiresKotlinProcessor: Boolean
        get() = true

    fun asCustom(): SpeakerToneSettings = copy(preset = SpeakerTonePreset.Custom).normalized()

    companion object {
        fun fromPreset(preset: SpeakerTonePreset): SpeakerToneSettings =
            SpeakerToneSettings(
                preset = preset,
                clarity = preset.clarity,
                noiseReduction = preset.noiseReduction,
                loudness = preset.loudness,
                lowCut = preset.lowCut,
                protection = preset.protection,
                bassDb = preset.bassDb,
                trebleDb = preset.trebleDb
            ).normalized()
    }
}

enum class SpeakerLimiterProtection(val label: String, val ceiling: Float) {
    Low(label = "低", ceiling = 0.98f),
    Medium(label = "中", ceiling = 0.92f),
    High(label = "高", ceiling = 0.86f)
}

enum class SpeakerTonePreset(
    val label: String,
    val clarity: Int,
    val noiseReduction: Int,
    val loudness: Int,
    val lowCut: Int,
    val protection: SpeakerLimiterProtection,
    val bassDb: Float,
    val trebleDb: Float
) {
    Standard(
        label = "标准",
        clarity = 45,
        noiseReduction = 35,
        loudness = 45,
        lowCut = 40,
        protection = SpeakerLimiterProtection.Medium,
        bassDb = 0f,
        trebleDb = 0f
    ),
    Clear(
        label = "清晰",
        clarity = 68,
        noiseReduction = 35,
        loudness = 48,
        lowCut = 58,
        protection = SpeakerLimiterProtection.Medium,
        bassDb = -1.5f,
        trebleDb = 2.5f
    ),
    Loud(
        label = "响亮",
        clarity = 62,
        noiseReduction = 45,
        loudness = 76,
        lowCut = 55,
        protection = SpeakerLimiterProtection.High,
        bassDb = -1f,
        trebleDb = 2f
    ),
    Far(
        label = "远距离",
        clarity = 78,
        noiseReduction = 50,
        loudness = 72,
        lowCut = 70,
        protection = SpeakerLimiterProtection.High,
        bassDb = -2.5f,
        trebleDb = 3f
    ),
    Noise(
        label = "抗噪",
        clarity = 58,
        noiseReduction = 78,
        loudness = 58,
        lowCut = 82,
        protection = SpeakerLimiterProtection.High,
        bassDb = -3f,
        trebleDb = 1f
    ),
    Soft(
        label = "柔和",
        clarity = 32,
        noiseReduction = 22,
        loudness = 28,
        lowCut = 25,
        protection = SpeakerLimiterProtection.Medium,
        bassDb = 1f,
        trebleDb = -1.5f
    ),
    Custom(
        label = "自定义",
        clarity = Standard.clarity,
        noiseReduction = Standard.noiseReduction,
        loudness = Standard.loudness,
        lowCut = Standard.lowCut,
        protection = SpeakerLimiterProtection.Medium,
        bassDb = 0f,
        trebleDb = 0f
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

enum class SpeakerTtsEngine(val label: String) {
    System(label = "手机语音"),
    LocalKokoro(label = "内置语音")
}

enum class SpeakerAudioQuality(
    val label: String,
    val wireName: String,
    val sampleRate: Int,
    val packetMs: Int
) {
    Low(label = "低", wireName = "low", sampleRate = 8_000, packetMs = 40),
    Medium(label = "中", wireName = "medium", sampleRate = 16_000, packetMs = 40),
    High(label = "高", wireName = "high", sampleRate = 24_000, packetMs = 40);

    val samplesPerFrame: Int
        get() = sampleRate * packetMs / 1_000

    val frameBytes: Int
        get() = samplesPerFrame * SpeakerAdpcmPacketizer.CHANNELS * 2
}

enum class SpeakerKokoroVoice(
    val speakerId: Int,
    val serverName: String,
    val label: String,
    val gender: String
) {
    ZfXiaobei(speakerId = 45, serverName = "zf_xiaobei", label = "小北", gender = "女声"),
    ZfXiaoni(speakerId = 46, serverName = "zf_xiaoni", label = "小妮", gender = "女声"),
    ZfXiaoxiao(speakerId = 47, serverName = "zf_xiaoxiao", label = "小小", gender = "女声"),
    ZfXiaoyi(speakerId = 48, serverName = "zf_xiaoyi", label = "小艺", gender = "女声"),
    ZmYunjian(speakerId = 49, serverName = "zm_yunjian", label = "云健", gender = "男声"),
    ZmYunxi(speakerId = 50, serverName = "zm_yunxi", label = "云希", gender = "男声"),
    ZmYunxia(speakerId = 51, serverName = "zm_yunxia", label = "云夏", gender = "男声"),
    ZmYunyang(speakerId = 52, serverName = "zm_yunyang", label = "云扬", gender = "男声")
}

fun SpeakerKokoroVoice.customerLabel(): String {
    val sameGenderVoices = SpeakerKokoroVoice.entries.filter { it.gender == gender }
    val index = sameGenderVoices.indexOf(this).coerceAtLeast(0) + 1
    return index.toString()
}

data class SpeakerKokoroTtsSettings(
    val voice: SpeakerKokoroVoice = SpeakerAudioConfig.Tts.DEFAULT_KOKORO_VOICE,
    val speed: Float = SpeakerAudioConfig.Tts.KOKORO_DEFAULT_SPEED
) {
    fun normalized(): SpeakerKokoroTtsSettings =
        copy(
            speed = speed.coerceIn(
                SpeakerAudioConfig.Tts.KOKORO_MIN_SPEED,
                SpeakerAudioConfig.Tts.KOKORO_MAX_SPEED
            )
        )
}
