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

- 手机麦克风按所选低/中/高音质采集；松手喊话统一生成 48 kHz Opus，
  通过可靠 UDP 分块直传播放。
- MCU 板载 PDM 麦克风实时回传到手机监听（独立于手机录音）。
- Android 系统 TTS。
- Ogg/Opus 文件编码和上传。
- 录音库分页、保存、删除、改名和播放。
- 音量、音质、音色设置。
- 客户界面隐藏底层包数和调试入口。

## 两条麦克风链路

手机录音和 MCU 麦克风监听是两个方向相反、格式不同的功能，不能混用：

| 功能 | 音频源 | 采样率 | 传输 |
|---|---|---:|---|
| 手机松手喊话 | Android `AudioRecord` | 低 8 kHz / 中 16 kHz / 高 48 kHz；发送统一 48 kHz | raw Opus UDP，ACK/重传/窗口背压 |
| 手机保存录音 | Android `AudioRecord` | 低 8 kHz / 中 16 kHz / 高 48 kHz | Ogg/Opus 文件上传，MCU 下载保存 |
| 设备麦克风监听 | MCU PDM 麦克风 | 固定 16 kHz | MCU → relay → App 实时 UDP |

手机录音的音质选择从 `AudioRecord` 源头开始生效。保存文件保持所选采样率；
直接喊话在编码前转换到协议固定的 48 kHz，不能只修改元数据伪装采样率。

设备麦克风监听不申请手机录音权限，不生成 Ogg/Opus 文件。App 使用同一
UDP socket 注册和接收，经过抖动缓冲后将 16 kHz 音频写入流式
`AudioTrack`。

## Ogg/Opus 文件协议

App 生成 `.opus` 必须遵守 [ogg-opus-profile.md](ogg-opus-profile.md)。
编码主路径通过 `SpeakerCoreAudioEngine` 调用 native `speaker-core`，不再
保留旧私有文件编码 fallback。

## 测试

- `SpeakerTalkSectionTest` 锁住客户可见录音、发送和保存状态文案。
- `SpeakerMicrophoneFormatTest` 锁住手机低/中/高音质的实际采样率和编码。
- `SpeakerFeedbackProtocolTest` 覆盖 MCU 回传解析、乱序/丢包补偿、UDP
  注册续租和注销。
- `SpeakerDirectPttProtocolTest` 使用与 MCU/Python relay 相同的字节级
  golden vector，锁定直接喊话包格式。
- `SpeakerMcuMicrophoneControllerTest` 覆盖 cmd=116 开启、续租、异常和
  关闭清理。
