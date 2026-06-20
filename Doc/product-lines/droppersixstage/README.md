# SixStageDropper 产品线

## 状态

- 状态：active
- 产品代码：`droppersixstage`
- ProductCode：`SixStageDropper`
- 旧称 / 兼容别名：`DropperSixStage`、`FC100_FireDrop`
- productId：`5`

## 产品定位

SixStageDropper 用于无人机挂载的 6 路抛投控制。App 侧先完成产品入口、设备详情页和 6 路开关控制 UI，协议字段后续以 MCU 和服务端最终协议为准。

## 当前 App 控制项

- 1-6 段独立抛投开关。
- 全部抛投。
- 全部复位。
- 在线 / 离线状态。
- 已抛投数量、电量、载荷检测状态展示。
- ACK 成功、失败、超时反馈。

## 暂定 MQTT Topic

沿用平台统一 topic 布局：

```text
SixStageDropper/devices/{deviceId}/lifecycle
SixStageDropper/devices/{deviceId}/status
SixStageDropper/devices/{deviceId}/control
```

## 暂定控制 Payload

协议未定稿，App 侧当前只封装临时字段，便于 UI 联调：

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

命令码暂定：

| cmd | cmdName | 说明 |
| --- | --- | --- |
| 0 | PING | 连通测试 |
| 10 | SET_STAGE_SWITCH | 单段开关 |
| 11 | SET_ALL_STAGES | 全部开关 |

## 暂定状态 Payload

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

## 暂定 ACK Payload

```json
{
  "type": "ack",
  "msgId": "stage-1-1710000000000",
  "ok": true,
  "stage": 1
}
```

## 后续待定

- MCU 最终命令码和字段名。
- 抛投后是否允许 App 复位为待命。
- 是否需要保险/二次确认。
- 载荷检测字段是否由 MCU 上报。
- 是否需要 OTA、设备信息页和错误码表。

## 分文档索引

- [protocol.md](protocol.md)：MQTT topic、临时 payload、ACK 和待确认协议项。
- [mcu.md](mcu.md)：MCU 安全约束、状态上报和联调清单。
- [server.md](server.md)：绑定字段、版本接口和服务端待办。
- [app.md](app.md)：App 代码落点、UI、悬浮窗和测试。
