# TJI Platform

版本：`V2.0.12`

TJI Platform 是一个基于 Android Jetpack Compose 的多产品设备管理 App。当前项目由原来的消防吊桶控制 App 演进而来，目标是把消防吊桶、光伏清洗、六段抛投、喊话器、无线电侦测等产品统一放到同一个平台 App 中管理；大疆 MSDK 这类复杂产品暂不放入本 App。

## V2.0.12 更新内容

- 按住松手喊话、系统 TTS 和录音保存统一使用可靠 Ogg/Opus UDP 媒体传输，
  共用 ACK、重传、背压、分块 CRC 和整文件校验。
- 删除 App 旧 `RECORD_DOWNLOAD`/`START_RECORD_STORE` 模型、特殊 JSON 编码器
  和音频 HTTP 上传服务；HTTP 下载仅由 MCU OTA 使用。
- 保留独立的 MCU 麦克风 raw Opus 回传，并继续支持与扬声器播放同时运行。
- 喊话器日志通过轻量 `SpeakerLogger` 统一 Debug/Release 控制，UDP relay
  改用标准分级日志，不引入额外日志框架。
- 同步 MCU `2.2.0` 的命令、媒体传输和录音事件协议，并通过双变体单测、
  native core、UDP relay 及 Release 构建验证。

## V2.0.11 更新内容

- 重构网络与会话边界：统一 HTTP 入口、端点配置、登录会话和 MQTT 连接状态，旧账号或旧请求返回后不再覆盖当前界面状态。
- 整理多产品运行时和 MQTT 生命周期，补齐命令等待、ACK 匹配、重连订阅及退出清理逻辑，降低跨设备、跨账号状态串扰。
- 拆分喊话器大 ViewModel，将设备命令、录音保存、TTS、MCU 麦克风回传和 UI 状态分到职责明确的协调类。
- 收口喊话器音频链路：松手喊话使用 48 kHz raw Opus 可靠 UDP 直传；
  TTS、保存录音和非实时播放继续使用 Ogg Opus 文件。
- 增加 MCU 麦克风 raw Opus 回传接收、抖动缓冲及生命周期控制，并补充 App、UDP relay、native speaker-core 和 MCU 协议测试。
- 将地图能力拆成 `map` / `noMap` 两个正式构建变体，非地图包不再携带地图 SDK、资源和权限。
- 清理含糊工具类、过时仓库和重复组件，改用按职责命名的 session、error、concurrent、repository、controller 与 coordinator。
- 发布前验证覆盖双变体单测、Lint、R8 Release 打包及模拟器冷启动。

## V2.0.10 更新内容

- 瘦身客户安装包：移除未启用的离线 Kokoro TTS native runtime（onnxruntime / sherpa-onnx），文字喊话保留手机系统 TTS，避免无模型情况下空占约 29MB。
- 隐藏喊话器“内置语音”入口，防止客户点到未打包的离线 TTS 链路。

## V2.0.9 更新内容

- 喊话器页面收口客户可见状态：按住喊话页不再展示底层设备状态数据，不再把设备 `lastError` 转成“设备处理失败，请重试”暴露给客户。
- 录音库支持按时间排序切换，刷新和加载更多会按当前排序方向请求设备列表，并在 App 本地保持显示顺序一致。
- 统一 App 滑动条样式，移除旧 `PayloadSlider` / `CustomSlider` / `SpeakerSmoothSlider`，全部收敛到光伏清洗同款 `TjiControlSlider`。
- 喊话器 App 音频改为 PCM 直通：录音和 TTS 不再经过自研门限、AGC、压缩或 EQ，避免轻声与首尾被误切。
- MCU 麦克风回传直接播放 MCU 的 SpeexDSP AEC 输出，不在 App 重复处理。

## V2.0.5 更新内容

