#include "tji_speaker_core.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

std::vector<uint8_t> make_pcm(int sample_rate, int samples)
{
    std::vector<uint8_t> pcm(static_cast<size_t>(samples) * 2U);
    for (int index = 0; index < samples; ++index) {
        const double phase =
            6.283185307179586 * 440.0 * index / sample_rate;
        const auto value =
            static_cast<int16_t>(std::lround(std::sin(phase) * 12000.0));
        pcm[static_cast<size_t>(index) * 2U] =
            static_cast<uint8_t>(value);
        pcm[static_cast<size_t>(index) * 2U + 1U] =
            static_cast<uint8_t>(static_cast<uint16_t>(value) >> 8U);
    }
    return pcm;
}

void require(bool condition, const char *message)
{
    if (!condition) {
        throw std::runtime_error(message);
    }
}

void test_ogg_opus_encode()
{
    constexpr int sample_rate = 24000;
    const auto pcm = make_pcm(sample_rate, sample_rate + 7);
    TjiScBuffer file{};
    TjiScOpusMetadata metadata{};

    require(tji_sc_encode_ogg_opus(
                pcm.data(), pcm.size(), "REC_OPUS_TEST",
                sample_rate, 1, 20, 24000,
                &file, &metadata) == TJI_SC_OK,
            "Ogg Opus encode failed");
    require(file.data != nullptr && file.size > 128U,
            "Ogg Opus output is empty");
    require(std::memcmp(file.data, "OggS", 4U) == 0,
            "missing Ogg capture pattern");
    const auto head_segments = file.data[26];
    const size_t head_payload = 27U + head_segments;
    require(head_payload + 8U <= file.size &&
            std::memcmp(file.data + head_payload, "OpusHead", 8U) == 0,
            "missing OpusHead");
    require(metadata.sample_rate == sample_rate &&
            metadata.channels == 1 &&
            metadata.packet_ms == 20 &&
            metadata.packet_count == 51 &&
            metadata.duration_ms == 1001 &&
            metadata.file_size == static_cast<int>(file.size),
            "incorrect Opus metadata");
    require(metadata.crc32[0] == '0' && metadata.crc32[1] == 'x',
            "incorrect CRC text");
    tji_sc_free(&file);
}

void test_invalid_opus_format_is_rejected()
{
    const auto pcm = make_pcm(24000, 480);
    TjiScBuffer file{};
    TjiScOpusMetadata metadata{};

    require(tji_sc_encode_ogg_opus(
                pcm.data(), pcm.size(), "REC_BAD",
                24000, 2, 20, 24000,
                &file, &metadata) == TJI_SC_INVALID_ARGUMENT,
            "stereo input must be rejected");
    tji_sc_free(&file);
}

void test_record_list_parser_preserves_server_cursor()
{
    const char *payload =
        R"({"items":[{"recordId":"REC_1","name":"one"}],"offset":0,"limit":4,"total":8,"nextOffset":4,"hasMore":true,"ts":123})";
    TjiScBuffer parsed{};

    require(tji_sc_parse_mqtt_record_list_json(payload, &parsed) == TJI_SC_OK,
            "record list parse failed");
    const std::string json(
        reinterpret_cast<const char *>(parsed.data), parsed.size);
    require(json.find("\"nextOffset\":4") != std::string::npos,
            "record list nextOffset was lost");
    require(json.find("\"recordId\":\"REC_1\"") != std::string::npos,
            "record list item was lost");
    tji_sc_free(&parsed);
}

} // namespace

int main()
{
    try {
        test_ogg_opus_encode();
        test_invalid_opus_format_is_rejected();
        test_record_list_parser_preserves_server_cursor();
        std::cout << "speaker-core tests passed\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
