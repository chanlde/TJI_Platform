# 光伏清洗 App 侧协议落地说明

## 状态

- 状态：active
- 说明：当前 App 采用的光伏清洗 MQTT topic 和 payload 口径，协议变更需同步产品线文档。

本文记录 App 当前采用的光伏清洗 MQTT 落地口径。Notion 中的光伏清洗文档用于参考字段和控制语义，但 topic 命名以本项目为准。

## Topic 规则

光伏清洗与消防吊桶保持同一类平台布局，只改产品名前缀：

```text
SolarClean/devices/{deviceId}/lifecycle
SolarClean/devices/{deviceId}/status
SolarClean/devices/{deviceId}/control
```

历史固件文档里的四主题：

```text
solarclean/{deviceId}/cmd
solarclean/{deviceId}/ack
solarclean/{deviceId}/state
solarclean/{deviceId}/event
```

已经在 Notion 中修订为平台三主题。App 与文档现在统一：**控制只发布到 `control`，状态/应答走 `status`，异步事件走 `lifecycle`**。

## Payload 语义

字段语义参考 Notion 光伏清洗文档：

- `control` 发布命令：统一使用数字 `cmd` 命令码；`cmdName` 只作为调试字段，单片机不要依赖它做业务判断。
- `status` 接收 `type=ack`：命令立即应答，包含 `msgId`、`ofType`、`ok`、`code`、`msg`、`data`。
- `status` 接收 `type=state`：周期状态，包含位置、姿态、电量、水位、下载状态等。
- `lifecycle` 接收 `downloadProgress` / `downloadDone` / `downloadError`：航线下载事件。
- `lifecycle` 接收 `routeExecuteStarted` / `routeExecuteFinished`：航线执行事件。

App 当前下发命令码：

| cmd | cmdName | 参数 | 说明 |
|---:|---|---|---|
| 0 | PING | 无 | 诊断 |
| 1 | GET_DEVICE_INFO | 无 | 查询设备硬件/固件信息 |
| 2 | SET_PUMP | `on` | 水泵开关 |
| 3 | SET_PUMP_PRESSURE | `percent` | 水泵压力，0-100 |
| 4 | SET_SPRAY_ANGLE | `amplitudeDeg` | 喷洒单边摆幅角，0-40 |
| 5 | SET_SWING_SPEED | `speedPercent` | 摆动速度，0-100 |
| 6 | SET_SERVO_SWING | `on`，可选 `speedPercent`、`amplitude` | 摆动开关 |
| 20 | START_OTA | 必填 `target_version`、`target_inner_version`、`download_url`、`file_size`、`sha256`；可选 `hardware_version`、`signature` | 开始 OTA |
| 30 | ROUTE_LIST | 无 | 查询航线槽位，当前 App 暂不使用 |
| 31 | ROUTE_DELETE | `slot` | 删除航线槽位，当前 App 暂不使用 |
| 32 | ROUTE_DOWNLOAD | `slot`、`url`、`size`，可选 `checksum` | 下载航线，当前 App 暂不使用 |
| 33 | ROUTE_DOWNLOAD_CANCEL | 可选 `slot` | 取消航线下载，当前 App 暂不使用 |
| 34 | EXECUTE_SLOT | `slot` | 执行航线槽位，当前 App 暂不使用 |

示例：

```json
{
  "v": 1,
  "msgId": "device-info-1777617643409",
  "ts": 1777617643410,
  "cmd": 1,
  "cmdName": "GET_DEVICE_INFO"
}
```

`MqttEventHandler` 会优先读取 `event_type`，如果没有则读取 `type`。这让 FireBucket 继续走旧 `event_type`，SolarClean 可以按 Notion 文档里的 `type` 字段表达 ack/state/event。

## 代码落点

- Topic：`app/src/main/java/com/tji/device/product/solarclean/mqtt/SolarCleanMqttTopics.kt`
- 入站解析：`app/src/main/java/com/tji/device/product/solarclean/mqtt/SolarCleanMqttInbound.kt`
- 状态/命令模型：`app/src/main/java/com/tji/device/product/solarclean/model/SolarCleanRuntime.kt`
- 状态仓库：`app/src/main/java/com/tji/device/product/solarclean/repository/SolarCleanRepository.kt`
- 控制下发：`app/src/main/java/com/tji/device/product/solarclean/repository/SolarCleanControlRepository.kt`

## 架构约束

- 光伏清洗不能复用消防吊桶的 `FireBucketLinkDevice`、`Switch`、`FireBucketLinkRepository`。
- 光伏清洗的状态、命令、航线、下载进度都必须放在 `product/solarclean` 下。
- 公共 `data` 层只保存 `ProductType`、`BoundAccountDevice`、登录信息等跨产品壳层数据。