- 发布前安全收口：release 包通过 R8 剥离 Android 日志调用，减少账号、MQTT、设备诊断信息在正式包中的暴露。
- 首页和设备列表只显示当前账号绑定设备，移除本地演示设备兜底数据，避免客户看到无关产品或测试设备。
- OTA 入口切换为正式升级流程：查询最新版本、确认后下发 `START_OTA`，并通过设备状态展示升级进度。
- 客户可见文案继续中文化：Wi-Fi 模式、经纬高、序列号、远程识别、TTS 引擎/音色、MQTT/网络/服务端错误提示等改为中文或更自然的中文表述。
- 清理客户界面调试入口：隐藏 OTA 下载测试、测试账号注释和六段抛投测试表述，保留正式控制能力。
- 喊话器链路继续收口：补充 native core/JNI shadow、命令 JSON、MQTT 载荷解析、语音处理与黄金样本测试，提升音频链路一致性。
- 更新产品文档和协议说明，保持光伏清洗、喊话器、无线电侦测等产品线交付描述同步。

## V2.0.4 更新内容

- 收口喊话器音频链路：移除 App 云端 TTS 分支，文字转语音统一由 Android 系统 TTS 生成 Ogg/Opus，再通过可靠 UDP 媒体传输给 MCU 播放或保存。
- 删除旧音频文件上传/HTTP 下载服务，TTS、按住松手喊话和录音保存共用同一套可靠 UDP 媒体传输。
- 清理客户界面测试入口：设置页只保留正式“播放蜂鸣”，移除静音文件、数据校验、本机旧格式、音质测试等调试按钮和对应 ViewModel 死代码。
- 完善喊话器输出音质：支持低/中/高三档输出配置，TTS 与录音文件上传按当前音质写入对应 `.hadp` 元数据。
- 优化录音库链路：保存、删除、改名后刷新录音库和容量状态，分页加载按每页 4 条处理，减少一次性拉取压力。
- 补充本地 TTS 运行组件：App 接入 sherpa-onnx JNI wrapper 和 Android native libs；大模型资源不直接提交普通 Git，需本地放入 `app/src/main/assets/kokoro-multi-lang-v1_0/` 或后续改用 Git LFS/外部分发。

## V2.0.3 更新内容

- 统一 App UI 风格：引入 `PayloadColors` / `PayloadDimens` / `PayloadControls`，主界面、产品控制页、悬浮窗、登录页和通用组件逐步收敛到同一套视觉 token。
- 重做喊话器 UI：拆分设备状态、按住喊话、录音库、文字喊话、音色调节等 Compose 组件，隐藏客户不应看到的底层包数、原始 ACK 和调试信息。
- 优化六段抛投交互：从 6 份重复控制卡改为“通道选择 + 单一当前通道控制面板”，保留全局全部开钩/关闭能力。
- 完善产品模块化：新增 `ProductModuleRegistry`，降低 `AppContainer` 和主界面对具体产品实现的耦合。
- 拆分大 Compose 页面：主界面、喊话器、太阳能清洗、六段抛投等页面拆出 section、preview、widget 文件，提升代码定位和维护体验。
- 补齐组件级 Preview：为喊话器、六段抛投、太阳能清洗等关键 UI 增加组件级 Preview，避免只点到整页 Preview。
- 扩展产品能力：补充六段抛投、无线电侦测、喊话器相关模型、MQTT 入站解析、仓库、ViewModel、悬浮窗与测试覆盖。
- 增加 UDP relay 服务目录和联调说明，用于真实设备语音链路联调。

## 当前能力

- 账号登录与绑定设备获取。
- 多产品首页：按产品线进入设备列表。
- 消防吊桶 `FireBucket`：保留 Link / 桶控制逻辑，一个账号可有多个 Link，一个 Link 下可挂多个桶。
- 光伏清洗 `SolarClean`：已接入 MQTT 状态、控制、悬浮窗快捷控制、设备设置与 OTA 入口。
- 六段抛投 `SixStageDropper`：支持 6 路通道状态展示、单通道控制、全部开/关、定时开钩和测试循环。
- 喊话器 `Speaker`：支持按住录音后 Opus UDP 直传播放、录音
  保存/播放/删除/改名、文字转语音、音色调节和存储状态展示。
