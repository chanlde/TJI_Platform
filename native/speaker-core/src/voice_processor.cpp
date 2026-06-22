#include "speaker_core_internal.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <numeric>
#include <optional>
#include <stdexcept>
#include <vector>

namespace tji::speaker {
namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 2.0f * kPi;
constexpr float kPcmI16NegativeScale = 32768.0f;
constexpr float kPcmI16PositiveScale = 32767.0f;
constexpr int kMillisPerSecond = 1000;

// 通用语音清理参数。这里故意保持温和：喊话器是窄带语音链路，
// 处理过猛会让辅音变刺耳，或者吞掉中文首字。
constexpr float kHighPassCutoffHz = 120.0f;
constexpr float kPresenceEdgeGain = 0.18f;
constexpr float kCompressThreshold = 0.58f;
constexpr float kCompressRatio = 3.2f;
constexpr float kLimiterCeiling = 0.98f;

// 实时喊话自动增益和噪声门。实时增益偏保守，因为手机麦克风可能会拾取实体喇叭声音，
// 形成反馈或啸叫。
constexpr float kLiveTargetRms = 0.18f;
constexpr float kLiveMaxGain = 6.0f;
constexpr float kLiveGainUpSmoothing = 0.18f;
constexpr float kLiveGainDownSmoothing = 0.65f;
constexpr float kLiveMinActiveRms = 0.0005f;
constexpr float kLiveMinActivePeak = 0.001f;
constexpr float kLiveGateMuteRms = 0.006f;
constexpr float kLiveGateMutePeak = 0.030f;
constexpr float kLiveGateOpenRms = 0.014f;
constexpr float kLiveGateOpenPeak = 0.050f;
constexpr float kLiveGateLowActivityScale = 0.16f;

// 按住说话可以比实时喊话更响，因为录音结束后才播放。软噪声门会从整段录音估算安静底噪，
// 降低稳定背景噪声，但尽量不硬切字头。
constexpr float kPttTargetRms = 0.22f;
constexpr float kPttMaxGain = 12.0f;
constexpr float kPttMinActiveRms = 0.0003f;
constexpr float kPttMinActivePeak = 0.0008f;
constexpr float kPttNoiseLowPercent = 0.20f;
constexpr float kPttNoiseFloorRms = 0.0025f;
constexpr float kPttNoiseGateCloseMultiplier = 1.8f;
constexpr float kPttNoiseGateOpenMultiplier = 3.2f;
constexpr float kPttNoiseGateClosedScale = 0.02f;
constexpr int kPttNoiseGateWindowMs = 20;
constexpr float kPttNoiseGateSmoothing = 0.55f;
constexpr float kPttLowPassCutoffHz = 3200.0f;
constexpr int kPttLowPassPasses = 1;
constexpr int kPttReleaseGuardMs = 250;
constexpr int kPttEndSilenceTrimWindowMs = 20;
constexpr float kPttEndSilenceRms = 0.010f;
constexpr float kPttEndSilencePeak = 0.045f;
constexpr int kPttEndKeepAfterSpeechMs = 120;
constexpr int kPttTailFadeMs = 40;
constexpr int kPttTrailingSilenceMs = 100;

// TTS 播放跳过麦克风清理，但仍做响度归一、面向 8 kHz 喊话器链路的低通平滑，
// 以及安全的尾部淡出。
constexpr float kTtsTargetRms = 0.28f;
constexpr float kTtsMaxGain = 28.0f;
constexpr float kTtsMinActiveRms = 0.0003f;
constexpr float kTtsMinActivePeak = 0.0008f;
constexpr float kTtsLowPassCutoffHz = 3000.0f;
constexpr int kTtsLowPassPasses = 2;
constexpr int kTtsHeadFadeMs = 0;
constexpr float kTtsHeadFadeStartPeak = 0.002f;
constexpr int kTtsHeadLimitMs = 0;
constexpr float kTtsHeadLimitCeiling = 0.50f;
constexpr int kTtsTailFadeMs = 40;
constexpr int kTtsTrailingSilenceMs = 500;

// 用户可调音色。范围保持较小，因为 8 kHz ADPCM 在过度提升时容易削波和发刺。
constexpr float kEqMinDb = -6.0f;
constexpr float kEqMaxDb = 6.0f;
constexpr float kBassShelfHz = 180.0f;
constexpr float kTrebleShelfHz = 2500.0f;
constexpr float kShelfSlope = 0.707f;

struct Stats {
    float rms = 0.0f;
    float peak = 0.0f;
    int samples = 0;
};

struct Params {
    // 自动增益后的目标均方根响度。调高会让语音更响，但更容易削波。
    float target_rms;
    // 安静语音允许的最大增益。调高也会一起抬高环境噪声。
    float max_gain;
    // 均方根/峰值活动下限。低于该值的音频按静音处理，不做归一化。
    float min_active_rms;
    float min_active_peak;
};

struct PttNoiseGate {
    float close_rms = 0.0f;
    float open_rms = 0.0f;
    int window_samples = 1;
};

void reset_biquad(VoiceBiquadState &filter) {
    filter = VoiceBiquadState{};
}

void configure_shelf(VoiceBiquadState &filter, float frequency_hz, float gain_db, bool low_shelf, int sample_rate) {
        const float nyquist_limit = std::max(20.0f, sample_rate / 2.0f - 100.0f);
        const double frequency = std::clamp(frequency_hz, 20.0f, nyquist_limit);
        const double a = std::pow(10.0, static_cast<double>(gain_db) / 40.0);
        const double omega = 2.0 * static_cast<double>(kPi) * frequency / static_cast<double>(sample_rate);
        const double sin_omega = std::sin(omega);
        const double cos_omega = std::cos(omega);
        const double sqrt_a = std::sqrt(a);
        const double slope = std::max(static_cast<double>(kShelfSlope), 0.1);
        const double alpha = sin_omega / 2.0 *
            std::sqrt(std::max((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0, 0.0));

        double rb0;
        double rb1;
        double rb2;
        double ra0;
        double ra1;
        double ra2;
        if (low_shelf) {
            rb0 = a * ((a + 1.0) - (a - 1.0) * cos_omega + 2.0 * sqrt_a * alpha);
            rb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos_omega);
            rb2 = a * ((a + 1.0) - (a - 1.0) * cos_omega - 2.0 * sqrt_a * alpha);
            ra0 = (a + 1.0) + (a - 1.0) * cos_omega + 2.0 * sqrt_a * alpha;
            ra1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos_omega);
            ra2 = (a + 1.0) + (a - 1.0) * cos_omega - 2.0 * sqrt_a * alpha;
        } else {
            rb0 = a * ((a + 1.0) + (a - 1.0) * cos_omega + 2.0 * sqrt_a * alpha);
            rb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_omega);
            rb2 = a * ((a + 1.0) + (a - 1.0) * cos_omega - 2.0 * sqrt_a * alpha);
            ra0 = (a + 1.0) - (a - 1.0) * cos_omega + 2.0 * sqrt_a * alpha;
            ra1 = 2.0 * ((a - 1.0) - (a + 1.0) * cos_omega);
            ra2 = (a + 1.0) - (a - 1.0) * cos_omega - 2.0 * sqrt_a * alpha;
        }
        filter.b0 = static_cast<float>(rb0 / ra0);
        filter.b1 = static_cast<float>(rb1 / ra0);
        filter.b2 = static_cast<float>(rb2 / ra0);
        filter.a1 = static_cast<float>(ra1 / ra0);
        filter.a2 = static_cast<float>(ra2 / ra0);
}

