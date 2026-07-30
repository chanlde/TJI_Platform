# Speaker Ogg/Opus 文件配置

## 范围

按住录音松手、Android 系统 TTS 和录音保存统一生成标准 Ogg/Opus 文件。
旧私有文件容器和解码器不再属于正式链路。

## 受控参数

| 字段 | 值 |
|---|---|
| 容器 | Ogg |
| 编码 | Opus |
| 扩展名 | `.opus` |
| 声道 | 单声道 |
| 输入采样率 | 8 / 16 / 24 kHz |
| Opus 包时长 | 20 ms |
| Ogg 分页 | 每页一个完整 Opus 包 |
| MIME | `audio/ogg` |

每页一个包是本产品当前生成器和 MCU 增量解析器共同遵守的受控子集，
文件本身仍是标准 Ogg/Opus。

## 端到端职责

- App：采集或合成 PCM16，通过官方 libopus 编码，再通过可靠 UDP 媒体协议发送完整 Ogg/Opus 字节流。
- UDP Relay：只路由媒体数据包和确认包，不保存、不转码音频。
- MCU：按序接收并校验传输块，同时校验 Ogg CRC、解码 Opus 并把 PCM16 送入播放环形缓冲；持久化模式同时保存压缩文件。

媒体传输起始块携带用途、采样率、`channels=1`、`packetMs=20`、
`fileSize`、`durationMs` 和完整文件 CRC32。MCU 还校验 Ogg 页序号、
流序列号、OpusHead/OpusTags、EOS granule、包数和解码时长。

PCM16 只是编码前和解码后的内部音频表示，不是第二套文件格式。设备麦克风
实时回传走独立 UDP 协议，不使用本文件链路。
