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

- 接收 App 上传的完整 `.opus`（Ogg/Opus）文件。
- 生成短期下载 URL。
- 供 MCU 下载后播放或保存。
- 临时文件默认短期保留，不写数据库。
- 不重新编码、不修改 Ogg 页面或 Opus 包；受控格式见
  [ogg-opus-profile.md](ogg-opus-profile.md)。

该服务不再负责 TTS 合成。

## UDP Relay

服务目录：

```text
server/hydrolink_udp_relay/
```

用于喊话器 UDP 4G relay，路由 App 直接喊话到 MCU 最新 4G endpoint，并将
MCU 麦克风回传/直接喊话 ACK 路由给 App。App 必须先使用共享 token 从发送
socket 注册完全相同的 `deviceId/sessionId/talkId`；中继拒绝未注册、过期或
来源地址不一致的下行包。生产 token 只从构建配置/服务环境注入，不写入源码。

## 设备绑定

- 登录返回字段当前模型已有 `megaphonesns`。
- productId 为 `6` 时，App 识别为喊话器。
- 设备 `deviceId` 需与 MQTT topic 和临时文件上传参数一致。