void process_biquad(VoiceBiquadState &filter, std::vector<float> &samples) {
    for (float &sample : samples) {
        const float input = sample;
        const float output = filter.b0 * input + filter.z1;
        filter.z1 = filter.b1 * input - filter.a1 * output + filter.z2;
        filter.z2 = filter.b2 * input - filter.a2 * output;
        sample = output;
    }
}

int ms_to_samples(int ms, int sample_rate) {
    return sample_rate * std::max(ms, 0) / kMillisPerSecond;
}

std::vector<float> to_float_samples(const uint8_t *pcm, size_t size) {
    const size_t sample_count = size / 2;
    std::vector<float> samples(sample_count);
    for (size_t i = 0; i < sample_count; ++i) {
        const int16_t value = read_i16_le(pcm + i * 2);
        samples[i] = static_cast<float>(value) / kPcmI16NegativeScale;
    }
    return samples;
}

std::vector<uint8_t> to_pcm16le(const std::vector<float> &samples) {
    std::vector<uint8_t> pcm(samples.size() * 2);
    for (size_t i = 0; i < samples.size(); ++i) {
        const float clamped = std::clamp(samples[i], -1.0f, 1.0f);
        int value = clamped < 0.0f
            ? static_cast<int>(clamped * kPcmI16NegativeScale)
            : static_cast<int>(clamped * kPcmI16PositiveScale);
        value = std::clamp(value, static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
        pcm[i * 2] = static_cast<uint8_t>(value & 0xFF);
        pcm[i * 2 + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
    }
    return pcm;
}

Stats stats(const std::vector<float> &samples, size_t start = 0, size_t end = SIZE_MAX) {
    end = std::min(end, samples.size());
    if (start >= end) return {};
    float peak = 0.0f;
    float sum_sq = 0.0f;
    for (size_t i = start; i < end; ++i) {
        const float value = samples[i];
        peak = std::max(peak, std::abs(value));
        sum_sq += value * value;
    }
    const int count = static_cast<int>(end - start);
    return {static_cast<float>(std::sqrt(sum_sq / count)), peak, count};
}

float window_rms(const std::vector<float> &samples, size_t start, size_t end) {
    return stats(samples, start, end).rms;
}

void remove_dc(std::vector<float> &samples) {
    if (samples.empty()) return;
    const float mean = std::accumulate(samples.begin(), samples.end(), 0.0f) / static_cast<float>(samples.size());
    for (float &sample : samples) sample -= mean;
}

void high_pass(std::vector<float> &samples, bool stateful, int sample_rate, float &previous_input, float &previous_high_pass) {
    const float rc = 1.0f / (kTwoPi * kHighPassCutoffHz);
    const float dt = 1.0f / static_cast<float>(sample_rate);
    const float alpha = rc / (rc + dt);
    float last_input = stateful ? previous_input : 0.0f;
    float last_output = stateful ? previous_high_pass : 0.0f;
    for (float &sample : samples) {
        const float input = sample;
        const float output = alpha * (last_output + input - last_input);
        sample = output;
        last_input = input;
        last_output = output;
    }
    if (stateful) {
        previous_input = last_input;
        previous_high_pass = last_output;
    }
}

void add_presence(std::vector<float> &samples, bool stateful, float &previous_presence) {
    float previous = stateful ? previous_presence : 0.0f;
    for (float &sample : samples) {
        const float current = sample;
        const float edge = current - previous;
        sample = std::clamp(current + edge * kPresenceEdgeGain, -1.0f, 1.0f);
        previous = current;
    }
    if (stateful) previous_presence = previous;
}

void low_pass(std::vector<float> &samples, float cutoff_hz, int passes, int sample_rate) {
    if (samples.empty() || cutoff_hz <= 0.0f || passes <= 0) return;
    const float clamped_cutoff = std::clamp(cutoff_hz, 20.0f, sample_rate / 2.0f - 100.0f);
    const float rc = 1.0f / (kTwoPi * clamped_cutoff);
    const float dt = 1.0f / static_cast<float>(sample_rate);
    const float alpha = dt / (rc + dt);
    for (int pass = 0; pass < passes; ++pass) {
        float previous = samples.front();
        for (float &sample : samples) {
            previous += alpha * (sample - previous);
            sample = previous;
        }
    }
}

void apply_tone_equalizer(
    std::vector<float> &samples,
    VoiceToneSettings tone,
    int sample_rate,
    VoiceBiquadState *bass_state,
    VoiceBiquadState *treble_state
) {
    const float bass = std::clamp(tone.bass_db, kEqMinDb, kEqMaxDb);
    const float treble = std::clamp(tone.treble_db, kEqMinDb, kEqMaxDb);
    if (bass == 0.0f && treble == 0.0f) return;
    VoiceBiquadState stateless_filter;
    if (bass != 0.0f) {
        VoiceBiquadState &filter = bass_state != nullptr ? *bass_state : stateless_filter;
        if (bass_state == nullptr) reset_biquad(filter);
        configure_shelf(filter, kBassShelfHz, bass, true, sample_rate);
        process_biquad(filter, samples);
    }
    if (treble != 0.0f) {
        VoiceBiquadState &filter = treble_state != nullptr ? *treble_state : stateless_filter;
        if (treble_state == nullptr) reset_biquad(filter);
        configure_shelf(filter, kTrebleShelfHz, treble, false, sample_rate);
        process_biquad(filter, samples);
    }
    for (float &sample : samples) sample = std::clamp(sample, -1.0f, 1.0f);
}

void normalize_and_compress(
    std::vector<float> &samples,
    bool stateful,
    int profile,
    Params params,
    float &previous_agc_gain
) {
    const auto input_stats = stats(samples);
    if (input_stats.samples == 0 || input_stats.rms < params.min_active_rms || input_stats.peak < params.min_active_peak) {
        return;
    }
    const float target_gain = std::min(params.max_gain, params.target_rms / input_stats.rms);
    float gain = target_gain;
    if (stateful && profile == TJI_SC_VOICE_PROFILE_LIVE) {
        const float smoothing = target_gain > previous_agc_gain ? kLiveGainUpSmoothing : kLiveGainDownSmoothing;
        previous_agc_gain += (target_gain - previous_agc_gain) * smoothing;
        gain = std::clamp(previous_agc_gain, 0.0f, kLiveMaxGain);
    }
    for (float &sample : samples) {
        float x = sample * gain;
        const float sign = x < 0.0f ? -1.0f : 1.0f;
        float magnitude = std::abs(x);
        if (magnitude > kCompressThreshold) {
            magnitude = kCompressThreshold + (magnitude - kCompressThreshold) / kCompressRatio;
        }
        magnitude = std::min(magnitude, kLimiterCeiling);
        sample = sign * magnitude;
    }
}

void apply_live_gate(std::vector<float> &samples, const Stats &input_stats) {
    float scale = 1.0f;
    if (input_stats.rms < kLiveGateMuteRms && input_stats.peak < kLiveGateMutePeak) {
        scale = 0.0f;
    } else if (input_stats.rms < kLiveGateOpenRms && input_stats.peak < kLiveGateOpenPeak) {
        const float rms_scale = std::clamp(
            (input_stats.rms - kLiveGateMuteRms) / (kLiveGateOpenRms - kLiveGateMuteRms),
            0.0f,
            1.0f
        );
        scale = std::clamp(kLiveGateLowActivityScale + rms_scale * (1.0f - kLiveGateLowActivityScale), 0.0f, 1.0f);
    }
    if (scale >= 1.0f) return;
    for (float &sample : samples) sample *= scale;
}

float estimate_ptt_noise_rms(const std::vector<float> &samples, int sample_rate) {
    const int window_samples = std::max(1, ms_to_samples(kPttNoiseGateWindowMs, sample_rate));
    std::vector<float> values;
    for (size_t offset = 0; offset < samples.size();) {
        const size_t end = std::min(offset + static_cast<size_t>(window_samples), samples.size());
        values.push_back(window_rms(samples, offset, end));
        offset = end;
    }
    if (values.empty()) return kPttNoiseFloorRms;
    std::sort(values.begin(), values.end());
    const size_t low_count = std::max<size_t>(1, static_cast<size_t>(values.size() * kPttNoiseLowPercent));
    const float sum = std::accumulate(values.begin(), values.begin() + low_count, 0.0f);
    return std::max(sum / static_cast<float>(low_count), kPttNoiseFloorRms);
}

std::optional<PttNoiseGate> create_ptt_noise_gate(const std::vector<float> &samples, int sample_rate) {
    const int window_samples = std::max(1, ms_to_samples(kPttNoiseGateWindowMs, sample_rate));
    const float noise_rms = estimate_ptt_noise_rms(samples, sample_rate);
    const float close_rms = noise_rms * kPttNoiseGateCloseMultiplier;
    const float open_rms = noise_rms * kPttNoiseGateOpenMultiplier;
    float max_rms = 0.0f;
    for (size_t offset = 0; offset < samples.size();) {
        const size_t end = std::min(offset + static_cast<size_t>(window_samples), samples.size());
        max_rms = std::max(max_rms, window_rms(samples, offset, end));
        offset = end;
    }
    if (max_rms < open_rms) return std::nullopt;
    return PttNoiseGate{close_rms, open_rms, window_samples};
}

void apply_ptt_noise_gate(std::vector<float> &samples, const PttNoiseGate &gate) {
    float gain = kPttNoiseGateClosedScale;
    for (size_t offset = 0; offset < samples.size();) {
        const size_t end = std::min(offset + static_cast<size_t>(gate.window_samples), samples.size());
        const float rms = window_rms(samples, offset, end);
        float target_gain = 1.0f;
        if (rms <= gate.close_rms) {
            target_gain = kPttNoiseGateClosedScale;
        } else if (rms < gate.open_rms) {
            const float range = std::max(gate.open_rms - gate.close_rms, std::numeric_limits<float>::min());
            const float position = std::clamp((rms - gate.close_rms) / range, 0.0f, 1.0f);
            target_gain = kPttNoiseGateClosedScale + position * (1.0f - kPttNoiseGateClosedScale);
        }
        gain += (target_gain - gain) * kPttNoiseGateSmoothing;
        for (size_t i = offset; i < end; ++i) samples[i] *= gain;
        offset = end;
    }
}

std::vector<uint8_t> drop_release_guard_tail(const uint8_t *pcm, size_t size, int sample_rate) {
    const size_t guard_bytes = static_cast<size_t>(ms_to_samples(kPttReleaseGuardMs, sample_rate)) * 2;
    const size_t keep = guard_bytes <= 0 || size <= guard_bytes ? size : size - guard_bytes;
    return std::vector<uint8_t>(pcm, pcm + keep);
}

std::vector<uint8_t> trim_long_post_speech_silence(const std::vector<uint8_t> &pcm, int sample_rate) {
    auto samples = to_float_samples(pcm.data(), pcm.size());
    if (samples.empty()) return pcm;
    const int window_samples = std::max(1, ms_to_samples(kPttEndSilenceTrimWindowMs, sample_rate));
    size_t last_speech_end = samples.size();
    for (size_t offset = 0; offset < samples.size();) {
        const size_t end = std::min(offset + static_cast<size_t>(window_samples), samples.size());
        const auto value = stats(samples, offset, end);
        if (value.rms >= kPttEndSilenceRms || value.peak >= kPttEndSilencePeak) {
            last_speech_end = end;
        }
        offset = end;
    }
    const size_t keep_samples = static_cast<size_t>(ms_to_samples(kPttEndKeepAfterSpeechMs, sample_rate));
    const size_t trimmed_samples = std::min(samples.size(), last_speech_end + keep_samples);
    const size_t trimmed_bytes = std::min(pcm.size(), trimmed_samples * 2);
    return std::vector<uint8_t>(pcm.begin(), pcm.begin() + static_cast<std::ptrdiff_t>(trimmed_bytes));
}

void apply_tail_fade(std::vector<float> &samples, int fade_ms, int sample_rate) {
    const int fade_samples = std::clamp(ms_to_samples(fade_ms, sample_rate), 0, static_cast<int>(samples.size()));
    if (fade_samples <= 0) return;
    const size_t start = samples.size() - static_cast<size_t>(fade_samples);
    for (size_t i = start; i < samples.size(); ++i) {
        const size_t remaining = samples.size() - i - 1;
        const float gain = std::clamp(static_cast<float>(remaining) / static_cast<float>(fade_samples), 0.0f, 1.0f);
        samples[i] *= gain;
    }
}

void apply_speech_head_fade(std::vector<float> &samples, int fade_ms, float start_peak, int sample_rate) {
    auto first = std::find_if(samples.begin(), samples.end(), [start_peak](float value) {
        return std::abs(value) >= start_peak;
    });
    if (first == samples.end()) return;
    const int fade_samples = std::clamp(ms_to_samples(fade_ms, sample_rate), 0, static_cast<int>(samples.size()));
    if (fade_samples <= 0) return;
    size_t first_index = static_cast<size_t>(std::distance(samples.begin(), first));
    for (int i = 0; i < fade_samples; ++i) {
        const size_t index = first_index + static_cast<size_t>(i);
        if (index >= samples.size()) break;
        const float gain = std::clamp(static_cast<float>(i) / static_cast<float>(fade_samples), 0.0f, 1.0f);
        samples[index] *= gain;
    }
}

void limit_speech_head(std::vector<float> &samples, int limit_ms, float start_peak, float ceiling, int sample_rate) {
    auto first = std::find_if(samples.begin(), samples.end(), [start_peak](float value) {
        return std::abs(value) >= start_peak;
    });
    if (first == samples.end()) return;
    const size_t first_index = static_cast<size_t>(std::distance(samples.begin(), first));
    const int limit_samples = std::clamp(
        ms_to_samples(limit_ms, sample_rate),
        0,
        static_cast<int>(samples.size() - first_index)
    );
    if (limit_samples <= 0) return;
    const float normalized_ceiling = std::clamp(ceiling, 0.0f, kLimiterCeiling);
    for (size_t i = first_index; i < first_index + static_cast<size_t>(limit_samples); ++i) {
        samples[i] = std::clamp(samples[i], -normalized_ceiling, normalized_ceiling);
    }
}

std::vector<uint8_t> with_ptt_tail_smoothing(std::vector<uint8_t> pcm, int sample_rate) {
    auto samples = to_float_samples(pcm.data(), pcm.size());
    if (samples.empty()) return pcm;
    apply_tail_fade(samples, kPttTailFadeMs, sample_rate);
    auto out = to_pcm16le(samples);
    const size_t tail_silence_bytes = static_cast<size_t>(ms_to_samples(kPttTrailingSilenceMs, sample_rate)) * 2;
    out.resize(out.size() + tail_silence_bytes, 0);
    return out;
}

std::vector<uint8_t> with_tts_edge_smoothing(std::vector<uint8_t> pcm, int sample_rate) {
    auto samples = to_float_samples(pcm.data(), pcm.size());
    if (samples.empty()) return pcm;
    apply_speech_head_fade(samples, kTtsHeadFadeMs, kTtsHeadFadeStartPeak, sample_rate);
    limit_speech_head(samples, kTtsHeadLimitMs, kTtsHeadFadeStartPeak, kTtsHeadLimitCeiling, sample_rate);
    apply_tail_fade(samples, kTtsTailFadeMs, sample_rate);
    auto out = to_pcm16le(samples);
    const size_t tail_silence_bytes = static_cast<size_t>(ms_to_samples(kTtsTrailingSilenceMs, sample_rate)) * 2;
    out.resize(out.size() + tail_silence_bytes, 0);
    return out;
}

std::vector<uint8_t> process_chain(
    const uint8_t *pcm,
    size_t size,
    int profile,
    int sample_rate,
    VoiceToneSettings tone,
    bool stateful,
    float &previous_input,
    float &previous_high_pass,
    float &previous_presence,
    float &previous_agc_gain,
    VoiceBiquadState *bass_state,
    VoiceBiquadState *treble_state
) {
    if (pcm == nullptr && size > 0) throw std::invalid_argument("pcm is null");
    if (sample_rate <= 0) throw std::invalid_argument("sample rate must be positive");
    auto samples = to_float_samples(pcm, size);
    if (samples.empty()) return std::vector<uint8_t>(pcm, pcm + size);
    const auto input_stats = stats(samples);

    // 1. 麦克风清理。TTS 本身已经是合成音，跳过这一步；
    //    否则高通和清晰度增强可能会把第一个音节处理得太硬。
    if (profile != TJI_SC_VOICE_PROFILE_PLAYBACK) {
        remove_dc(samples);
        high_pass(samples, stateful, sample_rate, previous_input, previous_high_pass);
    }
    // 2. 按住说话先做噪声门，再做清晰度增强和自动增益，避免背景噪声被一起放大。
    std::optional<PttNoiseGate> ptt_noise_gate;
    if (profile == TJI_SC_VOICE_PROFILE_PUSH_TO_TALK) {
        ptt_noise_gate = create_ptt_noise_gate(samples, sample_rate);
        if (ptt_noise_gate.has_value()) {
            apply_ptt_noise_gate(samples, *ptt_noise_gate);
        }
    }
    // 3. 加一点边缘增强，让窄带喇叭上的人声更清楚。
    if (profile != TJI_SC_VOICE_PROFILE_PLAYBACK) {
        add_presence(samples, stateful, previous_presence);
    }
    // 4. 在响度归一前应用用户低频/高频搁架均衡，以及 TTS 平滑。
    apply_tone_equalizer(samples, tone, sample_rate, stateful ? bass_state : nullptr, stateful ? treble_state : nullptr);
    if (profile == TJI_SC_VOICE_PROFILE_PLAYBACK) {
        low_pass(samples, kTtsLowPassCutoffHz, kTtsLowPassPasses, sample_rate);
    }

    Params params = {kPttTargetRms, kPttMaxGain, kPttMinActiveRms, kPttMinActivePeak};
    if (profile == TJI_SC_VOICE_PROFILE_LIVE) {
        params = {kLiveTargetRms, kLiveMaxGain, kLiveMinActiveRms, kLiveMinActivePeak};
    } else if (profile == TJI_SC_VOICE_PROFILE_PLAYBACK) {
        params = {kTtsTargetRms, kTtsMaxGain, kTtsMinActiveRms, kTtsMinActivePeak};
    }
    // 5. 自动增益/压缩/限幅。这一步主要负责“变响但不爆”。
    normalize_and_compress(samples, stateful, profile, params, previous_agc_gain);
    // 6. 后处理噪声门和淡入淡出。用于压低被自动增益抬起来的噪声，并保护音频边缘。
    if (profile == TJI_SC_VOICE_PROFILE_PLAYBACK) {
        low_pass(samples, kTtsLowPassCutoffHz, kTtsLowPassPasses, sample_rate);
    } else if (profile == TJI_SC_VOICE_PROFILE_PUSH_TO_TALK) {
        low_pass(samples, kPttLowPassCutoffHz, kPttLowPassPasses, sample_rate);
    }
    if (profile == TJI_SC_VOICE_PROFILE_PUSH_TO_TALK) {
        if (ptt_noise_gate.has_value()) {
            apply_ptt_noise_gate(samples, *ptt_noise_gate);
        }
    }
    if (profile == TJI_SC_VOICE_PROFILE_LIVE) {
        apply_live_gate(samples, input_stats);
    }
    return to_pcm16le(samples);
}

} // namespace

void VoiceProcessor::reset() {
    previous_input_ = 0.0f;
    previous_high_pass_ = 0.0f;
    previous_presence_ = 0.0f;
    previous_agc_gain_ = 1.0f;
    reset_biquad(bass_shelf_);
    reset_biquad(treble_shelf_);
}

std::vector<uint8_t> VoiceProcessor::process_frame(const uint8_t *pcm, size_t size, VoiceToneSettings tone) {
    return process_chain(
        pcm,
        size,
        TJI_SC_VOICE_PROFILE_LIVE,
        kSampleRate,
        tone,
        true,
        previous_input_,
        previous_high_pass_,
        previous_presence_,
        previous_agc_gain_,
        &bass_shelf_,
        &treble_shelf_
    );
}

std::vector<uint8_t> process_voice(
    const uint8_t *pcm,
    size_t size,
    int profile,
    int sample_rate,
    VoiceToneSettings tone
) {
    if (
        profile != TJI_SC_VOICE_PROFILE_LIVE &&
        profile != TJI_SC_VOICE_PROFILE_PUSH_TO_TALK &&
        profile != TJI_SC_VOICE_PROFILE_PLAYBACK
    ) {
        throw std::invalid_argument("unsupported voice profile");
    }

    if (profile == TJI_SC_VOICE_PROFILE_PUSH_TO_TALK) {
        auto guarded = drop_release_guard_tail(pcm, size, kSampleRate);
        auto trimmed = trim_long_post_speech_silence(guarded, kSampleRate);
        float previous_input = 0.0f;
        float previous_high_pass = 0.0f;
        float previous_presence = 0.0f;
        float previous_agc_gain = 1.0f;
        auto processed = process_chain(
            trimmed.data(),
            trimmed.size(),
            profile,
            kSampleRate,
            tone,
            false,
            previous_input,
            previous_high_pass,
            previous_presence,
            previous_agc_gain,
            nullptr,
            nullptr
        );
        return with_ptt_tail_smoothing(std::move(processed), kSampleRate);
    }

    float previous_input = 0.0f;
    float previous_high_pass = 0.0f;
    float previous_presence = 0.0f;
    float previous_agc_gain = 1.0f;
    auto processed = process_chain(
        pcm,
        size,
        profile,
        sample_rate,
        tone,
        false,
        previous_input,
        previous_high_pass,
        previous_presence,
        previous_agc_gain,
        nullptr,
        nullptr
    );
    if (profile == TJI_SC_VOICE_PROFILE_PLAYBACK) {
        return with_tts_edge_smoothing(std::move(processed), sample_rate);
    }
    return processed;
}

} // namespace tji::speaker
