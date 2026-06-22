# 喊话器服务器职责

## 状态

- 状态：active
- 产品代码：`speaker`
- productId：`6`

## 临时音频文件传输服务

服务目录：

```text
server/kokoro_tts_service/
```

当前服务职责：

- 接收 App 上传的完整 `.hadp` 文件。
- 接收后续 Qt 上位机上传的完整 `.hadp` 文件。
- 生成短期下载 URL。
- 供 MCU 下载后播放或保存。
- 临时文件默认短期保留，不写数据库。
- 不重新编码、不修改 header、不修补音频区；HADP 文件格式见 [hadp-file-format.md](hadp-file-format.md)。

该服务不再负责 TTS 合成。

## UDP Relay

服务目录：

```text
server/hydrolink_udp_relay/
```

用于喊话器 UDP 4G relay 联调，路由 App UDP 音频包到 MCU 最新 4G endpoint。

## 设备绑定

- 登录返回字段当前模型已有 `megaphonesns`。
- productId 为 `6` 时，App 识别为喊话器。
- 设备 `deviceId` 需与 MQTT topic 和临时文件上传参数一致。
