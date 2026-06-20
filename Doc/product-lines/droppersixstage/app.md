# FC100_FireDrop App 落地

## 状态

- 状态：active
- 产品代码：`droppersixstage`

## 代码落点

```text
app/src/main/java/com/tji/device/product/droppersixstage/
  model/
  mqtt/
  repository/
  runtime/
  ui/
  viewmodel/
```

## 当前 App 能力

- 6 路通道状态展示。
- 单通道控制。
- 全部开关。
- 定时开钩和测试循环。
- ACK 成功、失败、超时反馈。
- 产品设备列表和控制页入口。

## 悬浮窗

- 是否需要悬浮窗快捷控制：已接入基础面板。
- 离线状态：不可发送控制命令。
- 后续待补：是否需要二次确认或安全锁。

## 测试

- `DropperSixStageMqttInboundTest` 覆盖 6 路状态解析、retained state、identity、offline、异常 stage、ACK 成功/失败、retained telemetry 合并。
- 后续可补 UI screenshot：通道选择、小屏、离线、ACK 状态。
