# 喊话器产品线

## 状态

- 状态：active
- 产品代码：`speaker`
- 后台产品：`Speaker`
- productId：`6`

## 产品定位

喊话器用于无人机实时喊话、录音管理、文字转语音和音量/音色控制。App 当前已收口云端 TTS，文字转语音由本地或系统生成音频，再通过 `.hadp` 临时文件上传下载链路给 MCU 播放或保存。

## 当前 App 能力

- 实时喊话。
- 录音保存、播放、删除、改名。
- 文字转语音。
- 音量、音质、音色调节。
- 存储状态展示。
- 临时 `.hadp` 上传下载链路。

## 当前边界

- App 不再依赖云端 Kokoro TTS。
- 客户界面不展示底层包数、原始 ACK 或调试信息。
- WebView 不承载喊话器主控制 UI。

## 分文档索引

- [protocol.md](protocol.md)：MQTT、HADP、录音和 ACK 规则。
- [mcu.md](mcu.md)：播放、保存、录音列表和存储状态职责。
- [server.md](server.md)：临时音频文件传输服务职责。
- [app.md](app.md)：App 音频链路、UI、测试和本地模型资源。
