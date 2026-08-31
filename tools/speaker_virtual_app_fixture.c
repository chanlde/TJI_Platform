/**
 * @file speaker_virtual_app_fixture.c
 * @brief Generate a deterministic, quiet 48 kHz/20 ms Ogg Opus fixture.
 *
 * This host-only helper intentionally uses the same bundled libopus source as
 * the MCU/App interoperability tests.  It is not linked into production code.
 */

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "opus.h"

#define FIXTURE_RATE_HZ       48000
#define FIXTURE_FRAME_SAMPLES 960
#define FIXTURE_BITRATE_BPS   32000
#define FIXTURE_SERIAL        0x544A4931UL
#define FIXTURE_MAX_PACKET    1275
#define FIXTURE_PI            3.14159265358979323846

static void put_le16(uint8_t *out, uint16_t value)
{
    out[0] = (uint8_t)value;
    out[1] = (uint8_t)(value >> 8);
}

static void put_le32(uint8_t *out, uint32_t value)
{
    out[0] = (uint8_t)value;
    out[1] = (uint8_t)(value >> 8);
    out[2] = (uint8_t)(value >> 16);
    out[3] = (uint8_t)(value >> 24);
}

static void put_le64(uint8_t *out, uint64_t value)
{
    put_le32(out, (uint32_t)value);
    put_le32(out + 4, (uint32_t)(value >> 32));
}

static uint32_t ogg_crc(const uint8_t *data, size_t length)
{
    uint32_t crc = 0U;
    size_t i;
    unsigned bit;

    for (i = 0U; i < length; ++i) {
        crc ^= (uint32_t)data[i] << 24;
        for (bit = 0U; bit < 8U; ++bit) {
            crc = (crc & 0x80000000UL) != 0U ?
                  (crc << 1) ^ 0x04C11DB7UL : crc << 1;
        }
    }
    return crc;
}

static int write_page(FILE *output,
                      const uint8_t *packet,
                      uint16_t packet_bytes,
                      uint8_t flags,
                      uint64_t granule,
                      uint32_t sequence)
{
    uint8_t page[27U + 6U + FIXTURE_MAX_PACKET];
    uint8_t lacing[6];
    uint16_t remaining = packet_bytes;
    uint8_t lacing_count = 0U;
    size_t page_bytes;

    do {
        uint8_t segment = remaining > 255U ? 255U : (uint8_t)remaining;
        lacing[lacing_count++] = segment;
        remaining = (uint16_t)(remaining - segment);
    } while (remaining > 0U);
    if (packet_bytes > 0U && packet_bytes % 255U == 0U) {
        lacing[lacing_count++] = 0U;
    }
    if (lacing_count > sizeof(lacing)) {
        return -1;
    }

    memset(page, 0, sizeof(page));
    memcpy(page, "OggS", 4U);
    page[4] = 0U;
    page[5] = flags;
    put_le64(&page[6], granule);
    put_le32(&page[14], FIXTURE_SERIAL);
    put_le32(&page[18], sequence);
    page[26] = lacing_count;
    memcpy(&page[27], lacing, lacing_count);
    memcpy(&page[27U + lacing_count], packet, packet_bytes);
    page_bytes = 27U + lacing_count + packet_bytes;
    put_le32(&page[22], ogg_crc(page, page_bytes));
    return fwrite(page, 1U, page_bytes, output) == page_bytes ? 0 : -1;
}

static int16_t fixture_sample(uint32_t index, int amplitude)
{
    const double phase = (double)index / (double)FIXTURE_RATE_HZ;
    double sample = sin(2.0 * FIXTURE_PI * 440.0 * phase) * 0.70 +
                    sin(2.0 * FIXTURE_PI * 733.0 * phase) * 0.30;

    /* A 20 ms fade prevents the fixture itself from producing edge clicks. */
    if (index < FIXTURE_FRAME_SAMPLES) {
        sample *= (double)index / (double)FIXTURE_FRAME_SAMPLES;
    }
    if (sample > 1.0) sample = 1.0;
    if (sample < -1.0) sample = -1.0;
    return (int16_t)(sample * (double)amplitude);
}

