# TJI Platform

版本：`V2.0.2`

TJI Platform 是一个基于 Android Jetpack Compose 的多产品设备管理 App。当前项目由原来的消防吊桶控制 App 演进而来，目标是把消防吊桶、光伏清洗等简单设备统一放到同一个平台 App 中管理；大疆 MSDK 这类复杂产品暂不放入本 App。

## 当前能力

- 账号登录与绑定设备获取。
- 多产品首页：按产品线进入设备列表。
- 消防吊桶 `FireBucket`：保留 Link / 桶控制逻辑，一个账号可有多个 Link，一个 Link 下可挂多个桶。
- 光伏清洗 `SolarClean`：已接入 MQTT 状态、控制、悬浮窗快捷控制、设备设置与 OTA 入口。
- MQTT 实时通信：按产品订阅 `status` / `lifecycle`，按设备发布 `control`。
- 悬浮窗控制：按产品类型显示不同控制面板。
- Compose 代码图标：公共图标和产品图标已按模块拆分。

## 产品架构

核心产品类型：

- `FireBucket`：消防吊桶。
- `SolarClean`：光伏清洗。

主要分层：

```text
app/src/main/java/com/tji/device/
  data/             # 公共登录、账号设备、产品模型
  product/
    firebucket/     # 消防吊桶独立模型、MQTT、仓库、UI
    solarclean/     # 光伏清洗独立模型、MQTT、仓库、UI
    runtime/        # 跨产品运行时快照与注册表
  service/          # MQTT 订阅与事件分发
  ui/               # 平台首页、登录页、悬浮窗、公共组件
  di/               # AppContainer 手动依赖注入
NetWork/            # 网络、HTTP、MQTT 基础模块
Doc/                # 项目说明与协议文档
```

新增产品线前先阅读：

```text
Doc/新增产品线接入规范.md
```

该文档用于统一新增产品的目录隔离、UI 风格、MQTT topic、悬浮窗、测试和交付清单。具体产品需求再写到对应产品自己的 Doc 下。

## MQTT 约定

光伏清洗当前使用平台统一 topic：

```text
SolarClean/devices/{deviceSn}/control
SolarClean/devices/{deviceSn}/status
SolarClean/devices/{deviceSn}/lifecycle
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

单片机侧需要负责 Bootloader、A/B 分区、固件下载、校验、启动确认、失败回滚。详细方案见 `Doc/OTA升级方案.md`。

## 构建

环境要求：

- Android Studio
- JDK 11+
- Android SDK

快速编译验证：

```bash
./gradlew :app:compileDebugKotlin
```

构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

当前版本配置在 `gradle.properties`：

```properties
APP_VERSION_CODE=202
APP_VERSION_NAME=V2.0.2
```

## 当前重点

- 用真实 MCU 验证 `control/status/lifecycle` 全流程。
- 用真实服务器验证登录设备字段和 OTA latest 接口。
- 验证 MQTT 弱网、断线重连、ClientId 冲突、retained state。
- 继续清理历史消防吊桶代码中的过时命名和旧逻辑。

## 说明

本仓库是 TJI 设备平台 App 工程，当前阶段重点是稳定多产品架构、光伏清洗 MQTT 控制链路、消防吊桶旧能力迁移和 OTA 联调。
