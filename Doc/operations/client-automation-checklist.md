# 客户端自动化测试与自迭代清单

## 状态

- 状态：active
- 说明：当前客户端可自动化测试与发布前扫描清单，随新增产品线持续补充。

本文只覆盖 App 侧可以独立完成的测试与迭代，不要求服务器、单片机或真实无人机配合；涉及接口协议、服务端返回字段、固件行为的问题只记录，不擅自改协议。

## 当前可自动化范围

### JVM 单元测试

运行：

```bash
./gradlew testDebugUnitTest --console=plain
```

适合覆盖：

- 产品目录：产品 ID、产品 code、后端字段识别、未知设备兜底。
- 产品模块注册表：runtime、MQTT handler、悬浮窗快捷控制按 `ProductType` 注册。
- MQTT topic 与 profile 路由：不同产品是否走正确 topic，只有无线电检测走 legacy MQTT profile。
- MQTT 入站解析：产品 JSON 字段、别名字段、乱序消息、retained 消息。
- OTA 通用逻辑：设备信息解析、升级状态解析、进度换算、终态保护、旧 seq 丢弃。
- 喊话器音频纯逻辑：PCM/ADPCM/HADP 数据结构、录音时长/大小格式化、客户 UI 文案不泄露包数。
- 悬浮窗状态机：选中设备、空状态、多设备摘要、快速控制入口。

### 静态扫描

```bash
rg -n "TODO|FIXME|发送.*包|packetsSent|debug|Debug|测试设备" app/src/main/java NetWork/src/main/java
```

用途：

- 检查客户界面是否露出底层包数、原始 ACK、调试 hash、测试设备字样。
- 检查 release 前是否仍有明显 TODO/FIXME。
- 检查新增产品是否散落到公共层，违反产品垂直分包。

### Compose Preview 检查

当前适合检查主界面、喊话器、六段抛投、太阳能清洗、悬浮窗和公共组件。后续可以引入截图测试，但在依赖引入前，先用 Preview 加 JVM 文案/格式化单测兜底。

### Android Emulator QA

不需要单片机也能跑：

- 登录页基础渲染、输入、错误提示。
- 使用 debug-only 演示设备进入各产品页。
- 主界面切换产品、进入控制页、打开设置页。
- 悬浮窗权限页/服务启动前后的 UI 状态。
- App 冷启动、横竖屏、返回键、低宽度设备截图。

不连接真实 MQTT/服务器时，只验证 UI 状态与本地兜底数据；真实登录、设备绑定、固件升级命令发送不能算纯客户端自动化完成。

## 已落地的新增客户端自检

- `MqttTopicLayoutTest`：锁住各产品 lifecycle/status/control topic；保留无线电检测 RID status topic 的 legacy 例外。
- `ProductMqttRouterTest`：锁住只有 `RadioDetection` 走 `RADIO_DETECTION_LEGACY` profile，其余产品走平台 MQTT。
- `SpeakerTalkSectionTest`：锁住喊话状态文案，不把内部 `packetsSent` 或“包数”暴露给客户。
- `MqttEventHandlerTest`：验证 plain `online/offline`、通用 OTA cache、产品 handler 分发，不连接真实 MQTT。
- `SolarCleanMqttInboundTest`：覆盖 deviceInfo、OTA status、state telemetry/GPS/水位/download、retained online、route ACK slots 合并、downloadError、routeExecuteFinished。
- `DropperSixStageMqttInboundTest`：覆盖 6 路状态解析、retained state、identity、offline、异常 stage、ACK 成功/失败、retained telemetry 合并。
- `CustomerVisibleTextGuardTest`：扫描生产源码，防止“测试设备”进入客户可见代码；同时锁住 release 关闭本地演示设备和 OTA 测试入口。
- `ProductRuntimeRegistryTest`：锁住运行时 registry 的按产品清理、全量清理、重复产品注册覆盖行为。
- `ProductOtaFormattersTest`：补充 OTA 进度 clamp、字节进度边界、升级包可启动条件。
- `SolarCleanDisplayFormattersTest`：锁住水位和 MQTT 状态客户可见文案。

## 自动化已抓到并修复的问题

- 太阳能清洗第一次收到 route list ACK 时，`lastAck` 已保存但 `routeSlots` 没有进入新建 state。已修复 `SolarCleanRepo.updateAck` 的 create 分支，并由 `SolarCleanMqttInboundTest` 覆盖。
- 六段抛投第一次收到单路成功 ACK 时，`lastAck` 已保存但对应通道没有打开。已修复 `DropperSixStageRepo.updateAck` 的 create 分支，并由 `DropperSixStageMqttInboundTest` 覆盖。
- 喊话器/六段抛投的本地兜底设备会进入产品设备列表，存在 release 包展示“测试设备”的风险。已改为 `BuildConfig.TJI_ENABLE_LOCAL_DEMO_DEVICES`：debug 开启、release 关闭，并由 `CustomerVisibleTextGuardTest` 覆盖。
- 太阳能清洗第一次收到 downloadError/downloadProgress 这类事件时，只保存 `lastEvent`，没有生成对应 `download` 展示状态。已修复 `SolarCleanRepo.updateEvent` 的 create 分支，并由 `SolarCleanMqttInboundTest` 覆盖。

## 下一批优先级

### P0

- `./gradlew testDebugUnitTest --console=plain`
- Release 前跑 `./gradlew assembleRelease --console=plain`

### P1

- `SolarCleanMqttInbound` 继续补 stale Unix 时间戳、downloadDone、offline lifecycle。
- `DropperSixStageMqttInbound` 继续补重复 retained state、identity 与 state 同 `deviceId` 合并策略。
- 客户可见文案扫描测试继续扩展：禁止“发送 N 包、ACK、raw、debug hash”等调试词出现在产品控制 UI 文案中。

### P2

- Compose screenshot 测试：主界面、喊话器、六段抛投、太阳能清洗、悬浮窗分别覆盖 360dp/393dp 宽度。
- Accessibility 检查：按钮可点击区域、状态颜色是否有文字辅助、长文本是否截断合理。
- Emulator smoke：冷启动、进入各产品页、返回、旋转、低内存重建。

## GitHub Issue 同步规则

- 已确认且本轮能修：直接修复，加测试，最终在提交说明里写清楚。
- 已确认但本轮不能修：开 GitHub issue，写复现证据、影响范围、建议修法。
- 不确定是否真问题：只写入 `Doc/operations/review-questions.md`，等人工 review 后再改代码或开 issue。
