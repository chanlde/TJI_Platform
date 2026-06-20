# {ProductName} App 落地

## 状态

- 状态：draft
- 适用产品代码：`{productCode}`
- 最近更新：YYYY-MM-DD

## 代码目录

```text
app/src/main/java/com/tji/device/product/{productCode}/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 接入清单

- 在 `ProductType` 增加产品类型。
- 在 `ProductCatalog` 增加 `ProductDefinition`。
- 增加产品图标和场景图映射。
- 建 MQTT topics 和 inbound。
- 建 repository。
- 建 runtime controller 并注册。
- 建控制页并接入 `ProductControlRoute`。
- 如需要悬浮窗，实现产品面板和 `ProductFloatingQuickControl`。
- 在 `AppContainer` 注册依赖。
- 补测试和 Preview。

## UI

- 首页入口：待补充。
- 设备列表：待补充。
- 控制详情页：待补充。
- 设置页：待补充。
- 悬浮窗：待补充。

## 测试

- 产品类型识别。
- MQTT payload 解析。
- ACK 成功和失败。
- 离线状态。
- 悬浮窗选中设备。
- 小屏、横屏和 Preview。

## 验收

- 登录后能看到产品入口。
- 进入产品页后只显示该产品设备。
- 控制命令能发出并显示 ACK。
- 离线设备不可控制。
- 客户界面不显示 topic、payload、调试包数或内部错误码。
