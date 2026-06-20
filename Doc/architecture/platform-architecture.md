# TJI 设备平台 工程架构说明

## 状态

- 状态：active
- 说明：当前工程结构和平台分层说明，新增产品线前应优先阅读。

本文描述当前代码结构的划分方式、设计动机，以及如何**新增一条产品线**时的推荐步骤。

---

## 1. 工程与模块划分

| 模块 | 职责 |
|------|------|
| **`:app`** | Android 应用主体：界面、业务 ViewModel、MQTT 前台服务与订阅逻辑、按产品拆分的 UI 与协议入口 |
| **`:NetWork`** | 网络基础设施：`Retrofit` + `OkHttp`（登录、版本信息等 HTTP API）、`MqttManager` + `MQTTConfig`（**仅 Broker 连接参数**） |

**为什么这样拆**

- **HTTP 与 MQTT 分治**：登录/固件信息走 `api.tjinnovations.cloud`；设备实时态走独立 MQTT Broker（`NetworkEndpoints` / `MQTTConfig`），避免把 REST 域名误当 Broker。
- **业务集中在一处**：产品功能、设备状态、订阅策略全部在 **`:app`**，`NetWork` 不依赖 `ProductType`，保持库轻、可复用。

---

## 2. 应用内分层（从外到内）

```
UI (Compose: MainActivity, MainScreen, 各产品 control 屏)
    ↓
ViewModel (MainViewModel, LoginViewModel, 各产品自己的 ViewModel / RuntimeController)
    ↓
Repository (AuthRepository, 各产品侧仓库)
    ↓
Data（`data.model`：BoundAccountDevice、Product、Login；产品运行时模型在 `product/<name>/model`）+  系统服务 (MqttService, MqttSubscriptionManager)
```

- **UI**：`MainScreen` 用「首页选产品线 → 产品下选设备（以登录 `boundDeviceRows` / `bucketsns` 解析的 `BoundAccountDevice` 为主 + 产品自己的 MQTT 运行时态补充在线信息）→ 产品控制台」的导航；**不**在登录页做全局强选设备。
- **ViewModel**：`MainViewModel` 只协调登录、产品入口和按产品 `openProduct` 时的 MQTT 订阅切换；它不直接依赖任何具体产品。产品运行时列表通过 `ProductRuntimeRegistry` 聚合，各产品自己实现 `ProductRuntimeController`（例如 `FireBucketRuntimeController`、`SolarCleanRuntimeController`）。
- **数据真源**  
  - **账号下有哪些设备 / 名称**：登录返回的 `boundDeviceRows`（推荐）或旧字段 `bucketsns` → 经 `LoginResponse.deviceRowsResolved()` 合并后解析为 `BoundAccountDevice`（含 `ProductType` 推断，见 `ProductCatalog.inferType`）。  
  - **产品运行时状态**：必须放在各自 `product/<name>/model|repository|runtime|viewmodel` 下，例如消防吊桶是 `FireBucketLinkDevice` / `FireBucketLinkRepository` / `FireBucketRuntimeController`，光伏清洗是 `SolarCleanDeviceState` / `SolarCleanCommand` / `SolarCleanRuntimeController`。

**为什么这样规划**

- 设备**清单**以服务器/登录为准，不因 MQTT 未上报而「凭空没有设备可选」。
- **实时数据**仍以 MQTT 为准；未上线时 UI 只展示 `BoundAccountDevice` 壳层信息，产品上线后由对应产品包补运行时态。
- 公共 `data` 层不放任何产品专属字段，尤其不能让光伏清洗复用消防吊桶的 `Link/Switch` 模型。

---

## 3. MQTT 架构（清晰点在哪里）

当前约定可以概括为：**一个 Broker 连接 + 每条产品线一套主题前缀 + 订阅时记下 ProductType + 按类型分发入站消息**。

