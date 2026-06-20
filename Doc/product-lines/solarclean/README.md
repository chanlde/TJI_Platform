# 光伏清洗产品线

## 状态

- 状态：active
- 产品代码：`solarclean`
- 后台产品：`SolarClean`
- productId：`3`

## 产品定位

光伏清洗用于无人机光伏清洗设备控制。App 侧已接入 MQTT 状态、控制、设备设置、悬浮窗快捷控制和 OTA 入口。

## 当前 App 能力

- 光伏清洗产品入口和设备列表。
- 控制页：水泵、压力、喷洒角度、摆动速度、摆动开关。
- 飞行 / 清洗状态展示。
- 悬浮窗快捷控制。
- 设备信息和 OTA 入口。
- ACK 成功、失败、超时反馈。

## 当前边界

本阶段不做航线、槽位、KMZ 下载和航线执行。

## 分文档索引

- [protocol.md](protocol.md)：MQTT topic、cmd、status、lifecycle 和 ACK。
- [mcu.md](mcu.md)：设备侧控制、安全和 OTA 职责。
- [server.md](server.md)：latest 版本接口、绑定字段和服务端待办。
- [app.md](app.md)：App 代码落点、UI、悬浮窗和测试。
