# FireGun App 实现

## 状态

- 状态：active
- 产品代码：`firegun`

## 代码边界

`ProductCatalog` 根据 FireGun 显式编码或旧 FireBucket 绑定中的 `HydroGunLink_` 名称识别模块。`FireGunProductModule` 注册运行时；`FireGunMqttTopics` 负责 Link Topic；`FireGunProtocol` 负责控制与状态报文；`FireGunRepo` 保存 Link 与下方设备状态；`FireGunControlViewModel` 驱动主控制页和悬浮窗。

下发命令必须使用 Link SN 构造 Topic、下方 ESP32 SN 构造 payload，并通过请求编号匹配回执。Link 离线、心跳超时或切换设备时，界面应清理过期控制状态。

## 验证

产品识别、双 SN 路由、协议解析、控制状态机和 MQTT 入站处理已有单测；noMap/map 调试单测与 noMap Release 构建通过。真实设备执行、错误回执和悬浮窗现场操作仍需实物验收。
