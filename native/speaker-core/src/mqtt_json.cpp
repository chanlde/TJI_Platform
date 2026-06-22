#include "speaker_core_internal.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

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

std::vector<uint8_t> to_bytes(const std::string &value) {
    return std::vector<uint8_t>(value.begin(), value.end());
}

int hex_value(char ch) {
    if (ch >= '0' && ch <= '9') return ch - '0';
    if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
    if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
    return -1;
}

uint32_t parse_hex4(std::string_view raw, size_t offset) {
    if (offset + 4 > raw.size()) throw std::invalid_argument("bad unicode escape");
    uint32_t value = 0;
    for (size_t index = 0; index < 4; ++index) {
        const int digit = hex_value(raw[offset + index]);
        if (digit < 0) throw std::invalid_argument("bad unicode escape");
        value = (value << 4U) | static_cast<uint32_t>(digit);
    }
    return value;
}

void append_utf8(std::string &out, uint32_t codepoint) {
    if (codepoint <= 0x7F) {
        out.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (codepoint >> 6U)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3FU)));
    } else if (codepoint <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (codepoint >> 12U)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 6U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3FU)));
    } else if (codepoint <= 0x10FFFF) {
        out.push_back(static_cast<char>(0xF0 | (codepoint >> 18U)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 12U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80 | ((codepoint >> 6U) & 0x3FU)));
        out.push_back(static_cast<char>(0x80 | (codepoint & 0x3FU)));
    } else {
        throw std::invalid_argument("unicode codepoint out of range");
    }
}

void skip_ws(std::string_view text, size_t &offset) {
    while (offset < text.size() && std::isspace(static_cast<unsigned char>(text[offset]))) ++offset;
}

size_t skip_string(std::string_view text, size_t offset) {
    if (offset >= text.size() || text[offset] != '"') throw std::invalid_argument("expected string");
    ++offset;
    while (offset < text.size()) {
        const char ch = text[offset++];
        if (ch == '\\') {
            if (offset >= text.size()) throw std::invalid_argument("bad escape");
            ++offset;
        } else if (ch == '"') {
            return offset;
        }
    }
    throw std::invalid_argument("unterminated string");
}

size_t skip_value(std::string_view text, size_t offset) {
    skip_ws(text, offset);
    if (offset >= text.size()) throw std::invalid_argument("missing value");
    if (text[offset] == '"') return skip_string(text, offset);
    if (text[offset] == '{' || text[offset] == '[') {
        const char open = text[offset];
        const char close = open == '{' ? '}' : ']';
        int depth = 0;
        while (offset < text.size()) {
            if (text[offset] == '"') {
                offset = skip_string(text, offset);
                continue;
            }
            if (text[offset] == open) ++depth;
            if (text[offset] == close) {
                --depth;
                ++offset;
                if (depth == 0) return offset;
                continue;
            }
            ++offset;
        }
        throw std::invalid_argument("unterminated container");
    }
    while (offset < text.size() && text[offset] != ',' && text[offset] != '}' && text[offset] != ']') ++offset;
    return offset;
}

std::string unescape_json_string(std::string_view raw) {
    if (raw.size() < 2 || raw.front() != '"' || raw.back() != '"') return {};
    std::string out;
    for (size_t i = 1; i + 1 < raw.size(); ++i) {
        char ch = raw[i];
        if (ch != '\\') {
            out.push_back(ch);
            continue;
        }
        if (++i + 1 >= raw.size()) break;
        switch (raw[i]) {
        case '"': out.push_back('"'); break;
        case '\\': out.push_back('\\'); break;
        case '/': out.push_back('/'); break;
        case 'b': out.push_back('\b'); break;
        case 'f': out.push_back('\f'); break;
        case 'n': out.push_back('\n'); break;
        case 'r': out.push_back('\r'); break;
        case 't': out.push_back('\t'); break;
        case 'u': {
            uint32_t codepoint = parse_hex4(raw, i + 1);
            i += 4;
            if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
                if (i + 6 >= raw.size() || raw[i + 1] != '\\' || raw[i + 2] != 'u') {
                    throw std::invalid_argument("missing unicode low surrogate");
                }
                const uint32_t low = parse_hex4(raw, i + 3);
                if (low < 0xDC00 || low > 0xDFFF) {
                    throw std::invalid_argument("bad unicode low surrogate");
                }
                i += 6;
                codepoint = 0x10000 + ((codepoint - 0xD800) << 10U) + (low - 0xDC00);
            } else if (codepoint >= 0xDC00 && codepoint <= 0xDFFF) {
                throw std::invalid_argument("unexpected unicode low surrogate");
            }
            append_utf8(out, codepoint);
            break;
        }
        default:
            out.push_back(raw[i]);
            break;
        }
    }
    return out;
}

