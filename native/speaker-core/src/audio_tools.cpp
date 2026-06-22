#include "speaker_core_internal.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <vector>

namespace tji::speaker {
namespace {

constexpr int kBytesPerPcm16Sample = 2;
constexpr int kMillisPerSecond = 1000;
constexpr double kPi = 3.14159265358979323846;

void put_i16_le(std::vector<uint8_t> &out, size_t sample_index, int value) {
    value = std::clamp(value, static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
    const size_t offset = sample_index * kBytesPerPcm16Sample;
    out[offset] = static_cast<uint8_t>(value & 0xFF);
    out[offset + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
}

int read_i16_le_at(const uint8_t *data, size_t offset) {
    return read_i16_le(data + offset);
}

uint16_t read_u16_le_at(const uint8_t *data, size_t offset) {
    return read_u16_le(data + offset);
}

uint32_t read_u32_le_at(const uint8_t *data, size_t offset) {
    return read_u32_le(data + offset);
}

bool chunk_id_equals(const uint8_t *data, size_t offset, const char (&id)[5]) {
    return std::memcmp(data + offset, id, 4) == 0;
}

std::vector<uint8_t> resample_mono_samples_for_tts(
    const std::vector<int> &samples,
    int source_sample_rate,
    int target_sample_rate
) {
    if (samples.empty()) return {};
    const size_t output_samples = std::max<size_t>(
        1,
        static_cast<size_t>(
            static_cast<uint64_t>(samples.size()) * static_cast<uint64_t>(target_sample_rate) /
            static_cast<uint64_t>(source_sample_rate)
        )
    );
    std::vector<uint8_t> output(output_samples * kBytesPerPcm16Sample);
    if (source_sample_rate == target_sample_rate) {
        for (size_t index = 0; index < output_samples; ++index) {
            put_i16_le(output, index, samples[std::min(index, samples.size() - 1)]);
        }
    } else if (source_sample_rate > target_sample_rate) {
        const float ratio = static_cast<float>(source_sample_rate) / static_cast<float>(target_sample_rate);
        size_t source_index = 0;
        for (size_t index = 0; index < output_samples; ++index) {
            const size_t lower_bound = std::min(source_index + 1, samples.size());
            const size_t next_index = std::clamp<size_t>(
                static_cast<size_t>(std::lround(static_cast<float>(index + 1) * ratio)),
                lower_bound,
                samples.size()
            );
            int64_t sum = 0;
            int count = 0;
            while (source_index < next_index) {
                sum += samples[source_index];
                ++source_index;
                ++count;
            }
            const int value = count > 0
                ? static_cast<int>(sum / count)
                : samples[std::clamp<size_t>(source_index, 0, samples.size() - 1)];
            put_i16_le(output, index, value);
        }
    } else {
        for (size_t index = 0; index < output_samples; ++index) {
            const float source_position =
                static_cast<float>(index) * static_cast<float>(source_sample_rate) /
                static_cast<float>(target_sample_rate);
            const auto base = static_cast<size_t>(source_position);
            const size_t left = std::min(base, samples.size() - 1);
            const size_t right = std::min(left + 1, samples.size() - 1);
            const float fraction = source_position - static_cast<float>(left);
            const int value = static_cast<int>(std::lround(
                static_cast<float>(samples[left]) +
                static_cast<float>(samples[right] - samples[left]) * fraction
            ));
            put_i16_le(output, index, value);
        }
    }
    return output;
}

} // namespace

std::vector<uint8_t> resample_pcm16(
    const uint8_t *pcm,
    size_t size,
    int source_sample_rate,
    int target_sample_rate
) {
    if (pcm == nullptr && size > 0) throw std::invalid_argument("pcm is null");
    if (source_sample_rate <= 0 || target_sample_rate <= 0) {
        throw std::invalid_argument("sample rates must be positive");
    }
    const size_t source_samples = size / kBytesPerPcm16Sample;
    if (source_samples == 0) return {};
    if (source_sample_rate == target_sample_rate) {
        return std::vector<uint8_t>(pcm, pcm + source_samples * kBytesPerPcm16Sample);
    }
    const size_t target_samples = std::max<size_t>(
        1,
        static_cast<size_t>(
            static_cast<uint64_t>(source_samples) * static_cast<uint64_t>(target_sample_rate) /
            static_cast<uint64_t>(source_sample_rate)
        )
    );
    std::vector<uint8_t> output(target_samples * kBytesPerPcm16Sample);
    for (size_t index = 0; index < target_samples; ++index) {
        const double source_position =
            static_cast<double>(index) * static_cast<double>(source_sample_rate) /
            static_cast<double>(target_sample_rate);
        const auto left = static_cast<size_t>(std::floor(source_position));
        const size_t clamped_left = std::min(left, source_samples - 1);
        const size_t right = std::min(clamped_left + 1, source_samples - 1);
        const double fraction = source_position - static_cast<double>(clamped_left);
        const int left_sample = read_i16_le(pcm + clamped_left * kBytesPerPcm16Sample);
        const int right_sample = read_i16_le(pcm + right * kBytesPerPcm16Sample);
        const int sample = static_cast<int>(std::lround(
            static_cast<double>(left_sample) +
            static_cast<double>(right_sample - left_sample) * fraction
        ));
        put_i16_le(output, index, sample);
    }
    return output;
}

std::vector<uint8_t> generate_tone_pcm16(
    int frequency_hz,
    int duration_ms,
    int sample_rate,
    int min_duration_ms,
    int fade_ms,
    float amplitude
) {
    if (frequency_hz <= 0 || sample_rate <= 0) {
        throw std::invalid_argument("tone frequency and sample rate must be positive");
    }
    const int safe_duration_ms = std::max(duration_ms, min_duration_ms);
    const size_t sample_count = static_cast<size_t>(
        static_cast<int64_t>(sample_rate) * static_cast<int64_t>(safe_duration_ms) / kMillisPerSecond
    );
    const int fade_samples = sample_rate * std::max(fade_ms, 0) / kMillisPerSecond;
    const double safe_amplitude = std::clamp(amplitude, 0.0f, 1.0f);
    std::vector<uint8_t> pcm(sample_count * kBytesPerPcm16Sample);
    for (size_t index = 0; index < sample_count; ++index) {
        const double fade_in = fade_samples > 0
            ? std::clamp(static_cast<double>(index) / static_cast<double>(fade_samples), 0.0, 1.0)
            : 1.0;
        const double fade_out = fade_samples > 0
            ? std::clamp(static_cast<double>(sample_count - index - 1) / static_cast<double>(fade_samples), 0.0, 1.0)
            : 1.0;
        const double envelope = std::min(fade_in, fade_out);
        const double phase =
            2.0 * kPi * static_cast<double>(frequency_hz) * static_cast<double>(index) /
            static_cast<double>(sample_rate);
        const int sample = static_cast<int>(std::lround(
            std::sin(phase) * static_cast<double>(INT16_MAX) * safe_amplitude * envelope
        ));
        put_i16_le(pcm, index, sample);
    }
    return pcm;
}

std::vector<uint8_t> prepend_silence_pcm16(
    const uint8_t *pcm,
    size_t size,
    int duration_ms,
    int sample_rate
) {
    if (pcm == nullptr && size > 0) throw std::invalid_argument("pcm is null");
    if (sample_rate <= 0) throw std::invalid_argument("sample rate must be positive");
    const size_t aligned_size = size - (size % kBytesPerPcm16Sample);
    const size_t silence_bytes = static_cast<size_t>(
        static_cast<int64_t>(sample_rate) * static_cast<int64_t>(std::max(duration_ms, 0)) /
        kMillisPerSecond
    ) * kBytesPerPcm16Sample;
    std::vector<uint8_t> out(silence_bytes + aligned_size, 0);
    if (aligned_size > 0) {
        std::copy(pcm, pcm + aligned_size, out.begin() + static_cast<std::ptrdiff_t>(silence_bytes));
    }
    return out;
}

std::vector<uint8_t> pad_pcm16_to_frame(
    const uint8_t *pcm,
    size_t size,
    size_t frame_bytes
) {
    if (pcm == nullptr && size > 0) throw std::invalid_argument("pcm is null");
    if (frame_bytes == 0) throw std::invalid_argument("frame bytes must be positive");
    const size_t aligned_size = size - (size % kBytesPerPcm16Sample);
    const size_t remainder = aligned_size % frame_bytes;
    const size_t padded_size = remainder == 0 ? aligned_size : aligned_size + frame_bytes - remainder;
    std::vector<uint8_t> out(padded_size, 0);
    if (aligned_size > 0) {
        std::copy(pcm, pcm + aligned_size, out.begin());
    }
    return out;
}

std::vector<uint8_t> decode_wav_pcm16_mono(
    const uint8_t *wav,
    size_t size,
    int target_sample_rate
) {
    if (wav == nullptr && size > 0) throw std::invalid_argument("wav is null");
    if (target_sample_rate <= 0) throw std::invalid_argument("target sample rate must be positive");
    if (size < 44) throw std::invalid_argument("wav is too small");
    if (!chunk_id_equals(wav, 0, "RIFF") || !chunk_id_equals(wav, 8, "WAVE")) {
        throw std::invalid_argument("wav header is invalid");
    }

    int channels = 1;
    int sample_rate = kSampleRate;
    int bits_per_sample = 16;
    size_t data_offset = 0;
    size_t data_size = 0;
    bool found_data = false;

    size_t offset = 12;
    while (offset + 8 <= size) {
        const uint32_t chunk_size = read_u32_le_at(wav, offset + 4);
        const size_t chunk_data = offset + 8;
        if (chunk_data > size || chunk_size > size - chunk_data) break;
        if (chunk_id_equals(wav, offset, "fmt ")) {
            if (chunk_size < 16) throw std::invalid_argument("wav fmt chunk is too small");
            const int audio_format = read_u16_le_at(wav, chunk_data);
            if (audio_format != 1) throw std::invalid_argument("wav is not pcm");
            channels = std::max<int>(1, read_u16_le_at(wav, chunk_data + 2));
            sample_rate = std::max<int>(1, static_cast<int>(read_u32_le_at(wav, chunk_data + 4)));
            bits_per_sample = read_u16_le_at(wav, chunk_data + 14);
        } else if (chunk_id_equals(wav, offset, "data")) {
            data_offset = chunk_data;
            data_size = chunk_size;
            found_data = true;
            break;
        }
        offset = chunk_data + chunk_size + (chunk_size & 1U);
    }

    if (!found_data || data_size == 0) throw std::invalid_argument("wav has no data chunk");
    if (bits_per_sample != 16) throw std::invalid_argument("wav is not 16-bit pcm");
    const size_t frame_bytes = static_cast<size_t>(channels) * kBytesPerPcm16Sample;
    const size_t input_frames = data_size / frame_bytes;
    if (input_frames == 0) return {};

    std::vector<int> mono(input_frames);
    size_t cursor = data_offset;
    for (size_t frame = 0; frame < input_frames; ++frame) {
        int mixed = 0;
        for (int channel = 0; channel < channels; ++channel) {
            mixed += read_i16_le_at(wav, cursor);
            cursor += kBytesPerPcm16Sample;
        }
        mono[frame] = std::clamp(mixed / channels, static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
    }

    return resample_mono_samples_for_tts(mono, sample_rate, target_sample_rate);
}

std::vector<uint8_t> float32_to_pcm16(
    const float *samples,
    size_t sample_count,
    int source_sample_rate,
    int target_sample_rate
) {
    if (samples == nullptr && sample_count > 0) throw std::invalid_argument("samples is null");
    if (source_sample_rate <= 0 || target_sample_rate <= 0) {
        throw std::invalid_argument("sample rates must be positive");
    }
    if (sample_count == 0) return {};
    const size_t target_samples = source_sample_rate == target_sample_rate
        ? sample_count
        : std::max<size_t>(
            1,
            static_cast<size_t>(
                static_cast<uint64_t>(sample_count) * static_cast<uint64_t>(target_sample_rate) /
                static_cast<uint64_t>(source_sample_rate)
            )
        );
    std::vector<uint8_t> pcm(target_samples * kBytesPerPcm16Sample);
    for (size_t index = 0; index < target_samples; ++index) {
        const double source_position = source_sample_rate == target_sample_rate
            ? static_cast<double>(index)
            : static_cast<double>(index) * static_cast<double>(source_sample_rate) /
                static_cast<double>(target_sample_rate);
        const auto left = static_cast<size_t>(std::floor(source_position));
        const size_t clamped_left = std::min(left, sample_count - 1);
        const size_t right = std::min(clamped_left + 1, sample_count - 1);
        const double fraction = source_position - static_cast<double>(clamped_left);
        const double sample =
            static_cast<double>(samples[clamped_left]) +
            static_cast<double>(samples[right] - samples[clamped_left]) * fraction;
        const double clamped = std::clamp(sample, -1.0, 1.0);
        const int value = clamped < 0.0
            ? static_cast<int>(std::lround(clamped * 32768.0))
            : static_cast<int>(std::lround(clamped * 32767.0));
        put_i16_le(pcm, index, value);
    }
    return pcm;
}

} // namespace tji::speaker
