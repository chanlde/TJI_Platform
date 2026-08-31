# 产品交付与注册矩阵

## 状态

- 状态：active
- 实施日期：2026-08-11

## 目的

本表是 App 正式包的产品交付基线。`ProductCatalog.enabledDefinitions` 是唯一启用来源；只有同时具备产品模块、MQTT topic/handler 和控制页的产品才能设为 `enabled=true`。

未交付产品可以保留类型和展示元数据用于继续开发，但正式登录请求不会查询其 productId；即使后端响应混入设备，也会在发布账号 session 前过滤，不能展示、订阅或进入占位控制页。

## 当前矩阵

| 产品 | productId | enabled | ProductModule / handler | MQTT topic | 控制页 | 正式包处理 |
| --- | ---: | --- | --- | --- | --- | --- |
| FireBucket 消防吊桶 | 2 | 是 | `FireBucketProductModule` | `FireBucketMqttTopics` | `FireBucketControlScreen` | 正常登录、展示、订阅与控制 |
| SolarClean 光伏清洗 | 3 | 是 | `SolarCleanProductModule` | `SolarCleanMqttTopics` | `SolarCleanControlScreen` | 正常登录、展示、订阅与控制 |
| RadioDetection 无线电检测 | 4 | 是 | `RadioDetectionProductModule` | `RadioDetectionMqttTopics` | `RadioDetectionControlScreen` | 正常登录、展示、订阅与控制 |
| DropperSixStage 六段抛投 | 5 | 是 | `DropperSixStageProductModule` | `DropperSixStageMqttTopics` | `DropperSixStageControlScreen` | 正常登录、展示、订阅与控制 |
| Speaker 喊话器 | 6 | 是 | `SpeakerProductModule` | `SpeakerMqttTopics` | `SpeakerControlScreen` | 正常登录、展示、订阅与控制 |
| GlassBreaker 破窗弹 | 7 | 是 | `GlassBreakerProductModule` | `GlassBreakerMqttTopics` | `GlassBreakerControlScreen` | 正常登录、展示、订阅与控制 |
| Searchlight 探照灯 | 8 | 否 | 未实现 | 未实现；禁止占位 topic | 未实现；禁止占位页 | 不请求、不展示、不订阅、不路由 |

## 启用新产品的门禁

将产品改为 `enabled=true` 前必须一次性完成：

- 注册唯一的 `ProductModule` 和 MQTT handler；
- 提供真实 lifecycle/status/control topic，不允许 placeholder；
- 提供正式控制页路由；
- 通过 `ProductModuleRegistryTest`、`MqttTopicLayoutTest`、`ProductControlRouteAvailabilityTest` 和登录过滤测试；
- 核对已有产品 payload、topic、设备顺序和控制行为未变化。

生产 `ProductModuleRegistry` 会校验注册类型与 enabled catalog 完全一致；缺模块或意外注册 disabled 模块都会立即失败，不再静默丢消息。