std::string raw_field(std::string_view object, std::string_view key) {
    size_t offset = 0;
    skip_ws(object, offset);
    if (offset >= object.size() || object[offset] != '{') return {};
    ++offset;
    while (offset < object.size()) {
        skip_ws(object, offset);
        if (offset < object.size() && object[offset] == '}') break;
        const size_t key_start = offset;
        const size_t key_end = skip_string(object, offset);
        const std::string found_key = unescape_json_string(object.substr(key_start, key_end - key_start));
        offset = key_end;
        skip_ws(object, offset);
        if (offset >= object.size() || object[offset] != ':') return {};
        ++offset;
        skip_ws(object, offset);
        const size_t value_start = offset;
        const size_t value_end = skip_value(object, offset);
        if (found_key == key) {
            size_t trimmed_end = value_end;
            while (trimmed_end > value_start && std::isspace(static_cast<unsigned char>(object[trimmed_end - 1]))) --trimmed_end;
            return std::string(object.substr(value_start, trimmed_end - value_start));
        }
        offset = value_end;
        skip_ws(object, offset);
        if (offset < object.size() && object[offset] == ',') ++offset;
    }
    return {};
}

bool has_field(std::string_view object, std::string_view key) {
    return !raw_field(object, key).empty();
}

std::string string_field(std::string_view object, std::string_view key, std::string fallback = {}) {
    const std::string raw = raw_field(object, key);
    if (raw.empty() || raw == "null") return fallback;
    if (raw.front() == '"') {
        const auto value = unescape_json_string(raw);
        return value.empty() ? fallback : value;
    }
    return raw;
}

int64_t int_field(std::string_view object, std::string_view key, int64_t fallback = 0) {
    const std::string raw = raw_field(object, key);
    if (raw.empty() || raw == "null") return fallback;
    try {
        return std::stoll(raw);
    } catch (...) {
        return fallback;
    }
}

bool bool_field(std::string_view object, std::string_view key, bool fallback = false) {
    const std::string raw = raw_field(object, key);
    if (raw == "true") return true;
    if (raw == "false") return false;
    return fallback;
}

std::string nullable_string_raw(std::string_view object, std::string_view key) {
    const std::string raw = raw_field(object, key);
    if (raw.empty() || raw == "null") return "null";
    const std::string value = string_field(object, key);
    return value.empty() ? "null" : quoted(value);
}

std::string nullable_int_raw(std::string_view object, std::string_view key) {
    return has_field(object, key) && raw_field(object, key) != "null" ? std::to_string(int_field(object, key)) : "null";
}

std::string timestamp_raw(std::string_view object) {
    return nullable_int_raw(object, "ts");
}

void append(std::ostringstream &out, bool &first, std::string_view key, std::string_view raw_value) {
    if (!first) out << ',';
    first = false;
    out << quoted(std::string(key)) << ':' << raw_value;
}

std::vector<std::string> split_object_array(std::string_view array) {
    std::vector<std::string> objects;
    size_t offset = 0;
    skip_ws(array, offset);
    if (offset >= array.size() || array[offset] != '[') return objects;
    ++offset;
    while (offset < array.size()) {
        skip_ws(array, offset);
        if (offset < array.size() && array[offset] == ']') break;
        const size_t start = offset;
        const size_t end = skip_value(array, offset);
        if (start < array.size() && array[start] == '{') {
            objects.emplace_back(array.substr(start, end - start));
        }
        offset = end;
        skip_ws(array, offset);
        if (offset < array.size() && array[offset] == ',') ++offset;
    }
    return objects;
}

std::string int_array_raw(std::string_view object, std::string_view key) {
    const std::string raw = raw_field(object, key);
    if (raw.empty() || raw.front() != '[') return "[]";
    return raw;
}

} // namespace

