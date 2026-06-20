# SixStageDropper 协议说明

## 状态

- 状态：draft
- 产品代码：`droppersixstage`
- ProductCode：`SixStageDropper`
- 旧称 / 兼容别名：`DropperSixStage`、`FC100_FireDrop`

## Topic 规则

沿用平台三主题：

```text
SixStageDropper/devices/{deviceId}/lifecycle
SixStageDropper/devices/{deviceId}/status
SixStageDropper/devices/{deviceId}/control
```

## 控制 Payload

协议未定稿，App 当前使用临时字段便于 UI 和 MQTT 链路联调：

```json
{
  "v": 1,
  "msgId": "stage-1-1710000000000",
  "ts": 1710000000000,
  "cmd": 10,
  "cmdName": "SET_STAGE_SWITCH",
  "stage": 1,
  "open": true
}
```

## 命令码

| cmd | cmdName | 说明 | ACK |
|---:|---------|------|-----|
| 0 | PING | 连通测试 | 是 |
| 10 | SET_STAGE_SWITCH | 单段开关 | 是 |
| 11 | SET_ALL_STAGES | 全部开关 | 是 |

## 状态 Payload

```json
{
  "type": "state",
  "ts": 1710000000000,
  "battery": 86,
  "firmware_version": "1.0.0",
  "stages": [
    { "stage": 1, "open": false, "loaded": true },
    { "stage": 2, "open": true, "loaded": true }
  ]
}
```

## ACK Payload

```json
{
  "type": "ack",
  "msgId": "stage-1-1710000000000",
  "ok": true,
  "stage": 1
}
```

## 待确认

- MCU 最终命令码和字段名。
- 失败 ACK 是否需要错误码和客户可见错误文案。
- retained state 是否由设备或服务端保留。
- `msgId`、`seq` 或 `requestId` 的最终命名。
