# 光伏清洗协议说明

## 状态

- 状态：active
- 产品代码：`solarclean`

## Topic 规则

平台三主题：

```text
SolarClean/devices/{deviceId}/control
SolarClean/devices/{deviceId}/status
SolarClean/devices/{deviceId}/lifecycle
```

详细 App 侧协议落地见：

```text
Doc/protocols/solar-clean-mqtt-protocol.md
```

## 当前启用命令码

| cmd | cmdName | 说明 |
|---:|---------|------|
| 0 | PING | MQTT 连通性诊断 |
| 1 | GET_DEVICE_INFO | 查询设备信息 |
| 2 | SET_PUMP | 水泵开关 |
| 3 | SET_PUMP_PRESSURE | 水泵压力 |
| 4 | SET_SPRAY_ANGLE | 喷洒角度 |
| 5 | SET_SWING_SPEED | 摆动速度 |
| 6 | SET_SERVO_SWING | 摆动开关 |
| 20 | START_OTA | 开始 OTA |

## 架构约束

- 控制只发布到 `control`。
- 周期状态和 ACK 走 `status`。
- 上下线和异步事件走 `lifecycle`。
- 航线、槽位、KMZ 下载、航线执行当前不做。
