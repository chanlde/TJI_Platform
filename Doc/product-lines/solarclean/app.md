# 光伏清洗 App 落地

## 状态

- 状态：active
- 产品代码：`solarclean`

## 代码落点

```text
app/src/main/java/com/tji/device/product/solarclean/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- MQTT 状态、控制和 lifecycle 接入。
- 控制页和设备设置入口。
- 悬浮窗快捷控制。
- OTA 卡片和升级入口。
- 客户可见状态文案格式化。

## 架构约束

- 状态、命令、航线和下载进度必须放在 `product/solarclean`。
- 首页只读取运行时快照，不解析光伏清洗私有 payload。
- 离线时禁用主控制页和悬浮窗控制。

## 测试

- `SolarCleanMqttInboundTest` 覆盖 status、event 和下载事件。
- `SolarCleanDisplayFormattersTest` 锁住水位和 MQTT 状态客户可见文案。
- 后续可补 UI screenshot：小屏、离线、OTA、悬浮窗。
