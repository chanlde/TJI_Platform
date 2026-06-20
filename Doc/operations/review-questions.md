# 不确定问题待 Review

## 状态

- 状态：active
- 说明：当前仍需产品、硬件或服务端确认的问题清单；确认后应转为代码改动、issue 或归档。

本文只记录“可能是问题，但需要产品/硬件/服务端一起确认”的事项。未确认前不直接改协议、不删除业务入口、不改其他端交互。

## 1. 悬浮窗 debug 日志是否需要进一步统一治理

- 位置：`app/src/main/java/com/tji/device/ui/floating/FloatingWindowViewModel.kt`
- 现象：部分日志用于定位悬浮窗状态更新。
- 已处理：包含 `stateHash` 的实现细节日志已用 `BuildConfig.DEBUG` 包起来，release 不再输出。
- 风险：其他普通 `Log.d` 仍可能存在，但不包含客户可见文案或敏感协议字段。
- 建议确认：release 是否要求统一治理所有 debug log。

## 2. 喊话器内部包数统计是否只保留为内部状态

- 位置：`app/src/main/java/com/tji/device/product/speaker/viewmodel/SpeakerControlViewModel.kt`
- 现象：`packetsSent` 仍作为内部状态统计存在。
- 已处理：新增 `SpeakerTalkSectionTest`，锁住客户可见状态文案不显示包数。
- 风险：如果以后别的 UI 直接读取 `packetsSent`，仍可能露出“发送 N 包”。
- 建议确认：是否需要彻底移出 UI state，改为仅日志/metrics；如果保留，应加客户文案扫描测试。

## 3. 太阳能清洗未知水位值是否应显示为“未知”

- 位置：`app/src/main/java/com/tji/device/product/solarclean/ui/control/SolarCleanDisplayFormatters.kt`
- 现象：水位 `0/1/2` 显示为 `低/正常/高`；其他数值当前直接显示原始数字。
- 已处理：新增 `SolarCleanDisplayFormattersTest` 锁住当前行为，避免无意变化。
- 风险：客户看到 `9` 这类原始数字可能不理解；但直接改成“未知”也可能隐藏现场诊断信息。
- 建议确认：客户版是否统一显示“未知”，诊断版/日志保留原始值。
