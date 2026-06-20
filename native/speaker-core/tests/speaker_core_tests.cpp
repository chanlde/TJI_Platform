#include "tji_speaker_core.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace {

constexpr double kPi = 3.14159265358979323846;

uint16_t u16le(const uint8_t *data) {
    return static_cast<uint16_t>(data[0]) | static_cast<uint16_t>(data[1] << 8);
}

uint32_t u32le(const uint8_t *data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8) |
           (static_cast<uint32_t>(data[2]) << 16) |
           (static_cast<uint32_t>(data[3]) << 24);
}

std::vector<uint8_t> synthetic_voice_pcm(double amplitude, int sample_count, int sample_rate = 8000) {
    std::vector<uint8_t> pcm(static_cast<size_t>(sample_count) * 2);
    for (int i = 0; i < sample_count; ++i) {
        const double phase = 2.0 * kPi * 440.0 * static_cast<double>(i) / static_cast<double>(sample_rate);
        int value = static_cast<int>(std::lround(std::sin(phase) * 32767.0 * amplitude));
        if (value > 32767) value = 32767;
        if (value < -32768) value = -32768;
        pcm[static_cast<size_t>(i) * 2] = static_cast<uint8_t>(value & 0xFF);
        pcm[static_cast<size_t>(i) * 2 + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
    }
    return pcm;
}

void require_true(bool value, const std::string &message) {
    if (!value) throw std::runtime_error(message);
}

void require_eq(size_t actual, size_t expected, const std::string &message) {
    if (actual != expected) {
        throw std::runtime_error(message + ": actual=" + std::to_string(actual) + " expected=" + std::to_string(expected));
    }
}

std::vector<uint8_t> read_binary(const std::string &path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        throw std::runtime_error("failed to open fixture: " + path);
    }
    return std::vector<uint8_t>(
        std::istreambuf_iterator<char>(in),
        std::istreambuf_iterator<char>()
    );
}

std::string read_text(const std::string &path) {
    std::ifstream in(path);
    if (!in) {
        throw std::runtime_error("failed to open fixture: " + path);
    }
    std::ostringstream out;
    out << in.rdbuf();
    return out.str();
}

std::string metadata_value(const std::string &metadata, std::string_view key) {
    const std::string needle = std::string(key) + "=";
    size_t offset = metadata.find(needle);
    if (offset == std::string::npos) {
        throw std::runtime_error("missing metadata key: " + std::string(key));
    }
    offset += needle.size();
    const size_t end = metadata.find('\n', offset);
    return metadata.substr(offset, end == std::string::npos ? std::string::npos : end - offset);
}

void require_bytes_eq(const uint8_t *actual, size_t actual_size, const std::vector<uint8_t> &expected, const std::string &message) {
    if (actual_size != expected.size()) {
        throw std::runtime_error(
            message + " size mismatch: actual=" + std::to_string(actual_size) +
            " expected=" + std::to_string(expected.size())
        );
    }
    if (std::memcmp(actual, expected.data(), expected.size()) != 0) {
        throw std::runtime_error(message + " bytes mismatch");
    }
}

struct OwnedBuffer {
    TjiScBuffer buffer{};
    ~OwnedBuffer() { tji_sc_free(&buffer); }
};

struct OwnedPacketizer {
    TjiScAdpcmPacketizer *packetizer = nullptr;
    OwnedPacketizer() {
        require_eq(tji_sc_adpcm_packetizer_create(&packetizer), TJI_SC_OK, "create adpcm packetizer");
    }
    ~OwnedPacketizer() { tji_sc_adpcm_packetizer_free(packetizer); }
};

void test_legacy_adpcm_header() {
    auto pcm = synthetic_voice_pcm(0.08, 320);
    OwnedBuffer packet;
    require_eq(tji_sc_packetize_adpcm_legacy(pcm.data(), pcm.size(), 0, 0, &packet.buffer), TJI_SC_OK, "legacy packetize");
    require_eq(packet.buffer.size, 184, "legacy packet size");
    require_eq(u16le(packet.buffer.data), 0xA55A, "legacy magic");
    require_eq(packet.buffer.data[2], 1, "legacy version");
    require_eq(packet.buffer.data[3], 1, "legacy codec");
    require_eq(u32le(packet.buffer.data + 4), 0, "legacy sequence");
    require_eq(u32le(packet.buffer.data + 8), 0, "legacy timestamp");
    require_eq(u16le(packet.buffer.data + 12), 8000, "legacy sample rate");
    require_eq(packet.buffer.data[14], 1, "legacy channels");
    require_eq(u16le(packet.buffer.data + 16), 164, "legacy payload bytes");
    require_eq(u16le(packet.buffer.data + 18), 320, "legacy sample count");
}

