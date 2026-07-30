# 喊话器服务器职责

## 状态

- 状态：active
- 产品代码：`speaker`
- productId：`6`

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
- 设备 `deviceId` 需与 MQTT topic 和 UDP 媒体传输路由参数一致。