| 组件 | 作用 |
|------|------|
| `MQTTConfig` | 只描述 **Broker 主机、端口、账号、TLS**；**不写**产品线主题（避免误以为「全局只有一条产品线」）。 |
| `MqttTopicLayout` / `mqttTopicsFor(ProductType)` | 各产品主题前缀：`FireBucket/devices/{deviceId}/…`、`SolarClean/devices/{deviceId}/…`。 |
| `MqttSubscriptionManager` | `subscribeToDevices(deviceIds, productType)`：对本批 `deviceId` 订阅 lifecycle/status；并记录 `deviceId` → 产品线，供入站分发与退订。 |
| `MqttEventHandler` | 解析 JSON，按订阅时登记的 `ProductType` 转到对应 `*MqttInbound`。 |
| `FireBucketMqttInbound` / `SolarCleanMqttInbound` | 产品线专属协议：FireBucket 解析 `event_type` 并更新 `FireBucketLinkRepository`；SolarClean 解析自己的 ack/state/event 并更新 `SolarCleanRepository`。 |

**扩展性**：增加产品时，主要是在 **`app`** 侧增加一套 `xxx/devices/` 前缀 + 对应的 Inbound；`NetWork` 的 `MqttManager` **通常不用改**（仍是同一条 TCP 连接）。

---

## 4. 依赖装配（可读性）

`AppContainer` 手写「服务定位器」式装配：**AuthRepo、产品侧 Repository、各产品 Inbound、`MqttEventHandler`、`MqttSubscriptionManager`、`MainViewModelFactory`**。

- **优点**：无 Hilt/Koin 也能看清依赖链，适合中小型项目。
- **代价**：新人加功能要改 **AppContainer + Factory + 路由** 等多处（见下文「新产品清单」）。

---

## 5. 按产品线垂直分包（`* / product / firebucket | solarclean`）

每个产品下有独立子树，例如：

- `product/firebucket/`：MQTT 协议、`FireBucketLinkDevice`、舵机 `Switch`、控制台 UI、悬浮窗面板等。
- `product/solarclean/`：`SolarCleanDeviceState`、`SolarCleanCommand`、MQTT 入站解析、控制台壳子等。

公共横切：`service/`（MQTT 订阅与事件分发）、`product/runtime/`（只暴露跨产品运行时快照和注册表）、`data/model`（只放 `Product`、`BoundAccountDevice`、`Login` 这类壳层模型）、`ui/main/`（主导航）。

**好处**：新产品代码集中在一个包内，阅读边界清晰。  
**硬规则**：每个产品自己的接口、运行时状态、命令、Repository、ViewModel 放在自己的 `product/<name>/` 下。不要为了“看起来统一”把 FireBucket 的 Link/Switch 或 SolarClean 的航线下载状态放到公共 data 层。

---

## 6. 这套架构算不算「清晰」？

**清晰的方面**

- **模块**：HTTP/MQTT 基础与应用分离。  
- **MQTT**：连接配置与主题前缀分离；订阅与产品线绑定；入站路由明确。  
- **导航**：产品与设备选择在 UI 流程上可追溯（MainScreen）。

**仍可改进的点（心里有数即可）**

- **`MqttEventHandler` / `AppContainer`** 每加一个 `ProductType` 仍要手动接线产品 Inbound 和 runtime controller；后续可以把入站分发也改成注册表，减少重复 `when`。  
- **`MainViewModel`** 不包含产品线专属控制：`MainViewModelFactory` 只注入 `ProductRuntimeRegistry`、登录和 MQTT 订阅管理。首页/产品页读取 `ProductDeviceRuntimeSnapshot`，具体 payload 只在进入对应产品控制台时由产品 UI 自己处理。FireBucket 开关控制仍使用 `FireBucketSwitchViewModelFactory` + `SwitchViewModel`。悬浮窗快捷开关通过 `AppContainer.floatingQuickControls`（[`ProductFloatingQuickControl`](../../app/src/main/java/com/tji/device/di/ProductFloatingQuickControl.kt)）按 `ProductType` 路由，新产品需要时在 `AppContainer` 注册实现即可。  
- **`ProductCatalog.inferType`** 靠名称关键字，后端若提供更稳定字段建议改为显式 product 字段。

整体上：**中小型多产品 IoT App，当前结构够用，扩展路径明确**，代价是新产品要按清单逐项落地。

---

## 7. 新增一条产品线时要做什么（实操清单）

假设新产品名为 `Acme`，并已有 MQTT 前缀约定 `Acme/devices/{deviceId}/lifecycle|status|control`。

### 7.1 模型与展示

