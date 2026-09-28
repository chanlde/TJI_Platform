# MQTT 量产基线

## 状态

- 状态：active
- 基线日期：2026-08-11

## 部署与安全边界

当前平台 Broker 默认 `129.211.180.25:1883`，无线电兼容 Broker 默认 `47.121.127.205:1883`，都属于公网地址，不按受控局域网处理。

为了不破坏当前已工作的设备链路，本次不擅自把端口改为 8883。`enableTLS` 已接入 HiveMQ transport builder：打开时使用系统信任库和标准主机名校验，关闭时保持当前 TCP 行为。配置项如下：

| Profile | Host/port 配置 | TLS 配置 | 当前默认 |
| --- | --- | --- | --- |
| platform | `TJI_MQTT_BROKER_HOST` / `TJI_MQTT_BROKER_PORT` | `TJI_MQTT_TLS_ENABLED` | `false` |
| radio legacy | `TJI_RADIO_LEGACY_MQTT_HOST` / `TJI_RADIO_LEGACY_MQTT_PORT` | `TJI_RADIO_LEGACY_MQTT_TLS_ENABLED` | `false` |

Broker 配置正式证书和 TLS 端口后，使用 DNS 主机名、对应端口并把开关设为 `true`；不需要改产品 payload。当前公网明文链路属于待服务端配合关闭的已知风险，不用 payload 二次加密、mTLS、硬件密钥或自建 PKI 扩大改动范围。

2026-08-11 只读探测：两个 1883 端口都对无用户名/密码的 MQTT 3.1.1 CONNECT 返回 `CONNACK success (0x20020000)`，说明当前允许匿名连接；两个 8883 端口可建立 TCP，但标准 TLS handshake 与明文 MQTT CONNECT 均在 5 秒内超时。因此本版不能打开 TLS 开关，服务端需先关闭匿名访问并完成真实 TLS listener、DNS、证书和最小 topic ACL，再做联调切换。

## 风险接受

- 接受日期：2026-08-11
- 接受范围：本 App 面向有限客户的嵌入式设备上位机，为保持现有设备业务连通，当前版本继续使用两个公网 1883 Broker。
- 明确风险：公网链路未加密且 Broker 当前允许匿名连接，网络路径上的攻击者可能读取或伪造控制/状态消息。
- 边界：仅接受现状，不删除客户端 TLS 能力，不把风险扩大到 payload 日志、明文密码存储或其他接口；服务端具备条件后仍应切换 TLS 和最小 ACL。
- 产品决定：已明确接受该风险，项目 06 可按风险豁免通过，不再以此阻塞后续客户端优化。

## 生命周期

App 定位是前台使用的嵌入式设备上位机，不承诺退到后台仍长期在线，因此不创建带常驻通知的 Foreground Service。

MQTT 由登录账号会话持有：登录建立连接，HiveMQ 在进程存活时自动重连，登出清订阅并断开。原空壳 `MqttService` 已移除；它没有执行连接/保活，被系统销毁时反而会主动断开全部连接。

## Topic 与订阅基线

所有 lifecycle/status 订阅使用 QoS 1；控制命令不 retain，离线时不排队，避免恢复网络后执行旧控制命令。

| 产品 | lifecycle | status / ACK | control |
| --- | --- | --- | --- |
| FireBucket | `FireBucket/devices/{id}/lifecycle` | `FireBucket/devices/{id}/status` | `FireBucket/devices/{id}/control` |
| SolarClean | `SolarClean/devices/{id}/lifecycle` | `SolarClean/devices/{id}/status` | `SolarClean/devices/{id}/control` |
| DropperSixStage | `FC100_FireDrop/devices/{id}/lifecycle`，兼容 `SixStageDropper/...` | `FC100_FireDrop/devices/{id}/status`，兼容 `SixStageDropper/...` | `FC100_FireDrop/devices/{id}/control` |
| RadioDetection | `RadioDetection/devices/{id}/lifecycle` | RID：`spectrum-detection-client/{id}`；RGB ACK：`RadioDetection/devices/{id}/status` | `RadioDetection/devices/{id}/control` |
| Speaker | `Speaker/devices/{id}/lifecycle` | `Speaker/devices/{id}/status` | `Speaker/devices/{id}/control` |
| GlassBreaker | `GlassBreaker/devices/{id}/lifecycle` | `GlassBreaker/devices/{id}/status` | `GlassBreaker/devices/{id}/control` |

控制发布 QoS 基线：FireBucket 1、SolarClean 0、DropperSixStage 1、RadioDetection 1、Speaker 1、GlassBreaker 0、公共 OTA 1。该差异属于现有设备协议，本次不统一、不修改。

## ACK、重连与过载规则

- SUBACK / UNSUBACK / QoS 1 PUBACK 完成后才向调用方报告成功。
- clean-session 重连后按 desired targets 恢复订阅；topic、QoS 和 payload 保持不变。
- ACK/lifecycle 使用可靠优先队列，普通 telemetry 使用独立有界队列；每台设备每类最多 256 条，过载保留较新状态。
- 会话 reset 先使 generation 失效，再 cancel 并 join 已开始的 handler，之后才允许上层清产品仓库，旧账号消息不能在清理完成后写回。
- 单条入站 payload 最大 1 MiB；超过上限直接拒绝，合法 JSON、RID 和 OTA payload 不受影响。