void test_v2_record_store_header() {
    auto pcm = synthetic_voice_pcm(0.08, 320);
    const std::string device_id = "T12345678";
    const std::string task_id = "STORE_T12345678_1";
    const std::string talk_id = "REC_T12345678_1";
    const size_t expected_header_len = 28 + device_id.size() + task_id.size() + talk_id.size();
    OwnedBuffer packet;
    require_eq(
        tji_sc_packetize_adpcm_v2(
            pcm.data(),
            pcm.size(),
            0,
            0,
            device_id.c_str(),
            task_id.c_str(),
            talk_id.c_str(),
            TJI_SC_STREAM_RECORD_STORE,
            1,
            &packet.buffer
        ),
        TJI_SC_OK,
        "v2 packetize"
    );
    require_eq(u16le(packet.buffer.data), 0xA55A, "v2 magic");
    require_eq(packet.buffer.data[2], 2, "v2 version");
    require_eq(packet.buffer.data[3], 1, "v2 codec");
    require_eq(u16le(packet.buffer.data + 4), expected_header_len, "v2 header length");
    require_eq(u16le(packet.buffer.data + 6), 0x03, "v2 flags");
    require_eq(u16le(packet.buffer.data + 16), 8000, "v2 sample rate");
    require_eq(packet.buffer.data[18], 1, "v2 channels");
    require_eq(packet.buffer.data[19], 40, "v2 packet ms");
    require_eq(u16le(packet.buffer.data + 20), 164, "v2 payload bytes");
    require_eq(u16le(packet.buffer.data + 22), 320, "v2 sample count");
    require_eq(packet.buffer.data[24], device_id.size(), "v2 device id length");
    require_eq(packet.buffer.data[25], task_id.size(), "v2 task id length");
    require_eq(packet.buffer.data[26], talk_id.size(), "v2 talk id length");
}

void test_hadp_pcm16_header_and_decode() {
    auto pcm = synthetic_voice_pcm(0.08, 8000);
    OwnedBuffer hadp;
    TjiScHadpMetadata metadata{};
    require_eq(
        tji_sc_encode_hadp(
            pcm.data(),
            pcm.size(),
            "REC_PCM16_TEST",
            TJI_SC_CODEC_PCM16,
            8000,
            1,
            40,
            &hadp.buffer,
            &metadata
        ),
        TJI_SC_OK,
        "encode pcm16 hadp"
    );
    require_eq(metadata.frame_count, 25, "pcm16 frame count");
    require_eq(metadata.audio_bytes, 25 * 640, "pcm16 audio bytes");
    require_eq(metadata.file_size, 128 + 25 * 640, "pcm16 file size");
    require_eq(metadata.duration_ms, 1000, "pcm16 duration");
    require_true(std::string(metadata.crc32).starts_with("0x"), "file crc format");
    require_true(std::string(metadata.audio_crc32).starts_with("0x"), "audio crc format");
    require_true(std::memcmp(hadp.buffer.data, "HADP", 4) == 0, "hadp magic");
    require_eq(u16le(hadp.buffer.data + 4), 1, "hadp version");
    require_eq(u16le(hadp.buffer.data + 6), 128, "hadp header bytes");
    require_eq(u16le(hadp.buffer.data + 8), 2, "hadp pcm16 codec id");
    require_eq(u32le(hadp.buffer.data + 12), 8000, "hadp sample rate");
    require_eq(u16le(hadp.buffer.data + 16), 1, "hadp channels");
    require_eq(u16le(hadp.buffer.data + 18), 40, "hadp packet ms");
    require_eq(u16le(hadp.buffer.data + 20), 640, "hadp frame bytes");
    require_eq(u16le(hadp.buffer.data + 22), 320, "hadp samples per frame");
    require_eq(u32le(hadp.buffer.data + 24), 25, "hadp frame count");
    require_eq(u32le(hadp.buffer.data + 28), 25 * 640, "hadp audio bytes");
    require_eq(u32le(hadp.buffer.data + 32), 1000, "hadp duration");

    OwnedBuffer decoded;
    require_eq(tji_sc_decode_hadp_pcm16(hadp.buffer.data, hadp.buffer.size, &decoded.buffer), TJI_SC_OK, "decode pcm16 hadp");
    require_eq(decoded.buffer.size, pcm.size(), "decoded pcm size");
    require_true(std::memcmp(decoded.buffer.data, pcm.data(), pcm.size()) == 0, "decoded pcm matches original");
}

