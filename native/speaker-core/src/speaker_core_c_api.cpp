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

struct TjiScAdpcmPacketizer {
    int step_index = 0;
};

struct TjiScVoiceProcessor {
    tji::speaker::VoiceProcessor processor;
};

extern "C" {

void tji_sc_free(TjiScBuffer *buffer) {
    if (buffer == nullptr) return;
    std::free(buffer->data);
    buffer->data = nullptr;
    buffer->size = 0;
}

int tji_sc_encode_hadp(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    const char *record_id,
    int codec_id,
    int sample_rate,
    int channels,
    int packet_ms,
    TjiScBuffer *out_file,
    TjiScHadpMetadata *out_metadata
) {
    if (out_file == nullptr || out_metadata == nullptr || record_id == nullptr) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        const auto result = tji::speaker::encode_hadp(
            pcm16le,
            pcm16le_size,
            record_id,
            codec_id,
            sample_rate,
            channels,
            packet_ms
        );
        *out_metadata = result.metadata;
        return copy_to_c_buffer(result.data, out_file);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_decode_hadp_pcm16(
    const uint8_t *hadp,
    size_t hadp_size,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(tji::speaker::decode_hadp_pcm16(hadp, hadp_size), out_pcm16le);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_packetize_adpcm_legacy(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    TjiScBuffer *out_packet
) {
    if (out_packet == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::packetize_adpcm_legacy(pcm16le, pcm16le_size, sequence, timestamp_samples),
            out_packet
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_packetize_adpcm_v2(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const char *device_id,
    const char *task_id,
    const char *talk_id,
    int stream_type,
    int is_last_packet,
    TjiScBuffer *out_packet
) {
    if (out_packet == nullptr || device_id == nullptr || task_id == nullptr || talk_id == nullptr) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        return copy_to_c_buffer(
            tji::speaker::packetize_adpcm_v2(
                pcm16le,
                pcm16le_size,
                sequence,
                timestamp_ms,
                device_id,
                task_id,
                talk_id,
                stream_type,
                is_last_packet != 0
            ),
            out_packet
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_adpcm_packetizer_create(TjiScAdpcmPacketizer **out_packetizer) {
    if (out_packetizer == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        *out_packetizer = new TjiScAdpcmPacketizer();
        return TJI_SC_OK;
    } catch (...) {
        *out_packetizer = nullptr;
        return translate_exception();
    }
}

void tji_sc_adpcm_packetizer_free(TjiScAdpcmPacketizer *packetizer) {
    delete packetizer;
}

void tji_sc_adpcm_packetizer_reset(TjiScAdpcmPacketizer *packetizer) {
    if (packetizer != nullptr) {
        packetizer->step_index = 0;
    }
}

int tji_sc_adpcm_packetizer_packetize_legacy(
    TjiScAdpcmPacketizer *packetizer,
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    TjiScBuffer *out_packet
) {
    if (packetizer == nullptr || out_packet == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        auto result = tji::speaker::packetize_adpcm_legacy(
            pcm16le,
            pcm16le_size,
            sequence,
            timestamp_samples,
            packetizer->step_index
        );
        packetizer->step_index = result.next_step_index;
        return copy_to_c_buffer(result.packet, out_packet);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_adpcm_packetizer_packetize_v2(
    TjiScAdpcmPacketizer *packetizer,
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const char *device_id,
    const char *task_id,
    const char *talk_id,
    int stream_type,
    int is_last_packet,
    TjiScBuffer *out_packet
) {
    if (
        packetizer == nullptr ||
        out_packet == nullptr ||
        device_id == nullptr ||
        task_id == nullptr ||
        talk_id == nullptr
    ) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        auto result = tji::speaker::packetize_adpcm_v2(
            pcm16le,
            pcm16le_size,
            sequence,
            timestamp_ms,
            device_id,
            task_id,
            talk_id,
            stream_type,
            is_last_packet != 0,
            packetizer->step_index
        );
        packetizer->step_index = result.next_step_index;
        return copy_to_c_buffer(result.packet, out_packet);
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_process_voice(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int profile,
    int sample_rate,
    float bass_db,
    float treble_db,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::process_voice(
                pcm16le,
                pcm16le_size,
                profile,
                sample_rate,
                tji::speaker::VoiceToneSettings{bass_db, treble_db}
            ),
            out_pcm16le
        );
    } catch (...) {
        return translate_exception();
    }
}

int tji_sc_voice_processor_create(TjiScVoiceProcessor **out_processor) {
    if (out_processor == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        *out_processor = new TjiScVoiceProcessor();
        return TJI_SC_OK;
    } catch (...) {
        *out_processor = nullptr;
        return translate_exception();
    }
}

void tji_sc_voice_processor_free(TjiScVoiceProcessor *processor) {
    delete processor;
}

void tji_sc_voice_processor_reset(TjiScVoiceProcessor *processor) {
    if (processor != nullptr) {
        processor->processor.reset();
    }
}

int tji_sc_voice_processor_process_frame(
    TjiScVoiceProcessor *processor,
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    float bass_db,
    float treble_db,
    TjiScBuffer *out_pcm16le
) {
    if (processor == nullptr || out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            processor->processor.process_frame(
                pcm16le,
                pcm16le_size,
                tji::speaker::VoiceToneSettings{bass_db, treble_db}
            ),
            out_pcm16le
        );
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

int tji_sc_build_record_download_command_json(
    const char *device_id,
    const char *msg_id,
    const char *record_id,
    const char *store_task_id,
    const char *created_at,
    const char *name,
    const char *download_url,
    int64_t file_size,
    const char *crc32,
    int duration_ms,
    const char *codec,
    int sample_rate,
    int channels,
    int packet_ms,
    int frame_bytes,
    int samples_per_frame,
    int verify_only,
    const char *verify_kind,
    const char *expected_audio_crc32,
    const char *expected_first_samples_json,
    int temporary,
    int visible,
    int auto_play,
    int playback_volume,
    int has_playback_volume,
    TjiScBuffer *out_json
) {
    if (
        out_json == nullptr ||
        device_id == nullptr ||
        msg_id == nullptr ||
        record_id == nullptr ||
        store_task_id == nullptr ||
        created_at == nullptr ||
        name == nullptr ||
        download_url == nullptr ||
        crc32 == nullptr ||
        codec == nullptr
    ) {
        return TJI_SC_INVALID_ARGUMENT;
    }
    try {
        return copy_to_c_buffer(
            tji::speaker::build_record_download_command_json(
                device_id,
                msg_id,
                record_id,
                store_task_id,
                created_at,
                name,
                download_url,
                file_size,
                crc32,
                duration_ms,
                codec,
                sample_rate,
                channels,
                packet_ms,
                frame_bytes,
                samples_per_frame,
                verify_only != 0,
                verify_kind == nullptr ? "" : verify_kind,
                expected_audio_crc32 == nullptr ? "" : expected_audio_crc32,
                expected_first_samples_json == nullptr ? "[]" : expected_first_samples_json,
                temporary != 0,
                visible != 0,
                auto_play != 0,
                playback_volume,
                has_playback_volume != 0
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

int tji_sc_float32_to_pcm16(
    const float *samples,
    size_t sample_count,
    int source_sample_rate,
    int target_sample_rate,
    TjiScBuffer *out_pcm16le
) {
    if (out_pcm16le == nullptr) return TJI_SC_INVALID_ARGUMENT;
    try {
        return copy_to_c_buffer(
            tji::speaker::float32_to_pcm16(samples, sample_count, source_sample_rate, target_sample_rate),
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