std::vector<uint8_t> parse_mqtt_state_json(
    const std::string &serial_number,
    const std::string &payload_json,
    bool allow_online
) {
    std::ostringstream out;
    bool first = true;
    out << '{';
    append(out, first, "serialNumber", quoted(serial_number));
    append(out, first, "name", nullable_string_raw(payload_json, "name"));
    append(out, first, "isOnline", allow_online ? "true" : "false");
    append(out, first, "playing", bool_field(payload_json, "playing", false) ? "true" : "false");
    append(out, first, "currentFile", nullable_string_raw(payload_json, "currentFile"));
    append(out, first, "volume", std::to_string(std::clamp<int64_t>(int_field(payload_json, "volume", 35), 0, 100)));
    append(out, first, "servoAngle", nullable_int_raw(payload_json, "servoAngle"));
    append(out, first, "lastError", nullable_string_raw(payload_json, "lastError"));
    append(out, first, "network", nullable_string_raw(payload_json, "network"));
    std::string quality = string_field(payload_json, "outputQuality");
    if (quality.empty()) quality = string_field(payload_json, "audioQuality");
    if (quality.empty()) quality = string_field(payload_json, "quality");
    append(out, first, "outputQuality", quality.empty() ? "null" : quoted(quality));
    append(out, first, "timestamp", timestamp_raw(payload_json));
    out << '}';
    return to_bytes(out.str());
}

std::vector<uint8_t> parse_mqtt_ack_json(const std::string &payload_json) {
    std::ostringstream out;
    bool first = true;
    std::string msg_id = string_field(payload_json, "cmdId");
    if (msg_id.empty()) msg_id = string_field(payload_json, "msgId");
    std::string of_type = string_field(payload_json, "ofType");
    if (of_type.empty()) of_type = string_field(payload_json, "cmdName");
    out << '{';
    append(out, first, "msgId", quoted(msg_id));
    append(out, first, "ofType", quoted(of_type));
    append(out, first, "ofCmd", std::to_string(int_field(payload_json, "ofCmd", int_field(payload_json, "cmd", -1))));
    append(out, first, "ok", bool_field(payload_json, "ok", false) ? "true" : "false");
    append(out, first, "code", std::to_string(int_field(payload_json, "code", -1)));
    append(out, first, "message", quoted(string_field(payload_json, "msg")));
    append(out, first, "timestamp", timestamp_raw(payload_json));
    out << '}';
    return to_bytes(out.str());
}

std::vector<uint8_t> parse_mqtt_record_list_json(const std::string &payload_json) {
    std::string array_raw = raw_field(payload_json, "items");
    if (array_raw.empty()) array_raw = raw_field(payload_json, "records");
    const auto objects = split_object_array(array_raw);
    std::ostringstream records;
    records << '[';
    bool first_record = true;
    int64_t parsed_record_count = 0;
    for (const auto &item : objects) {
        const std::string record_id = string_field(item, "recordId");
        if (record_id.empty()) continue;
        ++parsed_record_count;
        if (!first_record) records << ',';
        first_record = false;
        bool first = true;
        records << '{';
        append(records, first, "recordId", quoted(record_id));
        const std::string name = string_field(item, "name", record_id);
        append(records, first, "name", quoted(name));
        append(records, first, "fileSize", std::to_string(int_field(item, "fileSize", 0)));
        append(records, first, "durationMs", std::to_string(int_field(item, "durationMs", 0)));
        append(records, first, "codec", quoted(string_field(item, "codec", "ima_adpcm")));
        append(records, first, "sampleRate", std::to_string(int_field(item, "sampleRate", 8000)));
        append(records, first, "channels", std::to_string(int_field(item, "channels", 1)));
        append(records, first, "packetMs", std::to_string(int_field(item, "packetMs", 40)));
        append(records, first, "crc32", nullable_string_raw(item, "crc32"));
        append(records, first, "createdAt", nullable_string_raw(item, "createdAt"));
        append(records, first, "createdMs", nullable_int_raw(item, "createdMs"));
        append(records, first, "path", nullable_string_raw(item, "path"));
        records << '}';
    }
    records << ']';

    std::ostringstream out;
    bool first = true;
    out << '{';
    append(out, first, "records", records.str());
    append(out, first, "offset", std::to_string(int_field(payload_json, "offset", 0)));
    append(out, first, "limit", std::to_string(std::clamp<int64_t>(int_field(payload_json, "limit", 8), 1, 8)));
    append(out, first, "total", std::to_string(int_field(payload_json, "total", int_field(payload_json, "count", parsed_record_count))));
    append(out, first, "hasMore", bool_field(payload_json, "hasMore", false) ? "true" : "false");
    append(out, first, "timestamp", timestamp_raw(payload_json));
    out << '}';
    return to_bytes(out.str());
}

