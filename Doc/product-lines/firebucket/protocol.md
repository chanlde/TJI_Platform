# 消防吊桶协议说明

## 状态

- 状态：active
- 产品代码：`firebucket`

## Topic 规则

消防吊桶沿用产品私有 topic 布局，实际 topic 以 `FireBucketMqttTopics.kt` 为准。

代码落点：

```text
app/src/main/java/com/tji/device/product/firebucket/mqtt/FireBucketMqttTopics.kt
app/src/main/java/com/tji/device/product/firebucket/mqtt/FireBucketMqttInbound.kt
```

## Payload 语义

- Link 设备和桶设备运行时状态放在消防吊桶私有模型内。
- 现网旧版 Link 只持续发送 `LinkDeviceHeartbeat`，桶的 `isOnline` 位来自
  `LinkDeviceStartup.subDevices`。App 收到 retained 启动快照时先保持 Link/桶离线；
  收到同一 Link 的新鲜在线心跳后，再按快照中最后上报的桶状态恢复可用性。
- Link 离线或心跳超时时，其下所有桶立即按离线处理；后续新鲜在线心跳可恢复最后一次
  上报为在线的桶。实时 `SubDeviceStatusChanged` 优先于 retained 快照。
- 控制命令由 `FireBucketSwitchRepository` / `SwitchRepo` 负责发布。
- 入站 MQTT 由 `FireBucketMqttInbound` 解析，不进入通用 MQTT handler 之外的 UI 层。

## ACK 规则

- App 需要展示控制成功、失败、超时反馈。
- `SET_SERVO ACK status=0` 只表示 H618 已接受并发布控制命令，不表示吊桶已经转到目标位置。
- 数传模式保持 App 到 ESP32 的 TCP 连接；同一连接同时接收 ACK 和 H618 主动发送的
  `STATUS_REPORT`。实际角度到达目标值后，App 才能据此确认动作结果。
- 后续如调整字段名，必须同步更新 `FireBucketMqttInbound` 和单元测试。

## 数传状态回传

生产链路为：

```text
App --Wi-Fi TCP--> ESP32 --UART--> 地面数传 ~~空口~~ 天空数传 --UART--> H618
App <--Wi-Fi TCP-- ESP32 <--UART-- 地面数传 ~~空口~~ 天空数传 <--UART-- H618
```

ESP32 和两端数传只透明转发 CoreFrame 字节。App 连接的是 ESP32 TCP Server 端口；该端口号不写入
协议帧，但必须与 ESP32 配置一致。H618 收到旧吊桶 `SwitchDeviceInfo` 后，以
`DevType=0x04, Cmd=0x03, Flags=EVENT` 回传 28～36 字节状态：

```text
sn(8～16 位大写字母或数字), online, currentAngle(0.1°), currentCurrent(0.1mA),
inputVoltage(0.01V), batteryPercentage(0.1%), servoMinAngle(0.1°),
servoMaxAngle(0.1°), uptimeSeconds
```

固定黄金帧继续使用 8 位兼容样例（`FB00A123`、在线、90.1°、218.5mA、24.20V、87.0%、0..360°、123s、Seq 0x22）；真实 HydroSwitch V2 可上报 16 位 SN：

```text
54 4A 01 04 03 02 00 22 00 1C 08 46 42 30 30 41 31 32 33 01 03 85 00 00 08 89 09 74 03 66 00 00 0E 10 00 00 00 7B 59 2B
```

## 架构约束

- 消防吊桶的 Link / Switch 不能作为新产品通用模型。
- 其他产品不能写入 `FireBucketLinkRepository`。
- 首页只消费产品运行时快照，不读取消防吊桶私有 payload。
