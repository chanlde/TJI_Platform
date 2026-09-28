#pragma once

#include "tji_speaker_core.h"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace tji::speaker {

// Ogg Opus 文件与 Android 调用侧共享的音频常量。
constexpr int kDefaultPcmSampleRate = 16000;
constexpr int kOpusPacketMs = 20;
constexpr int kOpusBitrate = 24000;

/** 完整 Ogg Opus 文件字节及上传、下载命令需要的元数据。 */
struct OggOpusResult {
    std::vector<uint8_t> data;
    TjiScOpusMetadata metadata{};
};

/** 从 PCM16 编码标准 Ogg Opus 文件。 */
OggOpusResult encode_ogg_opus(
    const uint8_t *pcm,
    size_t size,
    const std::string &record_id,
    int sample_rate,
    int channels,
    int packet_ms,
    int bitrate
);
/** 构造普通命令 JSON 外层结构。 */
std::vector<uint8_t> build_standard_command_json(
    const std::string &device_id,
    const std::string &msg_id,
    int command_code,
    const std::string &command_name,
    int64_t timestamp_ms,
    const std::string &params_json,
    const std::string &extra_json
);
/** 轻量单声道 PCM16 重采样器。 */
std::vector<uint8_t> resample_pcm16(
    const uint8_t *pcm,
    size_t size,
    int source_sample_rate,
    int target_sample_rate
);
/** 本地喇叭测试使用的正弦测试音生成器。 */
std::vector<uint8_t> generate_tone_pcm16(
    int frequency_hz,
    int duration_ms,
    int sample_rate,
    int min_duration_ms,
    int fade_ms,
    float amplitude
);
/** 给 PCM16 添加前导静音。 */
std::vector<uint8_t> prepend_silence_pcm16(
    const uint8_t *pcm,
    size_t size,
    int duration_ms,
    int sample_rate
);
/** 将 PCM16 补齐到固定帧字节大小。 */
std::vector<uint8_t> pad_pcm16_to_frame(
    const uint8_t *pcm,
    size_t size,
    size_t frame_bytes
);
/** 将 Android TTS 输出的 WAV 解码成单声道 PCM16。 */
std::vector<uint8_t> decode_wav_pcm16_mono(
    const uint8_t *wav,
    size_t size,
    int target_sample_rate
);
/** 将归一化浮点 PCM 转成 PCM16，可选重采样。 */
/** 归一化单片机 MQTT 状态负载 JSON。 */
std::vector<uint8_t> parse_mqtt_state_json(
    const std::string &serial_number,
    const std::string &payload_json,
    bool allow_online
);
/** 归一化单片机 MQTT 应答负载 JSON。 */
std::vector<uint8_t> parse_mqtt_ack_json(const std::string &payload_json);
/** 归一化单片机 MQTT 录音列表负载 JSON。 */
std::vector<uint8_t> parse_mqtt_record_list_json(const std::string &payload_json);
/** 归一化单片机 MQTT 存储状态负载 JSON。 */
std::vector<uint8_t> parse_mqtt_storage_status_json(const std::string &payload_json);
/** 归一化单片机 MQTT 录音事件负载 JSON。 */
std::vector<uint8_t> parse_mqtt_record_event_json(
    const std::string &event_type,
    const std::string &payload_json
);

/** Ogg Opus 元数据和原生/Kotlin 影子对照使用的 CRC32。 */
uint32_t crc32(const uint8_t *data, size_t size);
std::string format_crc32(uint32_t value);
/** 用零把任意字节补齐到指定帧大小。 */
std::vector<uint8_t> pad_frame(const uint8_t *data, size_t size, size_t frame_size);

// 编码器和分包器共用的小端字节工具。
void put_u8(std::vector<uint8_t> &out, uint8_t value);
void put_u16_le(std::vector<uint8_t> &out, uint16_t value);
void put_u32_le(std::vector<uint8_t> &out, uint32_t value);
uint16_t read_u16_le(const uint8_t *data);
int16_t read_i16_le(const uint8_t *data);
uint32_t read_u32_le(const uint8_t *data);

} // namespace tji::speaker
