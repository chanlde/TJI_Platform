# 光伏清洗服务器职责

## 状态

- 状态：active
- 产品代码：`solarclean`
- productId：`3`

## 设备绑定

- 登录返回字段需要能映射到 `ProductType.SolarClean`。
- productId 为 `3` 时，App 识别为光伏清洗。
- 设备 `deviceId` 必须与 MQTT topic 中 `{deviceId}` 一致。

## OTA / 版本接口

App 当前负责：

- 显示当前固件版本。
- 请求服务器获取最新版本。
- 用户确认后下发 `START_OTA`。
- 监听 `otaStatus` 展示升级状态。

服务端需要负责：

- latest 版本接口。
- 固件元信息。
- 固件下载 URL。
- 发布和回滚策略。

## 上线检查

- 真实服务器能返回光伏清洗设备。
- latest 接口字段与 App 模型一致。
- 测试环境和生产环境的 MQTT topic 前缀一致或有明确映射。