void test_hadp_adpcm_header_and_decode_size() {
    auto pcm = synthetic_voice_pcm(0.08, 8000);
    OwnedBuffer hadp;
    TjiScHadpMetadata metadata{};
    require_eq(
        tji_sc_encode_hadp(
            pcm.data(),
            pcm.size(),
            "REC_ADPCM_TEST",
            TJI_SC_CODEC_IMA_ADPCM,
            8000,
            1,
            40,
            &hadp.buffer,
            &metadata
        ),
        TJI_SC_OK,
        "encode adpcm hadp"
    );
    require_eq(metadata.frame_count, 25, "adpcm frame count");
    require_eq(metadata.audio_bytes, 25 * 164, "adpcm audio bytes");
    require_eq(u16le(hadp.buffer.data + 8), 1, "hadp adpcm codec id");
    require_eq(u16le(hadp.buffer.data + 20), 164, "adpcm frame bytes");
    require_eq(u16le(hadp.buffer.data + 22), 320, "adpcm samples per frame");

    OwnedBuffer decoded;
    require_eq(tji_sc_decode_hadp_pcm16(hadp.buffer.data, hadp.buffer.size, &decoded.buffer), TJI_SC_OK, "decode adpcm hadp");
    require_eq(decoded.buffer.size, pcm.size(), "adpcm decoded pcm size");
}

void test_kotlin_golden_legacy_and_v2_packets() {
    const auto pcm = read_binary("../../app/src/test/resources/speaker-core-golden/voice_1s_8k_pcm16le.raw");
    const auto legacy_frame0 = read_binary("../../app/src/test/resources/speaker-core-golden/legacy_adpcm_packet_frame0.bin");
    const auto v2_record_store_last = read_binary("../../app/src/test/resources/speaker-core-golden/v2_record_store_last_packet.bin");

    OwnedBuffer legacy_packet;
    require_eq(
        tji_sc_packetize_adpcm_legacy(pcm.data(), 640, 0, 0, &legacy_packet.buffer),
        TJI_SC_OK,
        "golden legacy frame 0 packetize"
    );
    require_bytes_eq(legacy_packet.buffer.data, legacy_packet.buffer.size, legacy_frame0, "golden legacy frame 0");

    OwnedBuffer v2_packet;
    require_eq(
        tji_sc_packetize_adpcm_v2(
            pcm.data(),
            640,
            0,
            0,
            "T12345678",
            "STORE_T12345678_1",
            "REC_T12345678_1",
            TJI_SC_STREAM_RECORD_STORE,
            1,
            &v2_packet.buffer
        ),
        TJI_SC_OK,
        "golden v2 record-store packetize"
    );
    require_bytes_eq(v2_packet.buffer.data, v2_packet.buffer.size, v2_record_store_last, "golden v2 record-store packet");
}

