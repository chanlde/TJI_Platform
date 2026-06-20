#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum {
    TJI_SC_OK = 0,
    TJI_SC_INVALID_ARGUMENT = 1,
    TJI_SC_UNSUPPORTED = 2,
    TJI_SC_DECODE_ERROR = 3,
    TJI_SC_ALLOC_ERROR = 4
};

enum {
    TJI_SC_CODEC_IMA_ADPCM = 1,
    TJI_SC_CODEC_PCM16 = 2
};

enum {
    TJI_SC_STREAM_PLAYBACK = 0,
    TJI_SC_STREAM_RECORD_STORE = 1,
    TJI_SC_STREAM_PLAYBACK_FEEDBACK = 2
};

typedef struct TjiScBuffer {
    uint8_t *data;
    size_t size;
} TjiScBuffer;

typedef struct TjiScHadpMetadata {
    int codec_id;
    int sample_rate;
    int channels;
    int packet_ms;
    int frame_bytes;
    int samples_per_frame;
    int frame_count;
    int audio_bytes;
    int duration_ms;
    int file_size;
    char crc32[11];
    char audio_crc32[11];
} TjiScHadpMetadata;

void tji_sc_free(TjiScBuffer *buffer);

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
);

int tji_sc_decode_hadp_pcm16(
    const uint8_t *hadp,
    size_t hadp_size,
    TjiScBuffer *out_pcm16le
);

int tji_sc_packetize_adpcm_legacy(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    TjiScBuffer *out_packet
);

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
);

uint32_t tji_sc_crc32(const uint8_t *data, size_t size);

#ifdef __cplusplus
}
#endif

