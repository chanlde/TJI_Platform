# 喊话器协议说明

## 状态

- 状态：active
- 产品代码：`speaker`

## MQTT Topic

当前 topic 以 `SpeakerMqttTopics.kt` 为准：

```text
Speaker/devices/{deviceId}/control
Speaker/devices/{deviceId}/status
Speaker/devices/{deviceId}/lifecycle
```

## 命令类型

App 当前覆盖：

- 状态查询。
- 文字喊话准备和播放。
- 文件播放。
- 音量设置。
- 音质设置。
- 舵机角度设置。
- 录音保存。
- 录音列表查询。
- 录音播放、删除、改名。
- 存储状态查询。

## HADP 文件

- App 生成或录制音频后封装为 `.hadp`。
- Qt 上位机后续也必须生成同一格式的 `.hadp`，优先复用 `native/speaker-core`。
- 临时上传服务返回下载 URL。
- MCU 通过 URL 下载后播放或保存。
- 具体字节布局、codec、CRC 和四端职责见 [hadp-file-format.md](hadp-file-format.md)。

## ACK 和事件

- ACK 解析由 `SpeakerMqttInbound` 负责。
- 录音列表、存储状态、录音事件都进入 `SpeakerRepository`。
- 客户界面只展示可理解状态，不展示底层包数或原始 payload。
