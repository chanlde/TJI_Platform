# 消防吊桶产品线

## 状态

- 状态：active
- 产品代码：`firebucket`
- 后台产品：`FireBucket`
- productId：`2`

## 产品定位

消防吊桶是平台最早接入的产品线，保留 Link / 桶控制逻辑。一个账号可有多个 Link，一个 Link 下可挂多个桶；App 侧负责设备列表、控制台、悬浮窗快捷控制和 MQTT 控制反馈。

## 当前 App 能力

- 消防吊桶产品入口和设备列表。
- Link 和子桶运行时状态展示。
- 桶开关控制。
- 悬浮窗快捷控制。
- 在线 / 离线状态。
- ACK 成功、失败、超时反馈。

## 当前边界

本阶段继续保留历史能力，不把消防吊桶模型扩散到其他产品线。

本阶段不做：

- 将消防吊桶 `Switch` 抽成所有产品共用模型。
- 在首页直接解析消防吊桶私有 payload。
- 在悬浮窗重复订阅 MQTT。

## 分文档索引

- [protocol.md](protocol.md)：MQTT topic、payload 和 ACK 约束。
- [mcu.md](mcu.md)：Link / 桶设备侧职责。
- [server.md](server.md)：绑定、登录字段和服务端待办。
- [app.md](app.md)：App 代码落点、UI、悬浮窗和测试。
