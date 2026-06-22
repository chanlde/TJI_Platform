#pragma once

#include "tji_speaker_core.h"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace tji::speaker {

// HADP、ADPCM UDP、Android/Qt 调用侧共享的协议常量。
constexpr int kSampleRate = 8000;
constexpr int kChannels = 1;
constexpr int kPacketMs = 40;
constexpr int kPcmFrameBytes = kSampleRate * kPacketMs / 1000 * 2;
constexpr int kLegacyUdpHeaderBytes = 20;
constexpr int kV2FixedHeaderBytes = 28;
constexpr int kHadpHeaderBytes = 128;
constexpr int kHadpVersion = 1;
constexpr int kAdpcmFrameBytes = 164;
constexpr int kAdpcmSamplesPerFrame = 320;
constexpr uint16_t kUdpMagic = 0xA55A;

/** 一个 IMA ADPCM 帧，以及连续流所需的下一帧步进索引。 */
struct EncodedAdpcm {
    std::vector<uint8_t> payload;
    int sample_count = 0;
    int next_step_index = 0;
};

/** 完整 HADP 文件字节，以及从生成文件头中解析出的元数据。 */
struct HadpResult {
    std::vector<uint8_t> data;
    TjiScHadpMetadata metadata{};
};

/** UDP 包字节，以及有状态分包所需的下一帧 ADPCM 步进索引。 */
struct PacketizedAdpcm {
    std::vector<uint8_t> packet;
    int next_step_index = 0;
};

/** Android/Qt 传入的用户音色控制；数值会在 DSP 链路内部夹到安全范围。 */
struct VoiceToneSettings {
    float bass_db = 0.0f;
    float treble_db = 0.0f;
};

/** 实时语音 EQ 使用的双二阶滤波器状态。 */
struct VoiceBiquadState {
    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;
    float z1 = 0.0f;
    float z2 = 0.0f;
};

/**
 * 有状态实时语音处理器。
 *
 * 实时喊话会连续收到 40 ms 帧，所以这里会在帧与帧之间保留滤波器和 AGC 记忆。
 * 录音按住说话和 TTS 播放使用无状态辅助函数。
 */
class VoiceProcessor {
public:
    /** 处理一帧单声道 PCM16，并返回单声道 PCM16。 */
    std::vector<uint8_t> process_frame(const uint8_t *pcm, size_t size, VoiceToneSettings tone);
    /** 在新实时会话开始前清空高通、清晰度增强和 AGC 历史。 */
    void reset();

private:
    // 高通和清晰度增强的历史状态用于减少实时喊话帧边界的咔嗒声。
    float previous_input_ = 0.0f;
    float previous_high_pass_ = 0.0f;
    float previous_presence_ = 0.0f;
    // 平滑后的实时 AGC 增益用于减少相邻 40 ms 帧之间的忽大忽小。
    float previous_agc_gain_ = 1.0f;
    // 与 Kotlin SpeakerToneEqualizer 一致：实时模式下 EQ 滤波器历史跨帧保留。
    VoiceBiquadState bass_shelf_{};
    VoiceBiquadState treble_shelf_{};
};

/** 将一个 PCM16 块编码成 IMA ADPCM 负载字节。 */
EncodedAdpcm encode_ima_adpcm_block(const uint8_t *pcm, size_t length, int initial_step_index);
/** 将一个 IMA ADPCM 块解码成 PCM16。 */
std::vector<uint8_t> decode_ima_adpcm_block(const uint8_t *block, size_t block_size, int expected_samples);
/** 无状态旧版 UDP 分包。 */
std::vector<uint8_t> packetize_adpcm_legacy(const uint8_t *pcm, size_t size, uint32_t sequence, uint32_t timestamp_samples);
/** 有状态旧版 UDP 分包，初始步进索引由调用方提供。 */
PacketizedAdpcm packetize_adpcm_legacy(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_samples,
    int initial_step_index
);
/** 无状态 v2 UDP 分包。 */
std::vector<uint8_t> packetize_adpcm_v2(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const std::string &device_id,
    const std::string &task_id,
    const std::string &talk_id,
    int stream_type,
    bool is_last_packet
);
/** 有状态 v2 UDP 分包，初始步进索引由调用方提供。 */
PacketizedAdpcm packetize_adpcm_v2(
    const uint8_t *pcm,
    size_t size,
    uint32_t sequence,
    uint32_t timestamp_ms,
    const std::string &device_id,
    const std::string &task_id,
    const std::string &talk_id,
    int stream_type,
    bool is_last_packet,
    int initial_step_index
);
/** 从 PCM16 编码完整 HADP 文件字节。 */
HadpResult encode_hadp(
    const uint8_t *pcm,
    size_t size,
    const std::string &record_id,
    int codec_id,
    int sample_rate,
    int channels,
    int packet_ms
);
/** 将完整 HADP 文件字节解码成 PCM16。 */
std::vector<uint8_t> decode_hadp_pcm16(const uint8_t *data, size_t size);
/** 对 PCM 片段或帧应用实时喊话/按住说话/播放语音处理。 */
std::vector<uint8_t> process_voice(
    const uint8_t *pcm,
    size_t size,
    int profile,
    int sample_rate,
    VoiceToneSettings tone
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
/** 构造 RECORD_DOWNLOAD 命令 JSON 外层结构。 */
std::vector<uint8_t> build_record_download_command_json(
    const std::string &device_id,
    const std::string &msg_id,
    const std::string &record_id,
    const std::string &store_task_id,
    const std::string &created_at,
    const std::string &name,
    const std::string &download_url,
    int64_t file_size,
    const std::string &crc32,
    int duration_ms,
    const std::string &codec,
    int sample_rate,
    int channels,
    int packet_ms,
    int frame_bytes,
    int samples_per_frame,
    bool verify_only,
    const std::string &verify_kind,
    const std::string &expected_audio_crc32,
    const std::string &expected_first_samples_json,
    bool temporary,
    bool visible,
    bool auto_play,
    int playback_volume,
    bool has_playback_volume
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
std::vector<uint8_t> float32_to_pcm16(
    const float *samples,
    size_t sample_count,
    int source_sample_rate,
    int target_sample_rate
);
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

/** HADP 元数据和原生/Kotlin 影子对照使用的 CRC32。 */
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
