# 喊话器产品线

## 状态

- 状态：active
- 产品代码：`speaker`
- 后台产品：`Speaker`
- productId：`6`

## 产品定位

喊话器用于无人机按住录音后播放、录音管理、文字转语音和音量/音色控制。
松手喊话使用 48 kHz raw Opus 可靠 UDP 直传；Android 系统 TTS、保存录音和
非实时文件播放使用标准 `.opus`（Ogg/Opus）临时文件链路。

## 当前 App 能力

- 按住录音，松手后通过 ACK/重传的 Opus UDP 分块边传边播。
- 录音保存、播放、删除、改名。
- 文字转语音。
- 音量、音质、音色调节。
- 存储状态展示。
- TTS、保存录音和非实时播放的临时 `.opus` 上传下载链路。
- MCU 板载麦克风实时监听（16 kHz UDP 回传，与手机录音独立）。

## 当前边界

- App 不再依赖云端 Kokoro TTS。
- 客户界面不展示底层包数、原始 ACK 或调试信息。
- WebView 不承载喊话器主控制 UI。

## 分文档索引

- [protocol.md](protocol.md)：MQTT、Ogg/Opus、录音和 ACK 规则。
- [ogg-opus-profile.md](ogg-opus-profile.md)：App / Server / MCU 共同遵守的 Ogg/Opus 受控参数。
- [mcu.md](mcu.md)：播放、保存、录音列表和存储状态职责。
- [server.md](server.md)：UDP relay 路由与设备绑定职责。
- [app.md](app.md)：App 音频链路、UI、测试和本地模型资源。