- 无线电侦测 `RadioDetection`：支持侦测监控界面、目标列表、回放/轨迹/告警等业务页面骨架。
- MQTT 实时通信：按产品订阅 `status` / `lifecycle`，按设备发布 `control`。
- 悬浮窗控制：按产品类型显示不同控制面板。
- Compose 代码图标：公共图标和产品图标已按模块拆分。
- 组件级 Preview：关键产品控制卡、状态卡、列表项可单独预览和定位代码。

## 产品架构

核心产品类型：

- `FireBucket`：消防吊桶。
- `SolarClean`：光伏清洗。
- `SixStageDropper`：六段抛投（App 内部类型名保留 `DropperSixStage`）。
- `Speaker`：喊话器。
- `RadioDetection`：无线电侦测。

主要分层：

```text
app/src/main/java/com/tji/device/
  data/             # 公共登录、账号设备、产品模型
  product/
    firebucket/     # 消防吊桶独立模型、MQTT、仓库、UI
    solarclean/     # 光伏清洗独立模型、MQTT、仓库、UI
    droppersixstage/# 六段抛投独立模型、MQTT、仓库、UI
    speaker/        # 喊话器音频、MQTT、仓库、UI
    radiodetection/ # 无线电侦测模型、地图、控制 UI
    runtime/        # 跨产品运行时快照与注册表
  service/          # MQTT 订阅与事件分发
  ui/               # 平台首页、登录页、悬浮窗、公共组件
  di/               # AppContainer 与 ProductModuleRegistry 手动依赖注入
NetWork/            # 网络、HTTP、MQTT 基础模块
Doc/                # 分层文档入口
server/             # 辅助联调服务脚本与部署说明
```

完整文档入口见 [Doc/README.md](Doc/README.md)。

新增产品线前先阅读：

```text
Doc/architecture/product-line-onboarding.md
```

该文档用于统一新增产品的目录隔离、UI 风格、MQTT topic、悬浮窗、测试和交付清单。具体产品需求再写到对应产品自己的 Doc 下。

## MQTT 约定

光伏清洗当前使用平台统一 topic：

```text
SolarClean/devices/{deviceId}/control
SolarClean/devices/{deviceId}/status
SolarClean/devices/{deviceId}/lifecycle
```

当前启用命令码：

| cmd | cmdName | 说明 |
|---:|---|---|
| 0 | `PING` | MQTT 连通性诊断 |
| 1 | `GET_DEVICE_INFO` | 查询设备信息 |
| 2 | `SET_PUMP` | 水泵开关 |
| 3 | `SET_PUMP_PRESSURE` | 水泵压力 |
| 4 | `SET_SPRAY_ANGLE` | 喷洒角度 |
| 5 | `SET_SWING_SPEED` | 摆动速度 |
| 6 | `SET_SERVO_SWING` | 摆动开关 |
| 20 | `START_OTA` | 开始 OTA |

航线、槽位、KMZ 下载、航线执行当前不做。

## OTA 状态

App 当前负责：

- 显示当前固件版本。
- 请求服务器获取最新版本。
- 用户确认后下发 `START_OTA`。
- 监听 `otaStatus` 展示升级状态。

单片机侧需要负责 Bootloader、A/B 分区、固件下载、校验、启动确认、失败回滚。详细方案见 `Doc/features/ota/solar-clean-ota-plan.md`。

## 构建

环境要求：

- Android Studio
- JDK 11+
- Android SDK

快速编译验证：

```bash
./gradlew :app:compileDebugKotlin
```

文档结构验证：

```bash
./gradlew checkDocs
```

构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

当前版本配置在 `gradle.properties`：

```properties
APP_VERSION_CODE=212
APP_VERSION_NAME=V2.0.12
```

## 当前重点

- 用真实 MCU 验证 `control/status/lifecycle` 全流程。
- 用真实服务器验证登录设备字段和 OTA latest 接口。
- 验证 MQTT 弱网、断线重连、ClientId 冲突、retained state。
- 继续清理历史消防吊桶代码中的过时命名和旧逻辑。

## 说明

本仓库是 TJI 设备平台 App 工程，当前阶段重点是稳定多产品架构、光伏清洗 MQTT 控制链路、消防吊桶旧能力迁移和 OTA 联调。
