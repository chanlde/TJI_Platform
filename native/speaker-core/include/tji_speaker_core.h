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

/** HADP 文件头和命令负载中使用的音频编码编号。 */
enum {
    TJI_SC_CODEC_IMA_ADPCM = 1,
    TJI_SC_CODEC_PCM16 = 2
};

/** 单片机中继协议理解的 UDP v2 流类型。 */
enum {
    TJI_SC_STREAM_PLAYBACK = 0,
    TJI_SC_STREAM_RECORD_STORE = 1,
    TJI_SC_STREAM_PLAYBACK_FEEDBACK = 2
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

/** 有状态 ADPCM 编码器句柄，用于跨帧保留 IMA 步进索引。 */
typedef struct TjiScAdpcmPacketizer TjiScAdpcmPacketizer;

/** 有状态实时语音处理器句柄，用于保留滤波器和 AGC 历史。 */
typedef struct TjiScVoiceProcessor TjiScVoiceProcessor;

/** 语音处理模式。实时喊话是逐帧有状态；按住说话和播放是整段无状态。 */
enum {
    TJI_SC_VOICE_PROFILE_LIVE = 0,
    TJI_SC_VOICE_PROFILE_PUSH_TO_TALK = 1,
    TJI_SC_VOICE_PROFILE_PLAYBACK = 2
};

/** HADP 编码后的元数据。CRC 字符串格式为 0xAABBCCDD。 */
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

/** 释放 speaker-core 返回的 TjiScBuffer，并把它重置为空。 */
void tji_sc_free(TjiScBuffer *buffer);

/**
 * 将单声道小端 PCM16 编码成 HADP 文件。
 *
 * @param pcm16le 输入 PCM 字节。当前喊话器链路期望单声道 PCM16。
 * @param pcm16le_size pcm16le 的字节数。末尾不成对的奇数字节会被编码器忽略。
 * @param record_id 写入 HADP 元数据区域的 UTF-8 录音编号。
 * @param codec_id TJI_SC_CODEC_PCM16 或 TJI_SC_CODEC_IMA_ADPCM。
 * @param sample_rate 输入/输出采样率，单位 Hz；App 质量档通常是 8000/16000/24000。
 * @param channels 声道数。当前正式喊话器链路期望 1。
 * @param packet_ms 包时长元数据，通常是 40。
 * @param out_file 输出 HADP 字节，用 tji_sc_free() 释放。
 * @param out_metadata 从生成的 HADP 文件头复制出来的元数据。
 */
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

/**
 * 将 HADP 文件解码成单声道小端 PCM16。
 *
 * 支持当前 PCM16 和 IMA ADPCM 两种 HADP 编码编号。
 * @param hadp 完整 HADP 文件字节。
 * @param hadp_size hadp 字节数。
 * @param out_pcm16le 输出 PCM 字节，用 tji_sc_free() 释放。
 */
int tji_sc_decode_hadp_pcm16(
    const uint8_t *hadp,
    size_t hadp_size,
    TjiScBuffer *out_pcm16le
);

/**
 * 从 40 ms PCM 帧构造一个旧版 v1 UDP ADPCM 包。
 *
 * 这个无状态辅助函数会对该帧重置 ADPCM 状态。
 * 连续实时流请使用 TjiScAdpcmPacketizer。
 */
int tji_sc_packetize_adpcm_legacy(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    TjiScBuffer *out_packet
);

/**
 * 构造一个带设备/任务/喊话编号的 v2 UDP ADPCM 包。
 *
 * 这个无状态辅助函数会对该帧重置 ADPCM 状态。
 * 连续实时流请使用 TjiScAdpcmPacketizer。
 */
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

/** 创建用于实时喊话或录音保存 UDP 流的有状态 ADPCM 分包器。 */
int tji_sc_adpcm_packetizer_create(TjiScAdpcmPacketizer **out_packetizer);

/** 释放由 tji_sc_adpcm_packetizer_create() 创建的分包器。 */
void tji_sc_adpcm_packetizer_free(TjiScAdpcmPacketizer *packetizer);

/** 将分包器内部 ADPCM 步进索引重置为初始状态。 */
void tji_sc_adpcm_packetizer_reset(TjiScAdpcmPacketizer *packetizer);

/** 有状态旧版分包；序号和时间戳仍由调用方提供。 */
int tji_sc_adpcm_packetizer_packetize_legacy(
    TjiScAdpcmPacketizer *packetizer,
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    TjiScBuffer *out_packet
);

/** 有状态 v2 分包；序号、时间戳和编号字段由调用方提供。 */
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
);

/**
 * 按指定语音模式处理一整段 PCM。
 *
 * @param profile App 整段音频路径通常使用 TJI_SC_VOICE_PROFILE_PUSH_TO_TALK 或 TJI_SC_VOICE_PROFILE_PLAYBACK。
 * @param sample_rate PCM 采样率，单位 Hz。PTT 当前会归一到喊话器 8 kHz 链路。
 * @param bass_db 低频搁架均衡增益，单位 dB；内部会夹到语音支持范围。
 * @param treble_db 高频搁架均衡增益，单位 dB；内部会夹到语音支持范围。
 * @param out_pcm16le 处理后的单声道 PCM16，用 tji_sc_free() 释放。
 */
int tji_sc_process_voice(
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    int profile,
    int sample_rate,
    float bass_db,
    float treble_db,
    TjiScBuffer *out_pcm16le
);

/** 创建用于连续 40 ms 麦克风帧的有状态实时语音处理器。 */
int tji_sc_voice_processor_create(TjiScVoiceProcessor **out_processor);

/** 释放由 tji_sc_voice_processor_create() 创建的实时语音处理器。 */
void tji_sc_voice_processor_free(TjiScVoiceProcessor *processor);

/** 重置实时滤波器历史和 AGC 状态；开始新实时喊话会话时调用。 */
void tji_sc_voice_processor_reset(TjiScVoiceProcessor *processor);

/**
 * 处理一帧实时麦克风音频，并保留滤波器/AGC 历史。
 *
 * 用于实时 UDP 喊话。录音 PTT 整段音频请使用 tji_sc_process_voice()。
 */
int tji_sc_voice_processor_process_frame(
    TjiScVoiceProcessor *processor,
    const uint8_t *pcm16le,
    size_t pcm16le_size,
    float bass_db,
    float treble_db,
    TjiScBuffer *out_pcm16le
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
 * 构造 RECORD_DOWNLOAD MQTT 控制命令 JSON 负载。
 *
 * 这里和 Android App 命令负载保持一致，放在 C++ 里是为了后续 Qt 工具
 * 能生成完全相同的命令体。
 */
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

/**
 * 将归一化浮点采样转成单声道小端 PCM16。
 *
 * 采样值会夹到 -1.0..1.0。如果源采样率和目标采样率不同，
 * 使用线性插值重采样。
 */
int tji_sc_float32_to_pcm16(
    const float *samples,
    size_t sample_count,
    int source_sample_rate,
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

/** 计算 HADP 元数据和影子对照日志使用的同一套 CRC32 基础值。 */
uint32_t tji_sc_crc32(const uint8_t *data, size_t size);

#ifdef __cplusplus
}
#endif
