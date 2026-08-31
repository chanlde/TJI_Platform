# 破窗弹协议说明

## 状态

- 状态：active
- 产品代码：`glassbreaker`

## Topic 规则

```text
GlassBreaker/devices/{deviceId}/control
GlassBreaker/devices/{deviceId}/status
GlassBreaker/devices/{deviceId}/lifecycle
```

实际 topic 以 `GlassBreakerMqttTopics.kt` 为准。

## App 当前发送命令

| cmd | cmdName | 说明 | 参数 |
|---:|---|---|---|
| 1 | `GET_DEVICE_INFO` | 查询设备信息 | 无 |
| 10 | `UNLOCK` | 解锁 | 无 |
| 11 | `LOCK` | 上锁 | 无 |
| 12 | `FIRE_CHANNEL` | 击发通道 | `channel`，1～4 |
| 13 | `LASER_SWITCH` | 激光开关 | `on` |
| 14 | `SELECT_CHANNEL` | 选择通道 | `channel`，1～4 |

命令包含 `v`、`msgId`、`cmdId`、`deviceId`、`ts`、`cmd`、`cmdName` 和 `params`。字段和值以现有 payload 单元测试为准，文档调整不得改变它们。

## 入站事件

- `online` / `offline`：更新 lifecycle 在线状态。
- `state` / `status`：更新锁、通道、激光、击发、电量和版本状态。
- `ack`：关联普通控制命令结果。
- `otaAck`：关联 OTA 命令结果。

Retained 状态只恢复遥测，不单独证明设备在线；实时 lifecycle 是在线状态依据。

## ACK 规则

- App 使用 `msgId` 关联待处理命令。
- 普通命令等待时间为 3 秒。
- ACK 可携带锁、选择通道、激光和击发状态。
- 失败信息会转换为客户可理解的中文提示。
