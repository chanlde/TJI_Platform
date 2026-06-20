#include "speaker_core_internal.h"

#include <algorithm>
#include <array>
#include <stdexcept>

namespace tji::speaker {
namespace {

constexpr std::array<int, 16> kImaIndexTable = {
    -1, -1, -1, -1, 2, 4, 6, 8,
    -1, -1, -1, -1, 2, 4, 6, 8
};

constexpr std::array<int, 89> kImaStepTable = {
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
    19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
    130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
    337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
    876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
    2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
    5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
    15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
};

int decode_nibble(int nibble, int predictor, int index) {
    const int step = kImaStepTable[static_cast<size_t>(index)];
    int delta = step >> 3;
    if ((nibble & 4) != 0) delta += step;
    if ((nibble & 2) != 0) delta += step >> 1;
    if ((nibble & 1) != 0) delta += step >> 2;
    const int next = ((nibble & 8) != 0) ? predictor - delta : predictor + delta;
    return std::clamp(next, static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
}

} // namespace

EncodedAdpcm encode_ima_adpcm_block(const uint8_t *pcm, size_t length, int initial_step_index) {
    if (pcm == nullptr || length < 2) {
        throw std::invalid_argument("PCM block is empty");
    }
    const size_t aligned = length - (length % 2);
    const int sample_count = static_cast<int>(aligned / 2);
    int predictor = read_i16_le(pcm);
    int index = std::clamp(initial_step_index, 0, 88);

    std::vector<uint8_t> out;
    out.reserve(4 + sample_count / 2);
    put_u16_le(out, static_cast<uint16_t>(static_cast<int16_t>(predictor)));
    put_u8(out, static_cast<uint8_t>(index));
    put_u8(out, 0);

    int pending = -1;
    for (int sample_index = 1; sample_index < sample_count; ++sample_index) {
        const int sample = read_i16_le(pcm + sample_index * 2);
        const int step = kImaStepTable[static_cast<size_t>(index)];
        int diff = sample - predictor;
        int nibble = 0;
        if (diff < 0) {
            nibble = 8;
            diff = -diff;
        }

        int delta = step >> 3;
        if (diff >= step) {
            nibble |= 4;
            diff -= step;
            delta += step;
        }
        if (diff >= (step >> 1)) {
            nibble |= 2;
            diff -= step >> 1;
            delta += step >> 1;
        }
        if (diff >= (step >> 2)) {
            nibble |= 1;
            delta += step >> 2;
        }

        predictor = ((nibble & 8) != 0) ? predictor - delta : predictor + delta;
        predictor = std::clamp(predictor, static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
        index = std::clamp(index + kImaIndexTable[static_cast<size_t>(nibble)], 0, 88);

        if (pending < 0) {
            pending = nibble & 0x0F;
        } else {
            out.push_back(static_cast<uint8_t>(pending | ((nibble & 0x0F) << 4)));
            pending = -1;
        }
    }
    if (pending >= 0) {
        out.push_back(static_cast<uint8_t>(pending));
    }

    return EncodedAdpcm{std::move(out), sample_count, index};
}

std::vector<uint8_t> decode_ima_adpcm_block(const uint8_t *block, size_t block_size, int expected_samples) {
    if (block == nullptr || block_size < 4 || expected_samples <= 0) {
        throw std::invalid_argument("ADPCM block is invalid");
    }
    int predictor = read_i16_le(block);
    int index = std::clamp(static_cast<int>(block[2]), 0, 88);
    std::vector<int> samples(static_cast<size_t>(expected_samples), 0);
    samples[0] = predictor;
    int sample_index = 1;
    size_t payload_offset = 4;
    const size_t payload_end = std::min(block_size, static_cast<size_t>(kAdpcmFrameBytes));

    while (payload_offset < payload_end && sample_index < expected_samples) {
        const int packed = block[payload_offset] & 0xFF;
        const int low = packed & 0x0F;
        predictor = decode_nibble(low, predictor, index);
        index = std::clamp(index + kImaIndexTable[static_cast<size_t>(low)], 0, 88);
        samples[static_cast<size_t>(sample_index++)] = predictor;

        if (sample_index >= expected_samples) break;

        const int high = (packed >> 4) & 0x0F;
        predictor = decode_nibble(high, predictor, index);
        index = std::clamp(index + kImaIndexTable[static_cast<size_t>(high)], 0, 88);
        samples[static_cast<size_t>(sample_index++)] = predictor;
        payload_offset += 1;
    }

    std::vector<uint8_t> pcm(static_cast<size_t>(expected_samples) * 2, 0);
    for (int i = 0; i < expected_samples; ++i) {
        const int value = std::clamp(samples[static_cast<size_t>(i)], static_cast<int>(INT16_MIN), static_cast<int>(INT16_MAX));
        pcm[static_cast<size_t>(i) * 2] = static_cast<uint8_t>(value & 0xFF);
        pcm[static_cast<size_t>(i) * 2 + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
    }
    return pcm;
}

} // namespace tji::speaker

