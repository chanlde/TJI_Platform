# Speaker HADP 音频文件协议

## 状态

- 状态：active
- 产品代码：`speaker`
- 协议版本：`HADP v1`
- 适用端：Android App、Qt 上位机、Server、MCU

## 定位

HADP 是喊话器产品线的私有音频容器格式，用于 App 或 Qt 生成音频文件、Server 临时存储和下发、MCU 下载后播放或保存。

HADP 不是通用音频格式，不替代 WAV、MP3 或 AAC。它只承载喊话器设备需要的最小元数据、音频帧和校验信息。

```text
file = 128-byte HADP header + audio payload
```

## 四端职责

| 端 | 职责 |
|----|------|
| Android App | 录音、TTS、音频处理、HADP 编码、上传 Server、发送 MQTT 命令 |
| Qt 上位机 | 复用 `speaker-core` 生成 HADP、上传 Server、发送 MQTT/控制命令 |
| Server | 接收完整 `.hadp` 文件，保存临时文件，返回下载 URL，不修改 HADP 内容 |
| MCU | 按 MQTT 命令下载 `.hadp`，校验文件，解析 header，按 codec 播放或保存 |

App 和 Qt 不应各自重写 HADP 逻辑，优先复用 `native/speaker-core`。Server 不应重新编码或修补 HADP。MCU 不应把 raw ADPCM 当正式 HADP 保存。

## 字节序和基础规则

- Header 固定 128 字节。
- 所有整数为 little-endian。
- 字符串使用 UTF-8。
- `recordId` 最多占 64 字节，超出部分截断，未用部分填 0。
- `fileSize = 128 + audioBytes`。
- `durationMs = frameCount * packetMs`。
- 末帧由编码端补 0 到完整帧，解码端不需要猜末帧长度。

## Header 固定格式

| Offset | Size | Field | 说明 |
|--------|------|-------|------|
| 0 | 4 | magic | 固定 ASCII `HADP` |
| 4 | 2 | version | 当前固定 `1` |
| 6 | 2 | headerLen | 当前固定 `128` |
| 8 | 2 | codec | `1=IMA ADPCM`，`2=PCM16` |
| 10 | 2 | flags | 当前固定 `0`，预留 |
| 12 | 4 | sampleRate | 采样率，单位 Hz |
| 16 | 2 | channels | 当前固定 `1` |
| 18 | 2 | packetMs | 每帧逻辑时长，当前常用 `40` |
| 20 | 2 | frameBytes | 每帧音频负载字节数 |
| 22 | 2 | samplesPerFrame | 每帧 PCM 采样数 |
| 24 | 4 | frameCount | 音频帧数量 |
| 28 | 4 | audioBytes | header 后音频区总字节数 |
| 32 | 4 | durationMs | 音频总时长，单位 ms |
| 36 | 4 | audioCrc32 | 只对音频区计算 CRC32 |
| 40 | 4 | reserved | 固定 `0` |
| 44 | 64 | recordId | UTF-8，0 结尾/0 填充 |
| 108 | 20 | reserved | 固定 `0` |

## Codec

### IMA ADPCM

| 字段 | 固定值 |
|------|--------|
| codec | `1` |
| sampleRate | `8000` |
| channels | `1` |
| packetMs | `40` |
| frameBytes | `164` |
| samplesPerFrame | `320` |

音频区由 `frameCount` 个 IMA ADPCM 帧组成。每帧 164 字节，对应 320 个 PCM16 采样。ADPCM 连续帧需要保留 IMA step index，不能每帧随意重置，除非协议双方明确切换为重置模式。

### PCM16

| 字段 | 低 | 中 | 高 |
|------|----|----|----|
| codec | `2` | `2` | `2` |
| sampleRate | `8000` | `16000` | `24000` |
| channels | `1` | `1` | `1` |
| packetMs | `40` | `40` | `40` |
| frameBytes | `640` | `1280` | `1920` |
| samplesPerFrame | `320` | `640` | `960` |

PCM16 payload 是 signed 16-bit little-endian mono PCM。MCU 播放时必须按 header 的 `sampleRate/frameBytes/samplesPerFrame` 切换播放参数，不能写死 8 kHz。

## CRC 规则

| 名称 | 范围 | 用途 |
|------|------|------|
| `audioCrc32` | 只对 128 字节 header 后面的音频区计算 | MCU 播放前二次校验 |
| MQTT/上传返回 `crc32` | 对完整 `.hadp` 文件计算 | Server 下载完整性校验 |

CRC32 字符串展示格式统一为 `0xAABBCCDD`。Header 内 `audioCrc32` 存 4 字节 little-endian 数值。

## MQTT 元数据一致性

`RECORD_DOWNLOAD` 命令中的音频元数据必须和 HADP header 一致：

```json
{
  "recordId": "REC_xxx",
  "downloadUrl": "http://...",
  "fileSize": 4228,
  "crc32": "0x1234ABCD",
  "durationMs": 1000,
  "codec": "pcm16",
  "sampleRate": 8000,
  "channels": 1,
  "packetMs": 40,
  "frameBytes": 640,
  "samplesPerFrame": 320
}
```

MCU 下载完成后必须校验：

- `magic == "HADP"`。
- `version == 1`。
- `headerLen == 128`。
- `fileSize == 128 + audioBytes`。
- MQTT `fileSize/crc32` 和实际文件一致。
- MQTT `codec/sampleRate/channels/packetMs/frameBytes/samplesPerFrame/durationMs` 和 header 一致。

## 兼容和拒绝规则

- 正式保存和播放只接受完整 HADP 文件。
- 历史 raw ADPCM 文件只能作为 MCU 旧数据兼容，不允许 App、Qt 或 Server 新生成。
- 不支持的 codec 必须拒绝并上报明确错误。
- 如果 MCU 当前播放链路不支持 16 kHz 或 24 kHz PCM16，应 ACK 失败或上报 `record_failed`，不能按 8 kHz 播放导致变速。
- Server 不应根据扩展名判断文件合法，必须至少保留文件大小和完整文件 CRC。

## 代码落点

| 端 | 代码 |
|----|------|
| C++ core | `native/speaker-core/src/hadp_codec.cpp` |
| C ABI | `native/speaker-core/include/tji_speaker_core.h` |
| Android JNI | `app/src/main/cpp/speaker_core_jni.cpp` |
| Android Kotlin | `SpeakerCoreAudioEngine.encodeHadp()` / `SpeakerHadpEncoder` fallback |
| Qt | 后续直接链接 `native/speaker-core`，不要重新实现 HADP |
| MCU | `hadp_file.c/.h` 或同等模块 |
| Server | 临时文件上传/下载服务只保存完整 `.hadp` |

## 验收样例

1 秒 8 kHz ADPCM：

```text
codec=1
sampleRate=8000
packetMs=40
frameBytes=164
samplesPerFrame=320
frameCount=25
audioBytes=4100
fileSize=4228
durationMs=1000
```

1 秒 8 kHz PCM16：

```text
codec=2
sampleRate=8000
packetMs=40
frameBytes=640
samplesPerFrame=320
frameCount=25
audioBytes=16000
fileSize=16128
durationMs=1000
```

## 变更纪律

- HADP 字段、偏移、codec 编号或 CRC 范围发生变化时，必须先更新本文档。
- App、Qt、Server、MCU 四端必须同步确认后才能发版。
- 新版本若不兼容 v1，必须提升 `version`，并保留 v1 解析能力直到存量文件迁移完成。