int main(int argc, char **argv)
{
    const char *path;
    unsigned duration_ms = 4000U;
    int amplitude = 1200;
    uint32_t input_samples;
    uint32_t packet_count;
    uint32_t packet_index;
    uint32_t pre_skip;
    uint8_t head[19] = {
        'O', 'p', 'u', 's', 'H', 'e', 'a', 'd',
        1U, 1U, 0U, 0U, 0x80U, 0xBBU, 0U, 0U, 0U, 0U, 0U
    };
    static const uint8_t tags[8] = {
        'O', 'p', 'u', 's', 'T', 'a', 'g', 's'
    };
    int16_t frame[FIXTURE_FRAME_SAMPLES];
    uint8_t encoded[FIXTURE_MAX_PACKET];
    OpusEncoder *encoder = NULL;
    FILE *output = NULL;
    int error;
    int lookahead;

    if (argc < 2 || argc > 4) {
        fprintf(stderr, "usage: %s OUTPUT.opus [duration_ms] [amplitude]\n", argv[0]);
        return 2;
    }
    path = argv[1];
    if (argc >= 3) duration_ms = (unsigned)strtoul(argv[2], NULL, 10);
    if (argc >= 4) amplitude = (int)strtol(argv[3], NULL, 10);
    if (duration_ms < 200U || duration_ms > 60000U ||
        amplitude < 1 || amplitude > 4000) {
        fprintf(stderr, "duration must be 200..60000 ms; amplitude 1..4000\n");
        return 2;
    }

    input_samples = (uint32_t)(((uint64_t)duration_ms * FIXTURE_RATE_HZ) / 1000U);
    encoder = opus_encoder_create(FIXTURE_RATE_HZ, 1, OPUS_APPLICATION_VOIP, &error);
    if (encoder == NULL || error != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_SET_BITRATE(FIXTURE_BITRATE_BPS)) != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_SET_VBR(1)) != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(5)) != OPUS_OK ||
        opus_encoder_ctl(encoder, OPUS_GET_LOOKAHEAD(&lookahead)) != OPUS_OK) {
        fprintf(stderr, "failed to initialize Opus encoder\n");
        if (encoder != NULL) opus_encoder_destroy(encoder);
        return 1;
    }
    pre_skip = (uint32_t)lookahead;
    if (pre_skip > UINT16_MAX) {
        opus_encoder_destroy(encoder);
        return 1;
    }
    put_le16(&head[10], (uint16_t)pre_skip);

    output = fopen(path, "wb");
    if (output == NULL ||
        write_page(output, head, sizeof(head), 0x02U, 0U, 0U) != 0 ||
        write_page(output, tags, sizeof(tags), 0U, 0U, 1U) != 0) {
        fprintf(stderr, "failed to create fixture\n");
        if (output != NULL) fclose(output);
        opus_encoder_destroy(encoder);
        return 1;
    }

    packet_count = (input_samples + pre_skip + FIXTURE_FRAME_SAMPLES - 1U) /
                   FIXTURE_FRAME_SAMPLES;
    for (packet_index = 0U; packet_index < packet_count; ++packet_index) {
        uint32_t encoded_offset = packet_index * FIXTURE_FRAME_SAMPLES;
        uint32_t sample_index;
        int encoded_bytes;
        uint64_t granule;
        uint8_t flags = 0U;

        memset(frame, 0, sizeof(frame));
        for (sample_index = 0U; sample_index < FIXTURE_FRAME_SAMPLES; ++sample_index) {
            uint32_t encoded_index = encoded_offset + sample_index;
            if (encoded_index >= pre_skip) {
                uint32_t input_index = encoded_index - pre_skip;
                if (input_index < input_samples) {
                    frame[sample_index] = fixture_sample(input_index, amplitude);
                }
            }
        }
        encoded_bytes = opus_encode(encoder, frame, FIXTURE_FRAME_SAMPLES,
                                    encoded, sizeof(encoded));
        if (encoded_bytes <= 0) {
            fprintf(stderr, "Opus encode failed at packet %u\n", packet_index);
            fclose(output);
            opus_encoder_destroy(encoder);
            return 1;
        }
        if (packet_index + 1U == packet_count) {
            flags = 0x04U;
            granule = pre_skip + input_samples;
        } else {
            granule = (uint64_t)(packet_index + 1U) * FIXTURE_FRAME_SAMPLES;
        }
        if (write_page(output, encoded, (uint16_t)encoded_bytes, flags,
                       granule, packet_index + 2U) != 0) {
            fprintf(stderr, "failed to write Ogg page\n");
            fclose(output);
            opus_encoder_destroy(encoder);
            return 1;
        }
    }

    if (fclose(output) != 0) {
        opus_encoder_destroy(encoder);
        return 1;
    }
    opus_encoder_destroy(encoder);
    printf("fixture=%s durationMs=%u packets=%u amplitude=%d\n",
           path, duration_ms, packet_count, amplitude);
    return 0;
}