std::vector<uint8_t> parse_mqtt_storage_status_json(const std::string &payload_json) {
    std::ostringstream out;
    bool first = true;
    out << '{';
    append(out, first, "ok", bool_field(payload_json, "ok", true) ? "true" : "false");
    append(out, first, "backend", nullable_string_raw(payload_json, "backend"));
    append(out, first, "totalBytes", std::to_string(int_field(payload_json, "totalBytes", 0)));
    append(out, first, "freeBytes", std::to_string(int_field(payload_json, "freeBytes", 0)));
    append(out, first, "recordCount", std::to_string(int_field(payload_json, "recordCount", 0)));
    append(out, first, "maxRecords", std::to_string(int_field(payload_json, "maxRecords", 0)));
    append(out, first, "code", std::to_string(int_field(payload_json, "code", 0)));
    append(out, first, "message", quoted(string_field(payload_json, "msg")));
    append(out, first, "timestamp", timestamp_raw(payload_json));
    out << '}';
    return to_bytes(out.str());
}

std::vector<uint8_t> parse_mqtt_record_event_json(
    const std::string &event_type,
    const std::string &payload_json
) {
    std::ostringstream out;
    bool first = true;
    out << '{';
    append(out, first, "type", quoted(event_type));
    append(out, first, "recordId", nullable_string_raw(payload_json, "recordId"));
    append(out, first, "ok", bool_field(payload_json, "ok", event_type != "record_failed") ? "true" : "false");
    append(out, first, "code", std::to_string(int_field(payload_json, "code", 0)));
    append(out, first, "message", quoted(string_field(payload_json, "msg")));
    append(out, first, "progress", std::to_string(int_field(payload_json, "progress", 0)));
    append(out, first, "downloadedBytes", std::to_string(int_field(payload_json, "downloadedBytes", 0)));
    append(out, first, "totalBytes", std::to_string(int_field(payload_json, "totalBytes", 0)));
    append(out, first, "headerSize", std::to_string(int_field(payload_json, "headerSize", int_field(payload_json, "headerBytes", 0))));
    append(out, first, "frameBytes", std::to_string(int_field(payload_json, "frameBytes", 0)));
    append(out, first, "samplesPerFrame", std::to_string(int_field(payload_json, "samplesPerFrame", 0)));
    append(out, first, "frameCount", std::to_string(int_field(payload_json, "frameCount", 0)));
    append(out, first, "audioBytes", std::to_string(int_field(payload_json, "audioBytes", 0)));
    append(out, first, "audioCrc32", nullable_string_raw(payload_json, "audioCrc32"));
    std::string file_crc = string_field(payload_json, "fileCrc32");
    if (file_crc.empty()) file_crc = string_field(payload_json, "crc32");
    append(out, first, "fileCrc32", file_crc.empty() ? "null" : quoted(file_crc));
    append(out, first, "firstSamples", int_array_raw(payload_json, "firstSamples"));
    append(out, first, "name", nullable_string_raw(payload_json, "name"));
    append(out, first, "fileSize", std::to_string(int_field(payload_json, "fileSize", 0)));
    append(out, first, "durationMs", std::to_string(int_field(payload_json, "durationMs", 0)));
    append(out, first, "codec", quoted(string_field(payload_json, "codec", "pcm16")));
    append(out, first, "sampleRate", std::to_string(int_field(payload_json, "sampleRate", 8000)));
    append(out, first, "channels", std::to_string(int_field(payload_json, "channels", 1)));
    append(out, first, "packetMs", std::to_string(int_field(payload_json, "packetMs", 40)));
    append(out, first, "crc32", nullable_string_raw(payload_json, "crc32"));
    append(out, first, "createdAt", nullable_string_raw(payload_json, "createdAt"));
    append(out, first, "storeTaskId", nullable_string_raw(payload_json, "storeTaskId"));
    append(out, first, "timestamp", timestamp_raw(payload_json));
    out << '}';
    return to_bytes(out.str());
}

} // namespace tji::speaker
