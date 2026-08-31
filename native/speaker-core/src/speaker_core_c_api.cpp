#include "speaker_core_internal.h"

#include <cstdlib>
#include <cstring>
#include <new>
#include <stdexcept>

namespace {

int copy_to_c_buffer(const std::vector<uint8_t> &source, TjiScBuffer *out) {
    if (out == nullptr) return TJI_SC_INVALID_ARGUMENT;
    out->data = nullptr;
    out->size = 0;
    if (source.empty()) return TJI_SC_OK;
    auto *data = static_cast<uint8_t *>(std::malloc(source.size()));
    if (data == nullptr) return TJI_SC_ALLOC_ERROR;
    std::memcpy(data, source.data(), source.size());
    out->data = data;
    out->size = source.size();
    return TJI_SC_OK;
}

int translate_exception() {
    try {
        throw;
    } catch (const std::bad_alloc &) {
        return TJI_SC_ALLOC_ERROR;
    } catch (const std::invalid_argument &) {
        return TJI_SC_INVALID_ARGUMENT;
    } catch (...) {
        return TJI_SC_DECODE_ERROR;
    }
}

} // namespace

extern "C" {

void tji_sc_free(TjiScBuffer *buffer) {
    if (buffer == nullptr) return;
    std::free(buffer->data);
    buffer->data = nullptr;
    buffer->size = 0;
}

int tji_sc_encode_ogg_opus(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    const char *record_id,
    int sample_rate,
    int channels,
    int packet_ms,
    int bitrate,
    TjiScBuffer *out_file,
    TjiScOpusMetadata *out_metadata
) {
    if (out_file == nullptr || out_metadata == nullptr || record_id == nullptr) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        const auto result = tji::speaker::encode_ogg_opus(
            pcm16le,
            pcm16le_size,
            record_id,
            sample_rate,
            channels,
            packet_ms,
            bitrate
        );
        *out_metadata = result.metadata;
        return copy_to_c_buffer(result.data, out_file);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_build_standard_command_json(
    const char *device_id,
    const char *msg_id,
    int command_code,
    const char *command_name,
    int64_t timestamp_ms,
    const char *params_json,
    const char *extra_json,
    TjiScBuffer *out_json
) {
    if (out_json == nullptr || device_id == nullptr || msg_id == nullptr || command_name == nullptr) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        return copy_to_c_buffer(
            tji::speaker::build_standard_command_json(
                device_id,
                msg_id,
                command_code,
                command_name,
                timestamp_ms,
                params_json == nullptr ? "" : params_json,
                extra_json == nullptr ? "" : extra_json
            ),
            out_json
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_resample_pcm16(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int source_sample_rate,
    int target_sample_rate,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::resample_pcm16(
                pcm16le,
                pcm16le_size,
                source_sample_rate,
                target_sample_rate
            ),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_generate_tone_pcm16(
    int frequency_hz,
    int duration_ms,
    int sample_rate,
    int min_duration_ms,
    int fade_ms,
    float amplitude,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::generate_tone_pcm16(
                frequency_hz,
                duration_ms,
                sample_rate,
                min_duration_ms,
                fade_ms,
                amplitude
            ),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_prepend_silence_pcm16(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int duration_ms,
    int sample_rate,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::prepend_silence_pcm16(pcm16le, pcm16le_size, duration_ms, sample_rate),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_pad_pcm16_to_frame(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    size_t frame_bytes,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::pad_pcm16_to_frame(pcm16le, pcm16le_size, frame_bytes),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_decode_wav_pcm16_mono(
    const uint8_t *wav,
    size_t wav_size,
    int target_sample_rate,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::decode_wav_pcm16_mono(wav, wav_size, target_sample_rate),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_parse_mqtt_state_json(
    const char *serial_number,
    const char *payload_json,
    int allow_online,
    TjiScBuffer *out_json
) {
    if (serial_number == nullptr || payload_json == nullptr || out_json == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::parse_mqtt_state_json(serial_number, payload_json, allow_online != 0),
            out_json
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_parse_mqtt_ack_json(
    const char *payload_json,
    TjiScBuffer *out_json
) {
    if (payload_json == nullptr || out_json == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(tji::speaker::parse_mqtt_ack_json(payload_json), out_json);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_parse_mqtt_record_list_json(
    const char *payload_json,
    TjiScBuffer *out_json
) {
    if (payload_json == nullptr || out_json == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(tji::speaker::parse_mqtt_record_list_json(payload_json), out_json);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_parse_mqtt_storage_status_json(
    const char *payload_json,
    TjiScBuffer *out_json
) {
    if (payload_json == nullptr || out_json == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(tji::speaker::parse_mqtt_storage_status_json(payload_json), out_json);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_parse_mqtt_record_event_json(
    const char *event_type,
    const char *payload_json,
    TjiScBuffer *out_json
) {
    if (event_type == nullptr || payload_json == nullptr || out_json == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(tji::speaker::parse_mqtt_record_event_json(event_type, payload_json), out_json);
    } catch (...) {
        return translate_exception();
    }
}

uint32_t tji_sc_crc32(const uint8_t *data, size_t size) {
    return tji::speaker::crc32(data, size);
}

} // extern "C"
