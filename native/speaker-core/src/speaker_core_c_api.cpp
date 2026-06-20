#include "speaker_core_internal.h"

#include <cstdlib>
#include <cstring>
#include <new>

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

uint32_t tji_sc_crc32(const uint8_t *data, size_t size) {
    return tji::speaker::crc32(data, size);
}

} // extern "C"
