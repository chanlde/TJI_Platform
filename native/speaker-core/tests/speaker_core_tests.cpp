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

void append_u16le(std::vector<uint8_t> &out, uint16_t value) {
    out.push_back(static_cast<uint8_t>(value & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
}

void append_u32le(std::vector<uint8_t> &out, uint32_t value) {
    out.push_back(static_cast<uint8_t>(value & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

void append_ascii(std::vector<uint8_t> &out, const char *text) {
    out.insert(out.end(), text, text + 4);
}

std::vector<uint8_t> build_pcm16_wav(int sample_rate, int channels, const std::vector<int16_t> &interleaved_samples) {
    const uint32_t data_bytes = static_cast<uint32_t>(interleaved_samples.size() * 2);
    std::vector<uint8_t> out;
    append_ascii(out, "RIFF");
    append_u32le(out, 36 + data_bytes);
    append_ascii(out, "WAVE");
    append_ascii(out, "fmt ");
    append_u32le(out, 16);
    append_u16le(out, 1);
    append_u16le(out, static_cast<uint16_t>(channels));
    append_u32le(out, static_cast<uint32_t>(sample_rate));
    append_u32le(out, static_cast<uint32_t>(sample_rate * channels * 2));
    append_u16le(out, static_cast<uint16_t>(channels * 2));
    append_u16le(out, 16);
    append_ascii(out, "data");
    append_u32le(out, data_bytes);
    for (int16_t sample : interleaved_samples) {
        append_u16le(out, static_cast<uint16_t>(sample));
    }
    return out;
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

void test_voice_processing_ptt_boosts_quiet_audio() {
    auto pcm = synthetic_voice_pcm(0.004, 1600);
    OwnedBuffer processed;
    require_eq(
        tji_sc_process_voice(
            pcm.data(),
            pcm.size(),
            TJI_SC_VOICE_PROFILE_PUSH_TO_TALK,
            8000,
            0.0f,
            0.0f,
            &processed.buffer
        ),
        TJI_SC_OK,
        "process ptt voice"
    );
    require_true(processed.buffer.size > pcm.size(), "ptt processing appends settle silence");
    require_true(processed.buffer.size % 2 == 0, "ptt output remains pcm16 aligned");
}

void test_kotlin_golden_ptt_voice_processing() {
    const auto pcm = read_binary("../../app/src/test/resources/speaker-core-golden/voice_1s_8k_pcm16le.raw");
    const auto expected = read_binary("../../app/src/test/resources/speaker-core-golden/voice_1s_8k_ptt_processed.raw");
    OwnedBuffer processed;
    require_eq(
        tji_sc_process_voice(
            pcm.data(),
            pcm.size(),
            TJI_SC_VOICE_PROFILE_PUSH_TO_TALK,
            8000,
            0.0f,
            0.0f,
            &processed.buffer
        ),
        TJI_SC_OK,
        "golden ptt voice processing"
    );
    require_bytes_eq(processed.buffer.data, processed.buffer.size, expected, "golden ptt processed pcm");
}

void test_stateful_live_voice_processor() {
    auto pcm = synthetic_voice_pcm(0.03, 320);
    TjiScVoiceProcessor *processor = nullptr;
    require_eq(tji_sc_voice_processor_create(&processor), TJI_SC_OK, "create voice processor");
    OwnedBuffer processed;
    require_eq(
        tji_sc_voice_processor_process_frame(
            processor,
            pcm.data(),
            pcm.size(),
            0.0f,
            0.0f,
            &processed.buffer
        ),
        TJI_SC_OK,
        "process live frame"
    );
    tji_sc_voice_processor_free(processor);
    require_eq(processed.buffer.size, pcm.size(), "live frame size remains unchanged");
}

void test_command_json_standard_envelope() {
    OwnedBuffer json;
    require_eq(
        tji_sc_build_standard_command_json(
            "T12345678",
            "speaker-volume-1",
            105,
            "SET_VOLUME",
            123456789,
            "{\"volume\":35}",
            "",
            &json.buffer
        ),
        TJI_SC_OK,
        "build standard command json"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    const std::string expected =
        "{\"v\":1,\"deviceId\":\"T12345678\",\"cmdId\":\"speaker-volume-1\","
        "\"msgId\":\"speaker-volume-1\",\"ts\":123456789,\"cmd\":105,"
        "\"cmdName\":\"SET_VOLUME\",\"params\":{\"volume\":35}}";
    require_true(actual == expected, "standard command json matches");
}

void test_command_json_record_download() {
    OwnedBuffer json;
    require_eq(
        tji_sc_build_record_download_command_json(
            "T12345678",
            "speaker-record-download-1",
            "REC_1",
            "STORE_1",
            "2026-06-21T09:00:00+08:00",
            "录音 09:00",
            "http://example.com/REC_1.hadp",
            4228,
            "0x1234ABCD",
            1000,
            "ima_adpcm",
            8000,
            1,
            40,
            164,
            320,
            1,
            "hadp",
            "0x00000001",
            "[1,2,3]",
            1,
            0,
            1,
            88,
            1,
            &json.buffer
        ),
        TJI_SC_OK,
        "build record download command json"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    require_true(actual.find("\"cmdName\":\"RECORD_DOWNLOAD\"") != std::string::npos, "record download command name");
    require_true(actual.find("\"name\":\"录音 09:00\"") != std::string::npos, "record download utf8 name");
    require_true(actual.find("\"temporary\":true") != std::string::npos, "record download temporary");
    require_true(actual.find("\"visible\":false") != std::string::npos, "record download visible");
    require_true(actual.find("\"playbackVolume\":88") != std::string::npos, "record download playback volume");
    require_true(actual.find("\"expectedFirstSamples\":[1,2,3]") != std::string::npos, "record download first samples");
}

void test_resample_pcm16_linear_length() {
    auto pcm = synthetic_voice_pcm(0.08, 8000, 8000);
    OwnedBuffer resampled;
    require_eq(
        tji_sc_resample_pcm16(pcm.data(), pcm.size(), 8000, 16000, &resampled.buffer),
        TJI_SC_OK,
        "resample pcm16"
    );
    require_eq(resampled.buffer.size, pcm.size() * 2, "resampled 8k to 16k size");
    require_true(resampled.buffer.size % 2 == 0, "resampled pcm16 aligned");
}

void test_generate_tone_pcm16() {
    OwnedBuffer tone;
    require_eq(
        tji_sc_generate_tone_pcm16(1000, 640, 8000, 40, 12, 0.35f, &tone.buffer),
        TJI_SC_OK,
        "generate tone"
    );
    require_eq(tone.buffer.size, 8000 * 640 / 1000 * 2, "tone pcm size");
    require_eq(static_cast<size_t>(tone.buffer.data[0]), static_cast<size_t>(0), "tone starts at zero low byte");
    require_eq(static_cast<size_t>(tone.buffer.data[1]), static_cast<size_t>(0), "tone starts at zero high byte");
}

void test_prepend_silence_and_pad_frame() {
    auto pcm = synthetic_voice_pcm(0.08, 320, 8000);
    OwnedBuffer with_silence;
    require_eq(
        tji_sc_prepend_silence_pcm16(pcm.data(), pcm.size(), 120, 8000, &with_silence.buffer),
        TJI_SC_OK,
        "prepend silence"
    );
    require_eq(with_silence.buffer.size, pcm.size() + 8000 * 120 / 1000 * 2, "silence size");
    require_true(std::memcmp(with_silence.buffer.data + with_silence.buffer.size - pcm.size(), pcm.data(), pcm.size()) == 0, "silence keeps pcm");

    OwnedBuffer padded;
    require_eq(
        tji_sc_pad_pcm16_to_frame(pcm.data(), pcm.size() - 20, 640, &padded.buffer),
        TJI_SC_OK,
        "pad frame"
    );
    require_eq(padded.buffer.size, 640, "padded frame size");
}

void test_decode_wav_pcm16_mono() {
    const auto wav = build_pcm16_wav(16000, 2, {
        1000, 3000,
        -1000, -3000,
        2000, 4000,
        -2000, -4000
    });
    OwnedBuffer pcm;
    require_eq(
        tji_sc_decode_wav_pcm16_mono(wav.data(), wav.size(), 8000, &pcm.buffer),
        TJI_SC_OK,
        "decode wav pcm16 mono"
    );
    require_eq(pcm.buffer.size, 4, "decoded wav downsampled size");
    require_eq(u16le(pcm.buffer.data), static_cast<uint16_t>(0), "decoded wav first averaged sample");
}

void test_float32_to_pcm16() {
    const float samples[] = {-1.0f, -0.5f, 0.0f, 0.5f, 1.0f};
    OwnedBuffer pcm;
    require_eq(
        tji_sc_float32_to_pcm16(samples, 5, 8000, 8000, &pcm.buffer),
        TJI_SC_OK,
        "float32 to pcm16"
    );
    require_eq(pcm.buffer.size, 10, "float32 pcm size");
    require_eq(u16le(pcm.buffer.data), static_cast<uint16_t>(0x8000), "float32 negative clamp");
    require_eq(u16le(pcm.buffer.data + 8), static_cast<uint16_t>(0x7FFF), "float32 positive clamp");
}

void test_mqtt_state_parser() {
    OwnedBuffer json;
    require_eq(
        tji_sc_parse_mqtt_state_json(
            "SPK-001",
            R"({"name":"喊话器 01","playing":true,"currentFile":"welcome.hadp","volume":124,"servoAngle":-15,"network":"wifi","lastError":"低电量","ts":1710000000000})",
            1,
            &json.buffer
        ),
        TJI_SC_OK,
        "parse mqtt state"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    require_true(actual.find("\"serialNumber\":\"SPK-001\"") != std::string::npos, "state serial");
    require_true(actual.find("\"volume\":100") != std::string::npos, "state volume clamp");
    require_true(actual.find("\"servoAngle\":-15") != std::string::npos, "state servo");
    require_true(actual.find("\"timestamp\":1710000000000") != std::string::npos, "state timestamp");
}

void test_mqtt_state_parser_decodes_unicode_escapes() {
    OwnedBuffer json;
    require_eq(
        tji_sc_parse_mqtt_state_json(
            "SPK-002",
            R"({"name":"\u5f55\u97f3","network":"wifi-\ud83d\udce1","ts":1710000000001})",
            1,
            &json.buffer
        ),
        TJI_SC_OK,
        "parse mqtt state unicode escapes"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    require_true(actual.find("\"name\":\"录音\"") != std::string::npos, "state unicode name");
    require_true(actual.find("\"network\":\"wifi-📡\"") != std::string::npos, "state unicode surrogate pair");
}

void test_mqtt_record_list_parser() {
    OwnedBuffer json;
    require_eq(
        tji_sc_parse_mqtt_record_list_json(
            R"({"items":[{"recordId":"rec-1","name":"起飞提醒","fileSize":1200,"durationMs":2000},{"recordId":"","fileSize":1},{"recordId":"rec-2","fileSize":2400,"durationMs":4000}],"offset":-2,"limit":99,"hasMore":true,"ts":1710000000100})",
            &json.buffer
        ),
        TJI_SC_OK,
        "parse mqtt record list"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    require_true(actual.find("\"recordId\":\"rec-1\"") != std::string::npos, "record list first");
    require_true(actual.find("\"name\":\"rec-2\"") != std::string::npos, "record list fallback name");
    require_true(actual.find("\"limit\":8") != std::string::npos, "record list limit clamp");
    require_true(actual.find("\"total\":2") != std::string::npos, "record list total fallback");
}

void test_mqtt_record_event_parser() {
    OwnedBuffer json;
    require_eq(
        tji_sc_parse_mqtt_record_event_json(
            "record_failed",
            R"({"recordId":"rec-1","code":42,"msg":"存储空间不足","firstSamples":[1,2,3],"ts":1710000000400})",
            &json.buffer
        ),
        TJI_SC_OK,
        "parse mqtt record event"
    );
    const std::string actual(reinterpret_cast<char *>(json.buffer.data), json.buffer.size);
    require_true(actual.find("\"type\":\"record_failed\"") != std::string::npos, "record event type");
    require_true(actual.find("\"ok\":false") != std::string::npos, "record event failed default");
    require_true(actual.find("\"message\":\"存储空间不足\"") != std::string::npos, "record event message");
    require_true(actual.find("\"firstSamples\":[1,2,3]") != std::string::npos, "record event first samples");
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
        test_voice_processing_ptt_boosts_quiet_audio();
        test_kotlin_golden_ptt_voice_processing();
        test_stateful_live_voice_processor();
        test_command_json_standard_envelope();
        test_command_json_record_download();
        test_resample_pcm16_linear_length();
        test_generate_tone_pcm16();
        test_prepend_silence_and_pad_frame();
        test_decode_wav_pcm16_mono();
        test_float32_to_pcm16();
        test_mqtt_state_parser();
        test_mqtt_state_parser_decodes_unicode_escapes();
        test_mqtt_record_list_parser();
        test_mqtt_record_event_parser();
    } catch (const std::exception &error) {
        std::cerr << "speaker-core test failed: " << error.what() << '\n';
        return 1;
    }
    std::cout << "speaker-core tests passed\n";
    return 0;
}