1. 在 [`Product.kt`](../../app/src/main/java/com/tji/device/data/model/Product.kt) 的 **`enum class ProductType`** 增加 `Acme`。  
2. 在 **`ProductCatalog.definitions`** 里增加 `ProductDefinition`（名称、文案、简短标签）。  
3. 按需补充：`ProductHome` / `MainScreen` 里已有的 `when (productType)` 分支（配色、标题、路由到 `AcmeControlScreen`）。  
4. 新建包 **`app/.../product/acme/`**：至少包含产品自己的 model、mqtt、ui；不要复用其他产品 runtime 类型。  
5. 为新产品实现 **`AcmeRuntimeController`**，输出公共层可读的 `ProductDeviceRuntimeSnapshot`；产品私有字段只能放到 `payload`，不要让公共主页读取产品私有模型。
6. **`BoundAccountDevice` 产品线判定**：扩展 **`ProductCatalog.inferType`**（或改为后端直接带类型），否则新设备可能被误判为消防桶产品线。

### 7.2 MQTT

7. 新增 **`AcmeMqttTopics`**，实现 **`MqttTopicLayout`**（与 FireBucket/SolarClean 同款三段 topic）。  
8. 在 **`mqttTopicsFor`**（[`MqttTopicLayout.kt`](../../app/src/main/java/com/tji/device/service/mqtt/MqttTopicLayout.kt)）中为 `Acme` 分支返回 `AcmeMqttTopics`。  
9. 实现 **`AcmeMqttInbound`**：按 Acme 自己的 payload 字段解析，更新 Acme 自己的 Repository 或状态模型；不要写入 FireBucket 的 `FireBucketLinkRepository`。

### 7.3 接线

10. **`AppContainer`**：构造 `AcmeMqttInbound` 和 `AcmeRuntimeController`，分别接入 `MqttEventHandler` 与 `ProductRuntimeRegistry`。  
11. **`MqttEventHandler`**：构造函数增加 `Acme` 依赖，`when (productType)` 增加 **`ProductType.Acme -> acmeInbound.handleEvent(...)`**。  
12. **`MqttSubscriptionManager`**：`ProductType.values()` 已包含新产品时，未知 `deviceId` 退订时已会遍历所有产品线主题（已实现多前缀退订）。  

### 7.4 可选

13. **悬浮窗 / 快捷控制**：UI 上仿 `FloatingExpandedCard` 增加 Acme 分支；若需在悬浮窗内下发控制指令，实现 `ProductFloatingQuickControl` 并在 **`AppContainer.floatingQuickControls`** 中注册 `Acme`。  
14. **独立业务能力**（如新传感器 SDK）：放在 `product/acme/repository/` 并通过 ViewModel 注入。

---

## 8. 相关文件速查

| 主题 | 路径提示 |
|------|-----------|
| Broker 连接 | `NetWork/MqttConfig.kt`、`NetworkEndpoints.kt`、`MqttManager.kt` |
| HTTP 登录 | `NetWork/data/ApiService.kt`、`LoginViewModel`、`DataReportManager.login` |
| 主导航 | `app/ui/main/MainScreen.kt`、`MainActivity` |
| MQTT 订阅 | `app/service/MqttSubscriptionManager.kt` |
| MQTT 分发 | `app/service/MqttEventHandler.kt` |
| 主题前缀表 | `app/service/mqtt/MqttTopicLayout.kt`、`product/*/mqtt/*MqttTopics.kt` |
| 产品运行时聚合 | `app/product/runtime/ProductRuntime.kt`、`product/*/runtime/*RuntimeController.kt` |
| 装配 | `app/di/AppContainer.kt`、`MainViewModelFactory.kt`、`FireBucketSwitchViewModelFactory`、`ProductFloatingQuickControl` |
| 光伏清洗协议落地 | `Doc/protocols/solar-clean-mqtt-protocol.md` |

---

## 9. 小结

- **架构清晰度**：模块与 MQTT 分层清楚；产品垂直包 + 共享服务，可读性尚可。  
- **为何这样规划**：单机多产品 IoT：共享同一登录与 Broker，差异化在主题前缀与协议解析；列表与实时态数据源分离，避免「没 MQTT 就没设备」。  
- **好不好扩展**：**加新产品有固定套路**（枚举 → 展示 → RuntimeController → MQTT 前缀 → Inbound → Handler/AppContainer）；重复 `when` 可适当再抽象一层注册表以降低样板代码。
