# 破窗弹 App 落地

## 状态

- 状态：active
- 产品代码：`glassbreaker`

## 代码目录

```text
app/src/main/java/com/tji/device/product/glassbreaker/
  model/
  mqtt/
  repository/
  runtime/
  ui/control/
  viewmodel/
```

## 当前 UI 和控制流程

- 页面进入后查询当前设备信息。
- 在线设备可以解锁、上锁和切换激光。
- 只有在线且已解锁时才能选择 1～4 号通道。
- 只有在线、已解锁且已选择通道时才能滑动确认击发。
- 命令展示发送中、成功、失败或 3 秒无响应反馈。
- 破窗弹暂不提供悬浮窗快捷控制。

## 状态与生命周期

- Repository 保存按设备序列号区分的运行状态。
- 较旧的设备时间戳不能覆盖较新的状态。
- Retained 状态不能把离线设备直接标记为在线。
- Runtime controller 向平台提供中立的产品运行时快照。

## 现有测试重点

- 状态与 ACK 合并、旧时间戳保护。
- 同类冲突命令的 pending 跟踪。
- 通道范围和 payload 字段。
- 在线、解锁、选择通道和击发前置条件。

文档治理和 CI 优化不得修改上述业务行为。
