#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 所有可能失败的 C ABI 函数都会返回这些状态码。
 *
 * TJI_SC_OK 表示输出参数有效。非 0 状态表示调用方不应使用输出内容；
 * 但如果传入了 TjiScBuffer，仍可以安全调用 tji_sc_free() 清理可能已初始化的缓冲区。
 */
enum {
    TJI_SC_OK = 0,
    TJI_SC_INVALID_ARGUMENT = 1,
    TJI_SC_UNSUPPORTED = 2,
    TJI_SC_DECODE_ERROR = 3,
    TJI_SC_ALLOC_ERROR = 4
};

/**
 * speaker-core 返回的堆内存字节缓冲区。
 *
 * 调用成功后 data 归调用方所有，必须用 tji_sc_free() 释放。
 * 空输出用 data == NULL 且 size == 0 表示。
 */
typedef struct TjiScBuffer {
    uint8_t *data;
    size_t size;
} TjiScBuffer;

/** Ogg Opus 编码结果的元数据。CRC 字符串格式为 0xAABBCCDD。 */
typedef struct TjiScOpusMetadata {
    int sample_rate;
    int channels;
    int packet_ms;
    int samples_per_frame;
    int packet_count;
    int audio_bytes;
    int duration_ms;
    int file_size;
    int bitrate;
    int pre_skip_48k;
    char crc32[11];
} TjiScOpusMetadata;

/** 释放 speaker-core 返回的 TjiScBuffer，并把它重置为空。 */
void tji_sc_free(TjiScBuffer *buffer);

/**
 * 将单声道小端 PCM16 编码成标准 Ogg Opus 文件。
 *
 * @param pcm16le 输入 PCM 字节。当前喊话器链路期望单声道 PCM16。
 * @param pcm16le_size pcm16le 的字节数。末尾不成对的奇数字节会被编码器忽略。
 * @param record_id 写入 OpusTags 的 UTF-8 录音编号。
 * @param sample_rate 输入采样率，支持 Opus 标准采样率。
 * @param channels 当前正式喊话器链路固定为 1。
 * @param packet_ms 当前正式链路固定为 20 ms。
 * @param bitrate 目标码率，单位 bit/s。
 * @param out_file 输出 .opus 文件字节，用 tji_sc_free() 释放。
 * @param out_metadata 编码结果元数据。
 */
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
);

/**
 * 构造标准 MQTT 控制命令 JSON 负载。
 *
 * params_json 和 extra_json 必须是合法 JSON 对象字符串，或空字符串。
 * 返回缓冲区是 UTF-8 JSON 字节，不带结尾 NUL。
 */
int tji_sc_build_standard_command_json(
    const char *device_id,
    const char *msg_id,
    int command_code,
    const char *command_name,
    int64_t timestamp_ms,
    const char *params_json,
    const char *extra_json,
    TjiScBuffer *out_json
);

/**
 * 对单声道小端 PCM16 做线性重采样。
 *
 * 对当前 8 kHz 语音链路够用；它不是高端 sinc 或多相滤波重采样器。
 */
int tji_sc_resample_pcm16(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int source_sample_rate,
    int target_sample_rate,
    TjiScBuffer *out_pcm16le
);

/**
 * 生成单声道小端 PCM16 正弦测试音，可带边缘淡入淡出。
 *
 * amplitude 会夹到 0.0..1.0；duration_ms 小于 min_duration_ms 时会抬到最小值。
 */
int tji_sc_generate_tone_pcm16(
    int frequency_hz,
    int duration_ms,
    int sample_rate,
    int min_duration_ms,
    int fade_ms,
    float amplitude,
    TjiScBuffer *out_pcm16le
);

/** 在 PCM16 前面补零值静音；duration_ms 会按 0 做下限。 */
int tji_sc_prepend_silence_pcm16(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int duration_ms,
    int sample_rate,
    TjiScBuffer *out_pcm16le
);

/** 用静音补齐 PCM16，直到字节数成为指定帧字节数的整数倍。 */
int tji_sc_pad_pcm16_to_frame(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    size_t frame_bytes,
    TjiScBuffer *out_pcm16le
);

/**
 * 将 PCM WAV 文件解码成目标采样率的单声道小端 PCM16。
 *
 * 支持 16 位 RIFF/WAVE PCM。多声道会先按声道平均混成单声道，再重采样。
 */
int tji_sc_decode_wav_pcm16_mono(
    const uint8_t *wav,
    size_t wav_size,
    int target_sample_rate,
    TjiScBuffer *out_pcm16le
);

/** 解析 MQTT 状态负载，并返回归一化 UTF-8 JSON。 */
int tji_sc_parse_mqtt_state_json(
    const char *serial_number,
    const char *payload_json,
    int allow_online,
    TjiScBuffer *out_json
);

/** 解析 MQTT 应答负载，并返回归一化 UTF-8 JSON。 */
int tji_sc_parse_mqtt_ack_json(
    const char *payload_json,
    TjiScBuffer *out_json
);

/** 解析 MQTT 录音列表负载，并返回归一化 UTF-8 JSON。 */
int tji_sc_parse_mqtt_record_list_json(
    const char *payload_json,
    TjiScBuffer *out_json
);

/** 解析 MQTT 存储状态负载，并返回归一化 UTF-8 JSON。 */
int tji_sc_parse_mqtt_storage_status_json(
    const char *payload_json,
    TjiScBuffer *out_json
);

/** 解析 MQTT 录音事件负载，并返回归一化 UTF-8 JSON。 */
int tji_sc_parse_mqtt_record_event_json(
    const char *event_type,
    const char *payload_json,
    TjiScBuffer *out_json
);

/** 计算 Ogg Opus 元数据和影子对照日志使用的同一套 CRC32 基础值。 */
uint32_t tji_sc_crc32(const uint8_t *data, size_t size);

#ifdef __cplusplus
}
#endif
