# 消防吊桶 App 落地

## 状态

- 状态：active
- 产品代码：`firebucket`

## 代码落点

```text
app/src/main/java/com/tji/device/product/firebucket/
  control/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- 产品入口和设备列表。
- 消防吊桶控制台。
- Link / 桶运行时聚合。
- 悬浮窗快捷控制。
- 控制反馈展示。

## 架构约束

- 消防吊桶私有状态只放在 `product/firebucket`。
- `MainViewModel` 不直接持有消防吊桶控制逻辑。
- 悬浮窗通过 `ProductFloatingQuickControl` 路由产品快捷控制。

## 测试

- 后续继续保留 Link / 桶运行时和 MQTT 解析测试。
- 新增产品时不能复用消防吊桶私有模型作为公共模型。
