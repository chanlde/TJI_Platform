#include "speaker_core_internal.h"

#include <opus.h>

#include <algorithm>
#include <array>
#include <cstring>
#include <memory>
#include <stdexcept>

namespace tji::speaker {
namespace {

constexpr std::array<uint8_t, 8> kOpusHead{
    'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'
};
constexpr std::array<uint8_t, 8> kOpusTags{
    'O', 'p', 'u', 's', 'T', 'a', 'g', 's'
};
constexpr char kVendor[] = "TJI speaker-core/libopus 1.6.1";
constexpr uint32_t kOggSerial = 0x544A4901U;
constexpr size_t kMaxOpusPacketBytes = 1275U;

void put_u64_le(std::vector<uint8_t> &out, uint64_t value)
{
    for (unsigned shift = 0; shift < 64; shift += 8) {
        out.push_back(static_cast<uint8_t>((value >> shift) & 0xFFU));
    }
}

uint32_t ogg_crc(const uint8_t *data, size_t size)
{
    uint32_t crc = 0;
    for (size_t i = 0; i < size; ++i) {
        crc ^= static_cast<uint32_t>(data[i]) << 24;
        for (int bit = 0; bit < 8; ++bit) {
            crc = (crc & 0x80000000U) != 0U
                      ? (crc << 1U) ^ 0x04C11DB7U
                      : crc << 1U;
        }
    }
    return crc;
}

void append_ogg_page(std::vector<uint8_t> &file,
                     const std::vector<uint8_t> &packet,
                     uint8_t header_type,
                     uint64_t granule_position,
                     uint32_t sequence)
{
    std::vector<uint8_t> page;
    std::vector<uint8_t> lacing;
    size_t remaining = packet.size();

    do {
        const auto segment = std::min<size_t>(remaining, 255U);
        lacing.push_back(static_cast<uint8_t>(segment));
        remaining -= segment;
    } while (remaining > 0U);
    if (!packet.empty() && (packet.size() % 255U) == 0U) {
        lacing.push_back(0U);
    }
    if (lacing.size() > 255U) {
        throw std::invalid_argument("Opus packet is too large for one Ogg page");
    }

    page.reserve(27U + lacing.size() + packet.size());
    page.insert(page.end(), {'O', 'g', 'g', 'S'});
    put_u8(page, 0U);
    put_u8(page, header_type);
    put_u64_le(page, granule_position);
    put_u32_le(page, kOggSerial);
    put_u32_le(page, sequence);
    put_u32_le(page, 0U);
    put_u8(page, static_cast<uint8_t>(lacing.size()));
    page.insert(page.end(), lacing.begin(), lacing.end());
    page.insert(page.end(), packet.begin(), packet.end());

    const uint32_t checksum = ogg_crc(page.data(), page.size());
    page[22] = static_cast<uint8_t>(checksum);
    page[23] = static_cast<uint8_t>(checksum >> 8);
    page[24] = static_cast<uint8_t>(checksum >> 16);
    page[25] = static_cast<uint8_t>(checksum >> 24);
    file.insert(file.end(), page.begin(), page.end());
}

std::vector<uint8_t> build_opus_head(int channels,
                                     int pre_skip_48k,
                                     int input_sample_rate)
{
    std::vector<uint8_t> packet(kOpusHead.begin(), kOpusHead.end());
    put_u8(packet, 1U);
    put_u8(packet, static_cast<uint8_t>(channels));
    put_u16_le(packet, static_cast<uint16_t>(pre_skip_48k));
    put_u32_le(packet, static_cast<uint32_t>(input_sample_rate));
    put_u16_le(packet, 0U);
    put_u8(packet, 0U);
    return packet;
}

std::vector<uint8_t> build_opus_tags(const std::string &record_id)
{
    std::vector<uint8_t> packet(kOpusTags.begin(), kOpusTags.end());
    const std::string comment = "RECORD_ID=" + record_id;
    put_u32_le(packet, static_cast<uint32_t>(std::strlen(kVendor)));
    packet.insert(packet.end(), kVendor, kVendor + std::strlen(kVendor));
    put_u32_le(packet, 1U);
    put_u32_le(packet, static_cast<uint32_t>(comment.size()));
    packet.insert(packet.end(), comment.begin(), comment.end());
    return packet;
}

void copy_crc(char (&target)[11], uint32_t value)
{
    const auto text = format_crc32(value);
    std::memset(target, 0, sizeof(target));
    std::memcpy(target, text.data(), std::min(text.size(), sizeof(target) - 1U));
}

} // namespace

OggOpusResult encode_ogg_opus(const uint8_t *pcm,
                              size_t size,
                              const std::string &record_id,
                              int sample_rate,
                              int channels,
                              int packet_ms,
                              int bitrate)
{
    if (pcm == nullptr || size < 2U || channels != 1 ||
        packet_ms != kOpusPacketMs || bitrate < 6000 || bitrate > 128000 ||
        (sample_rate != 8000 && sample_rate != 12000 &&
         sample_rate != 16000 && sample_rate != 24000 &&
         sample_rate != 48000)) {
        throw std::invalid_argument("Invalid Ogg Opus input");
    }

    int error = OPUS_OK;
    using EncoderPtr = std::unique_ptr<OpusEncoder, decltype(&opus_encoder_destroy)>;
    EncoderPtr encoder(
        opus_encoder_create(sample_rate, channels, OPUS_APPLICATION_VOIP, &error),
        &opus_encoder_destroy
    );
    if (encoder == nullptr || error != OPUS_OK ||
        opus_encoder_ctl(encoder.get(), OPUS_SET_BITRATE(bitrate)) != OPUS_OK ||
        opus_encoder_ctl(encoder.get(), OPUS_SET_VBR(1)) != OPUS_OK ||
        opus_encoder_ctl(encoder.get(), OPUS_SET_COMPLEXITY(5)) != OPUS_OK) {
        throw std::runtime_error("Opus encoder initialization failed");
    }

    int lookahead = 0;
    if (opus_encoder_ctl(encoder.get(), OPUS_GET_LOOKAHEAD(&lookahead)) != OPUS_OK) {
        throw std::runtime_error("Opus lookahead query failed");
    }
    const int pre_skip_48k = lookahead * 48000 / sample_rate;
    const int samples_per_frame = sample_rate * packet_ms / 1000;
    const size_t aligned_bytes = size - (size % 2U);
    const size_t input_samples = aligned_bytes / 2U;
    const size_t encoded_samples =
        input_samples + static_cast<size_t>(lookahead);
    const int encoded_packet_count = static_cast<int>(
        (encoded_samples + static_cast<size_t>(samples_per_frame) - 1U) /
        static_cast<size_t>(samples_per_frame)
    );
    const int output_packet_count = static_cast<int>(
        (input_samples + static_cast<size_t>(samples_per_frame) - 1U) /
        static_cast<size_t>(samples_per_frame)
    );

    std::vector<uint8_t> file;
    append_ogg_page(file, build_opus_head(channels, pre_skip_48k, sample_rate),
                    0x02U, 0U, 0U);
    append_ogg_page(file, build_opus_tags(record_id), 0U, 0U, 1U);

    std::vector<opus_int16> frame(static_cast<size_t>(samples_per_frame), 0);
    std::array<uint8_t, kMaxOpusPacketBytes> encoded{};
    size_t audio_bytes = 0U;
    for (int packet_index = 0;
         packet_index < encoded_packet_count;
         ++packet_index) {
        const size_t encoded_offset =
            static_cast<size_t>(packet_index) * samples_per_frame;
        std::fill(frame.begin(), frame.end(), 0);
        for (size_t i = 0; i < static_cast<size_t>(samples_per_frame); ++i) {
            const size_t encoded_index = encoded_offset + i;
            if (encoded_index >= static_cast<size_t>(lookahead)) {
                const size_t input_index =
                    encoded_index - static_cast<size_t>(lookahead);
                if (input_index < input_samples) {
                    frame[i] = read_i16_le(pcm + input_index * 2U);
                }
            }
        }
        const int encoded_bytes = opus_encode(
            encoder.get(), frame.data(), samples_per_frame,
            encoded.data(), static_cast<opus_int32>(encoded.size())
        );
        if (encoded_bytes <= 0) {
            throw std::runtime_error("Opus encoding failed");
        }
        const bool last = packet_index + 1 == encoded_packet_count;
        const uint64_t granule_position = last
            ? static_cast<uint64_t>(pre_skip_48k) +
              static_cast<uint64_t>(input_samples) * 48000U / sample_rate
            : static_cast<uint64_t>(packet_index + 1) *
              samples_per_frame * 48000U / sample_rate;
        append_ogg_page(
            file,
            std::vector<uint8_t>(encoded.begin(), encoded.begin() + encoded_bytes),
            last ? 0x04U : 0U,
            granule_position,
            static_cast<uint32_t>(packet_index + 2)
        );
        audio_bytes += static_cast<size_t>(encoded_bytes);
    }

    TjiScOpusMetadata metadata{};
    metadata.sample_rate = sample_rate;
    metadata.channels = channels;
    metadata.packet_ms = packet_ms;
    metadata.samples_per_frame = samples_per_frame;
    metadata.packet_count = output_packet_count;
    metadata.audio_bytes = static_cast<int>(audio_bytes);
    metadata.duration_ms = static_cast<int>(
        (input_samples * 1000U + static_cast<size_t>(sample_rate) - 1U) /
        static_cast<size_t>(sample_rate)
    );
    metadata.file_size = static_cast<int>(file.size());
    metadata.bitrate = bitrate;
    metadata.pre_skip_48k = pre_skip_48k;
    copy_crc(metadata.crc32, crc32(file.data(), file.size()));
    return OggOpusResult{std::move(file), metadata};
}

} // namespace tji::speaker