void test_stateful_v2_packetizer_matches_hadp_adpcm_frames() {
    const auto pcm = read_binary("../../app/src/test/resources/speaker-core-golden/voice_1s_8k_pcm16le.raw");
    OwnedBuffer hadp;
    TjiScHadpMetadata metadata{};
    require_eq(
        tji_sc_encode_hadp(
            pcm.data(),
            1280,
            "REC_ADPCM_STATEFUL_TEST",
            TJI_SC_CODEC_IMA_ADPCM,
            8000,
            1,
            40,
            &hadp.buffer,
            &metadata
        ),
        TJI_SC_OK,
        "encode stateful adpcm hadp"
    );
    require_eq(metadata.frame_count, 2, "stateful hadp frame count");

    OwnedPacketizer packetizer;
    for (uint32_t frame = 0; frame < 2; ++frame) {
        OwnedBuffer packet;
        require_eq(
            tji_sc_adpcm_packetizer_packetize_v2(
                packetizer.packetizer,
                pcm.data() + frame * 640,
                640,
                frame,
                frame * 40,
                "T12345678",
                "STORE_T12345678_1",
                "REC_T12345678_1",
                TJI_SC_STREAM_RECORD_STORE,
                frame == 1 ? 1 : 0,
                &packet.buffer
            ),
            TJI_SC_OK,
            "stateful v2 packetize"
        );
        const size_t header_len = u16le(packet.buffer.data + 4);
        const uint8_t *expected_payload = hadp.buffer.data + 128 + frame * 164;
        require_true(header_len + 164 == packet.buffer.size, "stateful v2 packet size");
        require_true(
            std::memcmp(packet.buffer.data + header_len, expected_payload, 164) == 0,
            "stateful v2 payload matches hadp frame " + std::to_string(frame)
        );
    }
}

void test_kotlin_golden_hadp_files() {
    const auto pcm = read_binary("../../app/src/test/resources/speaker-core-golden/voice_1s_8k_pcm16le.raw");
    const auto pcm16_hadp = read_binary("../../app/src/test/resources/speaker-core-golden/hadp_pcm16_1s.hadp");
    const auto adpcm_hadp = read_binary("../../app/src/test/resources/speaker-core-golden/hadp_ima_adpcm_1s.hadp");
    const auto metadata = read_text("../../app/src/test/resources/speaker-core-golden/metadata.properties");

    OwnedBuffer pcm16;
    TjiScHadpMetadata pcm16_metadata{};
    require_eq(
        tji_sc_encode_hadp(
            pcm.data(),
            pcm.size(),
            "REC_PCM16_TEST",
            TJI_SC_CODEC_PCM16,
            8000,
            1,
            40,
            &pcm16.buffer,
            &pcm16_metadata
        ),
        TJI_SC_OK,
        "golden encode pcm16 hadp"
    );
    require_bytes_eq(pcm16.buffer.data, pcm16.buffer.size, pcm16_hadp, "golden pcm16 hadp");
    require_eq(pcm16_metadata.file_size, std::stoul(metadata_value(metadata, "pcm16.fileSize")), "golden pcm16 file size");
    require_true(std::string(pcm16_metadata.crc32) == metadata_value(metadata, "pcm16.crc32"), "golden pcm16 crc");
    require_true(std::string(pcm16_metadata.audio_crc32) == metadata_value(metadata, "pcm16.audioCrc32"), "golden pcm16 audio crc");

    OwnedBuffer adpcm;
    TjiScHadpMetadata adpcm_metadata{};
    require_eq(
        tji_sc_encode_hadp(
            pcm.data(),
            pcm.size(),
            "REC_ADPCM_TEST",
            TJI_SC_CODEC_IMA_ADPCM,
            8000,
            1,
            40,
            &adpcm.buffer,
            &adpcm_metadata
        ),
        TJI_SC_OK,
        "golden encode adpcm hadp"
    );
    require_bytes_eq(adpcm.buffer.data, adpcm.buffer.size, adpcm_hadp, "golden adpcm hadp");
    require_eq(adpcm_metadata.file_size, std::stoul(metadata_value(metadata, "adpcm.fileSize")), "golden adpcm file size");
    require_true(std::string(adpcm_metadata.crc32) == metadata_value(metadata, "adpcm.crc32"), "golden adpcm crc");
    require_true(std::string(adpcm_metadata.audio_crc32) == metadata_value(metadata, "adpcm.audioCrc32"), "golden adpcm audio crc");
}

} // namespace

int main() {
    try {
        test_legacy_adpcm_header();
        test_v2_record_store_header();
        test_hadp_pcm16_header_and_decode();
        test_hadp_adpcm_header_and_decode_size();
        test_kotlin_golden_legacy_and_v2_packets();
        test_stateful_v2_packetizer_matches_hadp_adpcm_frames();
        test_kotlin_golden_hadp_files();
    } catch (const std::exception &error) {
        std::cerr << "speaker-core test failed: " << error.what() << '\n';
        return 1;
    }
    std::cout << "speaker-core tests passed\n";
    return 0;
}
