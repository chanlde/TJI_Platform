#include "speaker_core_internal.h"

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

} // namespace tji::speaker
