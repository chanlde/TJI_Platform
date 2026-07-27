# 喊话器 App 落地

## 状态

- 状态：active
- 产品代码：`speaker`

## 代码落点

```text
app/src/main/java/com/tji/device/product/speaker/
  audio/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- 手机麦克风按所选低/中/高音质采集，松手后生成 `.hadp` 上传播放。
- MCU 板载 PDM 麦克风实时回传到手机监听（独立于手机录音）。
- Android 系统 TTS。
- ADPCM packetizer 和 HADP encoder。
- `.hadp` 临时上传。
- 录音库分页、保存、删除、改名和播放。
- 音量、音质、音色设置。
- 客户界面隐藏底层包数和调试入口。

## 两条麦克风链路

手机录音和 MCU 麦克风监听是两个方向相反、格式不同的功能，不能混用：

| 功能 | 音频源 | 采样率 | 传输 |
|---|---|---:|---|
| 手机按住录音/保存 | Android `AudioRecord` | 低 8 kHz / 中 16 kHz / 高 24 kHz | HADP 文件上传，MCU 下载 |
| 设备麦克风监听 | MCU PDM 麦克风 | 固定 16 kHz | MCU → relay → App 实时 UDP |

手机录音的音质选择从 `AudioRecord` 源头开始生效，并贯穿处理和 HADP
编码；禁止先按 8 kHz 采集再伪装成 16/24 kHz。低音质使用 MCU 当前支持的
8 kHz IMA ADPCM；中、高音质分别使用 16/24 kHz PCM16。

设备麦克风监听不申请手机录音权限，不生成 WAV 或 HADP 文件。App 使用同一
UDP socket 注册和接收，经过抖动缓冲后将 16 kHz 音频写入流式
`AudioTrack`。

## HADP 协议

App 生成 `.hadp` 必须遵守 [hadp-file-format.md](hadp-file-format.md)。当前主路径通过 `SpeakerCoreAudioEngine` 调用 native `speaker-core`，Kotlin 实现只作为 fallback 和影子对照。

后续 Qt 上位机接入时，应复用同一套 `native/speaker-core`，不要按 Kotlin 或 UI 代码重新实现一份 HADP 编码。

## 测试

- `SpeakerTalkSectionTest` 锁住客户可见录音、发送和保存状态文案。
- `SpeakerMicrophoneFormatTest` 锁住手机低/中/高音质的实际采样率和编码。
- `SpeakerFeedbackProtocolTest` 覆盖 MCU 回传解析、乱序/丢包补偿、UDP
  注册续租和注销。
- `SpeakerMcuMicrophoneControllerTest` 覆盖 cmd=116 开启、续租、异常和
  关闭清理。
