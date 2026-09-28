# SixStageDropper 协议说明

## 状态

- 状态：draft
- 产品代码：`droppersixstage`
- 设备 ProductCode：`FC100_FireDrop`
- App 兼容别名：`SixStageDropper`、`DropperSixStage`

## Topic 规则

沿用平台三主题：

```text
FC100_FireDrop/devices/{deviceId}/lifecycle
FC100_FireDrop/devices/{deviceId}/status
FC100_FireDrop/devices/{deviceId}/control
```

App 入站同时订阅旧的 `SixStageDropper/devices/{deviceId}/...` lifecycle/status，
控制指令统一发送到设备实际使用的 `FC100_FireDrop` 前缀。

设备可使用 identity 作为上线帧；其中显式的 `online` 字段是生命周期真值：

```json
{"type":"identity","deviceId":"D29D5405F","product":"FC100_FireDrop","fw":"1.7.42.114","online":true}
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
