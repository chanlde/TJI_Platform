# SixStageDropper 服务器职责

## 状态

- 状态：draft
- 产品代码：`droppersixstage`
- ProductCode：`SixStageDropper`

## 设备绑定

- 登录返回字段：待后端确认。
- productId / productType：待后端确认。
- 是否复用现有设备绑定接口：待确认。

## MQTT / Broker

- topic 前缀：`SixStageDropper`。
- retained state 策略：待确认。
- 控制权限是否按账号和 `deviceId` 做限制：待确认。

## OTA / 版本

- 是否需要 OTA：待定。
- latest 接口是否复用光伏清洗 OTA 接口：待定。
- 固件下载和回滚策略：待定。

## 上线检查

- 登录后能返回六段抛投设备。
- 设备 `deviceId` 与 MQTT topic 中 `{deviceId}` 一致。
- 测试环境和生产环境 topic 前缀有明确映射。
