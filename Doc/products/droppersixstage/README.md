# 六段抛投产品线

## 产品定位

六段抛投用于无人机挂载的 6 路抛投控制。App 侧先完成产品入口、设备详情页和 6 路开关控制 UI，协议字段后续以 MCU 和服务端最终协议为准。

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
DropperSixStage/devices/{deviceSn}/lifecycle
DropperSixStage/devices/{deviceSn}/status
DropperSixStage/devices/{deviceSn}/control
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
