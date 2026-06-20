#pragma once

#include "tji_speaker_core.h"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace tji::speaker {

constexpr int kSampleRate = 8000;
constexpr int kChannels = 1;
constexpr int kPacketMs = 40;
constexpr int kPcmFrameBytes = kSampleRate * kPacketMs / 1000 * 2;
constexpr int kLegacyUdpHeaderBytes = 20;
constexpr int kV2FixedHeaderBytes = 28;
constexpr int kHadpHeaderBytes = 128;
constexpr int kHadpVersion = 1;
constexpr int kAdpcmFrameBytes = 164;
constexpr int kAdpcmSamplesPerFrame = 320;
constexpr uint16_t kUdpMagic = 0xA55A;

struct EncodedAdpcm {
    std::vector<uint8_t> payload;
    int sample_count = 0;
    int next_step_index = 0;
};

struct HadpResult {
    std::vector<uint8_t> data;
    TjiScHadpMetadata metadata{};
};

struct PacketizedAdpcm {
    std::vector<uint8_t> packet;
    int next_step_index = 0;
};

EncodedAdpcm encode_ima_adpcm_block(const uint8_t *pcm, size_t length, int initial_step_index);
std::vector<uint8_t> decode_ima_adpcm_block(const uint8_t *block, size_t block_size, int expected_samples);
std::vector<uint8_t> packetize_adpcm_legacy(const uint8_t *pcm, size_t size, uint32_t sequence, uint32_t timestamp_samples);
PacketizedAdpcm packetize_adpcm_legacy(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    int initial_step_index
);
std::vector<uint8_t> packetize_adpcm_v2(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const std::string &device_id,
    const std::string &task_id,
    const std::string &talk_id,
    int stream_type,
    bool is_last_packet
);
PacketizedAdpcm packetize_adpcm_v2(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const std::string &device_id,
    const std::string &task_id,
    const std::string &talk_id,
    int stream_type,
    bool is_last_packet,
    int initial_step_index
);
HadpResult encode_hadp(
    const uint8_t *pcm,
    size_t size,
    const std::string &record_id,
    int codec_id,
    int sample_rate,
    int channels,
    int packet_ms
);
std::vector<uint8_t> decode_hadp_pcm16(const uint8_t *data, size_t size);

uint32_t crc32(const uint8_t *data, size_t size);
std::string format_crc32(uint32_t value);
std::vector<uint8_t> pad_frame(const uint8_t *data, size_t size, size_t frame_size);

void put_u8(std::vector<uint8_t> &out, uint8_t value);
void put_u16_le(std::vector<uint8_t> &out, uint16_t value);
void put_u32_le(std::vector<uint8_t> &out, uint32_t value);
uint16_t read_u16_le(const uint8_t *data);
int16_t read_i16_le(const uint8_t *data);
uint32_t read_u32_le(const uint8_t *data);

} // namespace tji::speaker
