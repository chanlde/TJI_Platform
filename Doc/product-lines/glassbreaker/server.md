# 破窗弹服务器职责

## 状态

- 状态：active
- 产品代码：`glassbreaker`
- productId：`7`

## 登录与设备绑定

- 登录结果以 productId `7` 或产品代码 `GlassBreaker` 标识破窗弹。
- 服务端返回的设备序列号必须与 MQTT topic 中的 `deviceId` 一致。
- 一个账号存在多个设备时，每台设备独立展示和订阅。

## MQTT / Broker

- 允许 App 订阅当前账号绑定设备的 status 和 lifecycle。
- 允许 App 向当前账号绑定设备的 control topic 发布。
- 不应允许账号访问未绑定设备的 topic。
- retained status 用于恢复最近遥测，lifecycle 仍负责实时在线状态。

## OTA / 版本

- 当前 App 能接收 `otaAck`，具体固件发布规则沿用平台 OTA 文档。
- 服务端提供的产品、硬件版本和固件信息必须与目标设备匹配。

## 上线检查

- 登录账号能返回破窗弹设备。
- productId、产品代码和设备序列号与 App 映射一致。
- 控制 ACK 能回到发起命令的同一设备。
