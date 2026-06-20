# 无线电检测 App 落地

## 状态

- 状态：active
- 产品代码：`radiodetection`

## 代码落点

```text
app/src/main/java/com/tji/device/product/radiodetection/
  model/
  map/
  mqtt/
  protocol/
  replay/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- 高德地图 SDK 接入。
- 监控页显示真实地图。
- 模拟目标、飞手、探测范围和预警区叠加。
- 地图/列表切换。
- 轨迹、预警区、执法、名单等二级页面骨架。
- RGB 控制模型、仓库和 ViewModel 骨架。

## App 架构约束

- 控制页只使用地图组件，不直接依赖高德 SDK 细节。
- MQTT JSON 解析不写进 Compose UI。
- UI 只消费 `RadioDetectionUiState`。
- 明文高德 Key 不写进 Kotlin 源码。

## 测试

- 后续需要补 RID payload parser 单元测试。
- 后续需要补地图坐标转换测试。
- 后续需要补小屏、横屏、无坐标、无 Key 场景的 UI 验证。
