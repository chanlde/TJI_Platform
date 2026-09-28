# FireGun 协议边界

## 状态

- 状态：active
- 产品代码：`firegun`

## MQTT Topic 与身份

喷枪沿用 `FireBucket/devices/{Link SN}` 前缀，使用 `lifecycle`、`status`、`event` 和 `control` Topic。Topic 中是 Link 序列号；控制报文的 `serial_number` 是下方 ESP32 序列号。App 必须同时核对 Topic 的 Link 身份、设备序列号和请求编号，避免跨设备 ACK 覆盖当前操作。

## 当前控制载荷

- 解锁：`event_type=LockControlRequest`、`action=unlock`、`request_id`、下方设备 `serial_number`。
- 展开／收回／停止：`event_type=ActuatorControlRequest`、`action=open|close|stop`、`request_id`、下方设备 `serial_number`。
- 控制使用 QoS 1、非 retained；Broker 确认发布不代表设备业务执行成功。

设备回执分别使用 `LockControlResponse` 和 `ActuatorControlResponse`；App 按 `request_id`、序列号和动作解析。Link 启动快照、心跳及下方状态为独立上报，离线与旧请求的清理规则由运行时状态机处理。字段细节以 `FireGunProtocol`、`FireGunMqttTopics` 和对应单测为当前软件实现依据；真机协议仍须逐字段确认。
