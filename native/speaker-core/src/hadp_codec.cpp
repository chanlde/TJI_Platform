#include "speaker_core_internal.h"

#include <algorithm>
#include <cstring>
#include <stdexcept>

namespace tji::speaker {
namespace {

constexpr size_t kRecordIdBytes = 64;
constexpr size_t kHeaderPrefixAndRecordIdBytes = 44 + kRecordIdBytes;
constexpr uint8_t kHadpMagic[4] = {'H', 'A', 'D', 'P'};

void copy_crc(char (&target)[11], const std::string &value) {
    std::memset(target, 0, sizeof(target));
    std::memcpy(target, value.c_str(), std::min(value.size(), sizeof(target) - 1));
}

std::vector<uint8_t> build_hadp_header(
    const std::string &record_id,
    int codec_id,
    int sample_rate,
    int channels,
    int packet_ms,
    int frame_bytes,
    int samples_per_frame,
    int frame_count,
    int audio_bytes,
    int duration_ms,
    uint32_t audio_crc
) {
    std::vector<uint8_t> header;
    header.reserve(kHadpHeaderBytes);
    header.insert(header.end(), std::begin(kHadpMagic), std::end(kHadpMagic));
    put_u16_le(header, kHadpVersion);
    put_u16_le(header, kHadpHeaderBytes);
    put_u16_le(header, static_cast<uint16_t>(codec_id));
    put_u16_le(header, 0);
    put_u32_le(header, static_cast<uint32_t>(sample_rate));
    put_u16_le(header, static_cast<uint16_t>(channels));
    put_u16_le(header, static_cast<uint16_t>(packet_ms));
    put_u16_le(header, static_cast<uint16_t>(frame_bytes));
    put_u16_le(header, static_cast<uint16_t>(samples_per_frame));
    put_u32_le(header, static_cast<uint32_t>(frame_count));
    put_u32_le(header, static_cast<uint32_t>(audio_bytes));
    put_u32_le(header, static_cast<uint32_t>(duration_ms));
    put_u32_le(header, audio_crc);
    put_u32_le(header, 0);

    const auto record_len = std::min(record_id.size(), kRecordIdBytes);
    header.insert(header.end(), record_id.begin(), record_id.begin() + static_cast<std::ptrdiff_t>(record_len));
    header.resize(kHeaderPrefixAndRecordIdBytes, 0);
    header.resize(kHadpHeaderBytes, 0);
    return header;
}

std::vector<std::vector<uint8_t>> encode_pcm16_frames(const uint8_t *pcm, size_t size, size_t frame_bytes) {
    std::vector<std::vector<uint8_t>> frames;
    for (size_t offset = 0; offset < size; offset += frame_bytes) {
        const auto end = std::min(offset + frame_bytes, size);
        frames.push_back(pad_frame(pcm + offset, end - offset, frame_bytes));
    }
    return frames;
}

std::vector<std::vector<uint8_t>> encode_adpcm_frames(const uint8_t *pcm, size_t size) {
    std::vector<std::vector<uint8_t>> frames;
    int step_index = 0;
    for (size_t offset = 0; offset < size; offset += kPcmFrameBytes) {
        const auto end = std::min(offset + static_cast<size_t>(kPcmFrameBytes), size);
        const auto frame = pad_frame(pcm + offset, end - offset, kPcmFrameBytes);
        auto encoded = encode_ima_adpcm_block(frame.data(), frame.size(), step_index);
        step_index = encoded.next_step_index;
        frames.push_back(std::move(encoded.payload));
    }
    return frames;
}

} // namespace

HadpResult encode_hadp(
    const uint8_t *pcm,
    size_t size,
    const std::string &record_id,
    int codec_id,
    int sample_rate,
    int channels,
    int packet_ms
) {
    if (pcm == nullptr || size < 2) {
        throw std::invalid_argument("PCM is empty");
    }
    if (channels != 1 || packet_ms <= 0 || sample_rate <= 0) {
        throw std::invalid_argument("HADP metadata is invalid");
    }

    const size_t aligned_size = size - (size % 2);
    const int samples_per_frame = sample_rate * packet_ms / 1000;
    const int pcm_frame_bytes = samples_per_frame * 2;
    int frame_bytes = 0;
    std::vector<std::vector<uint8_t>> frames;

    if (codec_id == TJI_SC_CODEC_IMA_ADPCM) {
        if (sample_rate != kSampleRate || packet_ms != kPacketMs) {
            throw std::invalid_argument("ADPCM HADP supports only 8k/40ms");
        }
        frame_bytes = kAdpcmFrameBytes;
        frames = encode_adpcm_frames(pcm, aligned_size);
    } else if (codec_id == TJI_SC_CODEC_PCM16) {
        frame_bytes = pcm_frame_bytes;
        frames = encode_pcm16_frames(pcm, aligned_size, static_cast<size_t>(pcm_frame_bytes));
    } else {
        throw std::invalid_argument("Unsupported HADP codec");
    }

    std::vector<uint8_t> audio;
    for (const auto &frame : frames) {
        audio.insert(audio.end(), frame.begin(), frame.end());
    }
    const int frame_count = static_cast<int>(frames.size());
    const int duration_ms = frame_count * packet_ms;
    const auto audio_crc = crc32(audio.data(), audio.size());
    auto header = build_hadp_header(
        record_id,
        codec_id,
        sample_rate,
        channels,
        packet_ms,
        frame_bytes,
        samples_per_frame,
        frame_count,
        static_cast<int>(audio.size()),
        duration_ms,
        audio_crc
    );
    std::vector<uint8_t> file = header;
    file.insert(file.end(), audio.begin(), audio.end());
    const auto file_crc = crc32(file.data(), file.size());

    TjiScHadpMetadata metadata{};
    metadata.codec_id = codec_id;
    metadata.sample_rate = sample_rate;
    metadata.channels = channels;
    metadata.packet_ms = packet_ms;
    metadata.frame_bytes = frame_bytes;
    metadata.samples_per_frame = samples_per_frame;
    metadata.frame_count = frame_count;
    metadata.audio_bytes = static_cast<int>(audio.size());
    metadata.duration_ms = duration_ms;
    metadata.file_size = static_cast<int>(file.size());
    copy_crc(metadata.crc32, format_crc32(file_crc));
    copy_crc(metadata.audio_crc32, format_crc32(audio_crc));

    return HadpResult{std::move(file), metadata};
}

std::vector<uint8_t> decode_hadp_pcm16(const uint8_t *data, size_t size) {
    if (data == nullptr || size < kHadpHeaderBytes) {
        throw std::invalid_argument("HADP file is too short");
    }
    if (std::memcmp(data, kHadpMagic, sizeof(kHadpMagic)) != 0) {
        throw std::invalid_argument("HADP magic mismatch");
    }
    const auto version = read_u16_le(data + 4);
    const auto header_bytes = read_u16_le(data + 6);
    const auto codec_id = read_u16_le(data + 8);
    const auto sample_rate = static_cast<int>(read_u32_le(data + 12));
    const auto channels = static_cast<int>(read_u16_le(data + 16));
    const auto packet_ms = static_cast<int>(read_u16_le(data + 18));
    const auto frame_bytes = static_cast<int>(read_u16_le(data + 20));
    const auto samples_per_frame = static_cast<int>(read_u16_le(data + 22));
    const auto frame_count = static_cast<int>(read_u32_le(data + 24));
    const auto audio_bytes = static_cast<int>(read_u32_le(data + 28));

    if (version != kHadpVersion || header_bytes != kHadpHeaderBytes) {
        throw std::invalid_argument("Unsupported HADP version");
    }
    if (channels != 1 || packet_ms <= 0 || sample_rate <= 0 || frame_count < 0 || audio_bytes < 0) {
        throw std::invalid_argument("Invalid HADP metadata");
    }
    if (kHadpHeaderBytes + static_cast<size_t>(audio_bytes) > size) {
        throw std::invalid_argument("Invalid HADP audio length");
    }

    const uint8_t *audio = data + kHadpHeaderBytes;
    if (codec_id == TJI_SC_CODEC_PCM16) {
        if (frame_bytes != samples_per_frame * 2) {
            throw std::invalid_argument("Invalid PCM16 frame shape");
        }
        return std::vector<uint8_t>(audio, audio + audio_bytes);
    }
    if (codec_id == TJI_SC_CODEC_IMA_ADPCM) {
        if (sample_rate != kSampleRate || packet_ms != kPacketMs || frame_bytes != kAdpcmFrameBytes) {
            throw std::invalid_argument("Invalid ADPCM frame shape");
        }
        std::vector<uint8_t> pcm;
        pcm.reserve(static_cast<size_t>(frame_count) * samples_per_frame * 2);
        size_t offset = 0;
        for (int i = 0; i < frame_count; ++i) {
            if (offset + kAdpcmFrameBytes > static_cast<size_t>(audio_bytes)) {
                throw std::invalid_argument("Incomplete ADPCM HADP frame");
            }
            auto frame = decode_ima_adpcm_block(audio + offset, kAdpcmFrameBytes, samples_per_frame);
            pcm.insert(pcm.end(), frame.begin(), frame.end());
            offset += kAdpcmFrameBytes;
        }
        return pcm;
    }
    throw std::invalid_argument("Unsupported HADP codec");
}

} // namespace tji::speaker

