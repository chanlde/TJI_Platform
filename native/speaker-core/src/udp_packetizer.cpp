#include "speaker_core_internal.h"

#include <algorithm>

namespace tji::speaker {
namespace {

constexpr uint8_t kLegacyVersion = 1;
constexpr uint8_t kV2Version = 2;
constexpr uint8_t kCodecImaAdpcm = 1;
constexpr uint16_t kFlagLastPacket = 0x01;
constexpr uint16_t kFlagStoreToSd = 0x02;
constexpr uint16_t kFlagPlayback = 0x04;
constexpr uint16_t kFlagFeedback = 0x08;
constexpr size_t kMaxIdBytes = 255;

std::string capped_id(const std::string &value) {
    return value.substr(0, std::min(value.size(), kMaxIdBytes));
}

uint16_t flags_for(int stream_type, bool is_last_packet) {
    uint16_t flags = is_last_packet ? kFlagLastPacket : 0;
    switch (stream_type) {
        case TJI_SC_STREAM_RECORD_STORE:
            flags |= kFlagStoreToSd;
            break;
        case TJI_SC_STREAM_PLAYBACK_FEEDBACK:
            flags |= kFlagPlayback | kFlagFeedback;
            break;
        case TJI_SC_STREAM_PLAYBACK:
        default:
            flags |= kFlagPlayback;
            break;
    }
    return flags;
}

} // namespace

PacketizedAdpcm packetize_adpcm_legacy(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    int initial_step_index
) {
    const auto encoded = encode_ima_adpcm_block(pcm, size, initial_step_index);
    std::vector<uint8_t> packet;
    packet.reserve(kLegacyUdpHeaderBytes + encoded.payload.size());
    put_u16_le(packet, kUdpMagic);
    put_u8(packet, kLegacyVersion);
    put_u8(packet, kCodecImaAdpcm);
    put_u32_le(packet, sequence);
    put_u32_le(packet, timestamp_samples);
    put_u16_le(packet, kSampleRate);
    put_u8(packet, kChannels);
    put_u8(packet, 0);
    put_u16_le(packet, static_cast<uint16_t>(encoded.payload.size()));
    put_u16_le(packet, static_cast<uint16_t>(encoded.sample_count));
    packet.insert(packet.end(), encoded.payload.begin(), encoded.payload.end());
    return PacketizedAdpcm{std::move(packet), encoded.next_step_index};
}

std::vector<uint8_t> packetize_adpcm_legacy(const uint8_t *pcm, size_t size, uint32_t sequence, uint32_t timestamp_samples) {
    return packetize_adpcm_legacy(pcm, size, sequence, timestamp_samples, 0).packet;
}

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
) {
    const auto encoded = encode_ima_adpcm_block(pcm, size, initial_step_index);
    const auto device = capped_id(device_id);
    const auto task = capped_id(task_id);
    const auto talk = capped_id(talk_id);
    const auto header_len = static_cast<uint16_t>(kV2FixedHeaderBytes + device.size() + task.size() + talk.size());
    std::vector<uint8_t> packet;
    packet.reserve(header_len + encoded.payload.size());
    put_u16_le(packet, kUdpMagic);
    put_u8(packet, kV2Version);
    put_u8(packet, kCodecImaAdpcm);
    put_u16_le(packet, header_len);
    put_u16_le(packet, flags_for(stream_type, is_last_packet));
    put_u32_le(packet, sequence);
    put_u32_le(packet, timestamp_ms);
    put_u16_le(packet, kSampleRate);
    put_u8(packet, kChannels);
    put_u8(packet, kPacketMs);
    put_u16_le(packet, static_cast<uint16_t>(encoded.payload.size()));
    put_u16_le(packet, static_cast<uint16_t>(encoded.sample_count));
    put_u8(packet, static_cast<uint8_t>(device.size()));
    put_u8(packet, static_cast<uint8_t>(task.size()));
    put_u8(packet, static_cast<uint8_t>(talk.size()));
    put_u8(packet, 0);
    packet.insert(packet.end(), device.begin(), device.end());
    packet.insert(packet.end(), task.begin(), task.end());
    packet.insert(packet.end(), talk.begin(), talk.end());
    packet.insert(packet.end(), encoded.payload.begin(), encoded.payload.end());
    return PacketizedAdpcm{std::move(packet), encoded.next_step_index};
}

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
) {
    return packetize_adpcm_v2(
        pcm,
        size,
        sequence,
        timestamp_ms,
        device_id,
        task_id,
        talk_id,
        stream_type,
        is_last_packet,
        0
    ).packet;
}

} // namespace tji::speaker
