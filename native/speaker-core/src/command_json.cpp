#include "speaker_core_internal.h"

#include <algorithm>
#include <cstdint>
#include <sstream>
#include <string>

namespace tji::speaker {
namespace {

std::string json_escape(const std::string &value) {
    std::ostringstream out;
    for (unsigned char ch : value) {
        switch (ch) {
        case '"': out << "\\\""; break;
        case '\\': out << "\\\\"; break;
        case '\b': out << "\\b"; break;
        case '\f': out << "\\f"; break;
        case '\n': out << "\\n"; break;
        case '\r': out << "\\r"; break;
        case '\t': out << "\\t"; break;
        default:
            if (ch < 0x20) {
                constexpr char hex[] = "0123456789abcdef";
                out << "\\u00" << hex[(ch >> 4) & 0x0F] << hex[ch & 0x0F];
            } else {
                out << static_cast<char>(ch);
            }
        }
    }
    return out.str();
}

std::string quoted(const std::string &value) {
    return "\"" + json_escape(value) + "\"";
}

void append_string_field(std::ostringstream &out, const char *name, const std::string &value, bool &first) {
    if (!first) out << ',';
    first = false;
    out << quoted(name) << ':' << quoted(value);
}

void append_int_field(std::ostringstream &out, const char *name, int64_t value, bool &first) {
    if (!first) out << ',';
    first = false;
    out << quoted(name) << ':' << value;
}

void append_bool_field(std::ostringstream &out, const char *name, bool value, bool &first) {
    if (!first) out << ',';
    first = false;
    out << quoted(name) << ':' << (value ? "true" : "false");
}

void append_raw_field(std::ostringstream &out, const char *name, const std::string &value, bool &first) {
    if (value.empty()) return;
    if (!first) out << ',';
    first = false;
    out << quoted(name) << ':' << value;
}

std::vector<uint8_t> to_bytes(const std::string &value) {
    return std::vector<uint8_t>(value.begin(), value.end());
}

} // namespace

std::vector<uint8_t> build_standard_command_json(
    const std::string &device_id,
    const std::string &msg_id,
    int command_code,
    const std::string &command_name,
    int64_t timestamp_ms,
    const std::string &params_json,
    const std::string &extra_json
) {
    std::ostringstream out;
    bool first = true;
    out << '{';
    append_int_field(out, "v", 1, first);
    append_string_field(out, "deviceId", device_id, first);
    append_string_field(out, "cmdId", msg_id, first);
    append_string_field(out, "msgId", msg_id, first);
    append_int_field(out, "ts", timestamp_ms, first);
    append_int_field(out, "cmd", command_code, first);
    append_string_field(out, "cmdName", command_name, first);
    if (!extra_json.empty()) {
        out << ',' << extra_json;
    }
    append_raw_field(out, "params", params_json, first);
    out << '}';
    return to_bytes(out.str());
}

std::vector<uint8_t> build_record_download_command_json(
    const std::string &device_id,
    const std::string &msg_id,
    const std::string &record_id,
    const std::string &store_task_id,
    const std::string &created_at,
    const std::string &name,
    const std::string &download_url,
    int64_t file_size,
    const std::string &crc32,
    int duration_ms,
    const std::string &codec,
    int sample_rate,
    int channels,
    int packet_ms,
    int frame_bytes,
    int samples_per_frame,
    bool verify_only,
    const std::string &verify_kind,
    const std::string &expected_audio_crc32,
    const std::string &expected_first_samples_json,
    bool temporary,
    bool visible,
    bool auto_play,
    int playback_volume,
    bool has_playback_volume
) {
    std::ostringstream out;
    bool first = true;
    out << '{';
    append_int_field(out, "v", 1, first);
    append_string_field(out, "deviceId", device_id, first);
    append_string_field(out, "cmdId", msg_id, first);
    append_string_field(out, "cmdName", "RECORD_DOWNLOAD", first);
    append_string_field(out, "recordId", record_id, first);
    append_string_field(out, "storeTaskId", store_task_id, first);
    append_string_field(out, "createdAt", created_at, first);
    append_string_field(out, "name", name, first);
    append_string_field(out, "downloadUrl", download_url, first);
    append_int_field(out, "fileSize", file_size, first);
    append_string_field(out, "crc32", crc32, first);
    append_int_field(out, "durationMs", duration_ms, first);
    append_string_field(out, "codec", codec, first);
    append_int_field(out, "sampleRate", sample_rate, first);
    append_int_field(out, "channels", channels, first);
    append_int_field(out, "packetMs", packet_ms, first);
    append_int_field(out, "frameBytes", frame_bytes, first);
    append_int_field(out, "samplesPerFrame", samples_per_frame, first);
    if (temporary) {
        append_bool_field(out, "temporary", true, first);
        append_bool_field(out, "visible", visible, first);
        append_bool_field(out, "autoPlay", auto_play, first);
        if (has_playback_volume) {
            append_int_field(out, "playbackVolume", std::clamp(playback_volume, 0, 100), first);
        }
    }
    if (verify_only) {
        append_bool_field(out, "verifyOnly", true, first);
        if (!verify_kind.empty()) {
            append_string_field(out, "verifyKind", verify_kind, first);
        }
        if (!expected_audio_crc32.empty()) {
            append_string_field(out, "expectedAudioCrc32", expected_audio_crc32, first);
        }
        append_raw_field(out, "expectedFirstSamples", expected_first_samples_json.empty() ? "[]" : expected_first_samples_json, first);
    }
    out << '}';
    return to_bytes(out.str());
}

} // namespace tji::speaker
