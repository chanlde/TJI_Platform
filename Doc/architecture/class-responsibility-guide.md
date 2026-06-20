# App 架构与类职责说明

## 状态

- 状态：active
- 说明：当前 App 分层和核心类职责说明，作为新增产品线与重构时的架构参考。

这份文档用来回答两个问题：

1. **每个核心类是干嘛的。**
2. **为什么要这么拆，而不是继续按单一水桶 App 写。**

当前 App 的目标不是只控制消防吊桶，而是逐步变成一个统一设备 App：登录、产品入口、MQTT 连接可以共用，但每个产品的协议、状态、控制 UI 必须隔离。

---

## 1. 总体原则

一句话：

> 公共层只做平台能力；产品层只做自己的业务。

也就是：

- 登录、账号设备、产品入口：公共层。
- MQTT 连接、订阅、分发：公共层。
- 消防吊桶的 Link、Switch、控制协议：`product/firebucket`。
- 光伏清洗的状态、命令、控制协议：`product/solarclean`。
- 首页只看通用设备快照，不直接读取某个产品的私有模型。

这样做是为了避免一个问题：如果 `MainViewModel`、首页、公共 data 层都直接依赖 `FireBucketLinkDevice` 或 `Switch`，后面光伏清洗、其他产品进来时，公共代码会越来越像“消防吊桶 App 加补丁”，不是统一平台。

---

## 2. 当前核心流程

```text
MainActivity
  -> LoginWidget / MainScreen
  -> MainViewModel
  -> ProductRuntimeRegistry
  -> FireBucketRuntimeController / SolarCleanRuntimeController
  -> 各产品 Repository / 状态模型
  -> MqttEventHandler
  -> FireBucketMqttInbound / SolarCleanMqttInbound
  -> MqttSubscriptionManager
```

用户侧流程：

```text
登录
  -> 平台首页
  -> 选择产品
  -> 订阅该产品设备 MQTT
  -> 选择设备
  -> 进入对应产品控制页
```

---

## 3. 公共模型层

路径：

```text
app/src/main/java/com/tji/device/data/model/
```

### ProductType

文件：

```text
app/src/main/java/com/tji/device/data/model/Product.kt
```

作用：

- 表示产品类型。
- 当前有 `FireBucket`、`SolarClean`。
- 新增产品时，第一步通常就是加一个新的 `ProductType`。

为什么需要：

- App 需要知道当前设备属于哪个产品线。
- MQTT 订阅、首页卡片、详情页分流、悬浮窗分流都要靠它判断。

注意：

- `ProductType` 是公共层可以知道的东西。
- 但公共层不应该知道每个产品内部有哪些字段。

### ProductDefinition

文件：

```text
app/src/main/java/com/tji/device/data/model/Product.kt
```

作用：

- 定义产品展示信息。
- 比如显示名、短标签、描述。

为什么需要：

- UI 需要展示产品卡片。
- 不应该在每个页面到处硬编码产品名字。

### ProductCatalog

文件：

```text
app/src/main/java/com/tji/device/data/model/Product.kt
```

作用：

- 根据 `ProductType` 返回 `ProductDefinition`。
- 当前还负责根据设备名、设备类型等字段推断产品类型。

为什么需要：

- 现在登录接口没有稳定返回 `productType`，所以前端只能临时推断。
- 等后端返回明确字段后，应该减少这种靠名字推断的逻辑。

注意：

- `inferType(...)` 是过渡方案，不是最终理想方案。
- 最好后端后续返回明确的 `productId`、`productCode` 或 `productType`。

### BoundAccountDevice

文件：

```text
app/src/main/java/com/tji/device/data/model/BoundAccountDevice.kt
```

作用：

- 表示登录账号下绑定的设备。
- 它是“账号设备清单”，不是 MQTT 实时状态。

包含的信息：

- 设备 `deviceId`。
- 设备名称。
- 产品类型。

为什么需要：

- 首页和产品页应该先根据账号绑定信息展示设备入口。
- 不能因为 MQTT 还没上报，就让用户看不到设备。

---

## 4. 主 ViewModel 层

路径：

```text
app/src/main/java/com/tji/device/data/viewmodel/
```

### LoginViewModel

作用：

- 负责登录。
- 保存登录状态。
- 解析登录接口返回的账号设备。

为什么在公共层：

- 登录是整个平台共用能力。
- 不属于消防吊桶，也不属于光伏清洗。

### MainViewModel

作用：

- 管理主界面的平台逻辑。
- 对外暴露运行时设备列表 `runtimeDevices`。
- 用户进入某个产品页时，调用 `openProduct(productType)`。
- `openProduct(...)` 会按产品类型订阅对应设备 MQTT。

它不应该做什么：

- 不应该直接依赖 `FireBucketLinkDevice`。
- 不应该直接依赖 `Switch`。
- 不应该直接依赖 `SolarCleanDeviceState`。
- 不应该写某个产品的控制逻辑。

为什么要这样：

- `MainViewModel` 是平台主 ViewModel。
- 如果它直接依赖消防吊桶，后面每加产品都要污染主逻辑。

正确依赖方式：

```text
MainViewModel
  -> ProductRuntimeRegistry
  -> ProductDeviceRuntimeSnapshot
```

也就是说，主 ViewModel 只看“通用设备快照”，不看产品私有结构。

---

## 5. 产品运行时层

路径：

```text
app/src/main/java/com/tji/device/product/runtime/
```

### ProductRuntimePayload

作用：

- 产品私有运行时模型的标记接口。
- 比如 `FireBucketLinkDevice` 可以作为 payload。
- 比如后续 `SolarCleanDeviceState` 也可以作为 payload。

为什么需要：

- 公共层有时候需要把产品私有对象一路带到详情页。
- 但公共层不应该读取里面的字段。

规则：

- 公共首页可以持有 `payload`。
- 公共首页不能解析 `payload`。
- 到具体产品详情页后，产品 UI 自己把 payload 转回自己的模型。

### ProductDeviceRuntimeSnapshot

作用：

- 首页和产品页使用的通用设备快照。

包含的信息：

- `serialNumber`
- `name`
- `productType`
- `isOnline`
- `childCount`
- `payload`

为什么需要：

- 首页设备卡片只需要这些通用信息。
- 消防吊桶和光伏清洗的具体协议完全不同，但首页不应该关心。

### ProductRuntimeController

作用：

- 每个产品都实现一个运行时控制器。
- 对公共层输出该产品的设备快照列表。

为什么需要：

- 让 `MainViewModel` 不直接依赖具体产品。
- 新增产品时，只需要给产品自己写一个 controller，然后注册进 `ProductRuntimeRegistry`。

### ProductRuntimeRegistry

作用：

- 聚合所有产品的 `ProductRuntimeController`。
- 给 `MainViewModel` 提供所有产品的运行时设备列表。
- 统一清理所有产品运行时状态。

为什么需要：

- `MainViewModel` 不应该知道有多少产品、每个产品怎么存状态。
- 注册表把这些产品实现收在一起。

---

## 6. MQTT 公共层

路径：

```text
app/src/main/java/com/tji/device/service/
app/src/main/java/com/tji/device/service/mqtt/
```

### MqttSubscriptionManager

作用：

- 负责订阅和退订 MQTT topic。
- 记录某个 `deviceId` 属于哪个 `ProductType`。
- 进入某个产品页时才订阅该产品设备。

为什么需要：

- MQTT 连接可以共用。
- 但不同产品的 topic 前缀不同，消息解析也不同。

注意：

- 登录后不应该自动订阅所有东西。
- 悬浮窗也不应该重复订阅。
- 当前策略是：用户进入产品页时订阅该产品相关设备。

### MqttTopicLayout

作用：

- 定义产品 MQTT topic 结构。
- 例如：

```text
FireBucket/devices/{deviceId}/lifecycle
FireBucket/devices/{deviceId}/status
FireBucket/devices/{deviceId}/control

SolarClean/devices/{deviceId}/lifecycle
SolarClean/devices/{deviceId}/status
SolarClean/devices/{deviceId}/control
```

为什么需要：

- 不把 topic 写死在订阅逻辑里。
- 新产品只需要实现自己的 topic layout。

### MqttEventHandler

作用：

- 收到 MQTT 消息后，判断这条消息属于哪个产品。
- 然后分发给对应产品的入站解析类。

比如：

```text
ProductType.FireBucket -> FireBucketMqttInbound
ProductType.SolarClean -> SolarCleanMqttInbound
```

为什么需要：

- 公共 MQTT 层只负责分发。
- 具体 JSON 怎么解析，交给产品自己。

---

## 7. 消防吊桶产品层

路径：

```text
app/src/main/java/com/tji/device/product/firebucket/
```

### FireBucketLinkDevice

路径：

```text
product/firebucket/model/FireBucketRuntime.kt
```

作用：

- 消防吊桶 Link 设备的运行时模型。
- 包含 Link 自己的信息和子设备列表。

为什么放在产品包：

- 这是消防吊桶自己的协议模型。
- 光伏清洗不应该复用它。

### Switch

路径：

```text
product/firebucket/model/Switch.kt
```

作用：

- 消防吊桶下的子设备模型。
- 包含角度、电流、电压、舵机范围、在线状态等。

为什么字段多：

- 控制页和悬浮窗需要展示/控制这些状态。
- 字段本身是业务信息，不能简单砍掉。

当前优化：

- MQTT 解析里已经用 `json.toSwitch()` 集中创建。
- 后面字段变动，只改解析函数，不用到处改 `Switch(...)`。

### FireBucketLinkRepository

路径：

```text
product/firebucket/repository/FireBucketLinkRepository.kt
```

作用：

- 保存消防吊桶 Link 和子设备运行时状态。
- MQTT 入站消息会更新它。
- UI 和 runtime controller 从它读取状态。

为什么需要：

- MQTT 是异步到达的。
- UI 需要一个稳定的状态源。

### FireBucketRuntimeController

路径：

```text
product/firebucket/runtime/FireBucketRuntimeController.kt
```

作用：

- 把消防吊桶自己的 `FireBucketLinkDevice` 转成公共层可读的 `ProductDeviceRuntimeSnapshot`。

为什么需要：

- 首页只需要 `deviceId`、名称、在线状态、子设备数量。
- 首页不应该直接读 `FireBucketLinkDevice`。

### FireBucketMqttInbound

路径：

```text
product/firebucket/mqtt/FireBucketMqttInbound.kt
```

作用：

- 解析消防吊桶 MQTT 入站消息。
- 处理 `LinkDeviceStartup`、`LinkDeviceHeartbeat`、`SubDeviceAdded`、`SubDeviceStatusChanged` 等事件。
- 更新 `FireBucketLinkRepository`。

为什么需要：

- 消防吊桶协议和光伏清洗协议完全不同。
- 解析逻辑必须放在消防吊桶自己的包里。

### FireBucketControlScreen

路径：

```text
product/firebucket/ui/control/FireBucketControlScreen.kt
```

作用：

- 消防吊桶详情控制页。
- 承接原来水桶控制页面能力。

### FireBucketFloatingPanel

路径：

```text
product/firebucket/ui/floating/FireBucketFloatingPanel.kt
```

作用：

- 消防吊桶悬浮窗展开态控制面板。

为什么独立：

- 悬浮窗外壳是公共的。
- 展开后的控制内容是产品自己的。

---

## 8. 光伏清洗产品层

路径：

```text
app/src/main/java/com/tji/device/product/solarclean/
```

### SolarCleanDeviceState

路径：

```text
product/solarclean/model/SolarCleanRuntime.kt
```

作用：

- 光伏清洗自己的设备状态模型。

为什么不能用 Switch：

- 光伏清洗的状态、命令、业务语义和消防吊桶不一样。
- 如果复用 `Switch`，短期看省事，长期会让两个产品互相污染。

### SolarCleanCommand

路径：

```text
product/solarclean/model/SolarCleanRuntime.kt
```

作用：

- 光伏清洗自己的控制命令模型。
- 比如泵、喷洒角度、路线下载、路线执行等。

### SolarCleanRuntimeController

路径：

```text
product/solarclean/runtime/SolarCleanRuntimeController.kt
```

作用：

- 光伏清洗运行时控制器。
- 从 `SolarCleanRepository` 读取光伏清洗状态。
- 把 `SolarCleanDeviceState` 转成首页/产品页可用的 `ProductDeviceRuntimeSnapshot`。

为什么需要：

- 这样 `MainViewModel` 不需要知道光伏清洗以后怎么存状态。

### SolarCleanRepository

路径：

```text
product/solarclean/repository/SolarCleanRepository.kt
```

作用：

- 保存光伏清洗设备运行时状态。
- `SolarCleanMqttInbound` 解析到 `state`、`ack`、`event` 后更新它。
- `SolarCleanRuntimeController` 从它读取状态并输出通用设备快照。

为什么需要：

- 光伏清洗不能借用消防吊桶的 `FireBucketLinkRepository`。
- 每个产品都要有自己的状态源，否则后面协议和 UI 会互相污染。

### SolarCleanMqttInbound

路径：

```text
product/solarclean/mqtt/SolarCleanMqttInbound.kt
```

作用：

- 解析光伏清洗 MQTT 入站消息。
- 当前先按文档语义做解析骨架，后续接真实 Repository。

### SolarCleanControlScreen

路径：

```text
product/solarclean/ui/control/SolarCleanControlScreen.kt
```

作用：

- 光伏清洗详情页。
- 当前是空页面/骨架。

### SolarCleanFloatingPanel

路径：

```text
product/solarclean/ui/floating/SolarCleanFloatingPanel.kt
```

作用：

- 光伏清洗悬浮窗展开态内容。
- 当前是独立骨架，不复用消防吊桶控制面板。

---

## 9. UI 层

路径：

```text
app/src/main/java/com/tji/device/ui/
```

### MainActivity

作用：

- App 入口。
- 创建 `MainViewModel`。
- 提供 `LocalMainViewModel` 给 Compose 页面使用。

### LoginWidget

作用：

- 登录页面 UI。
- 调用 `MainViewModel.login(...)`。

### MainScreen

作用：

- 登录后的主界面。
- 管理三层页面：

```text
平台首页
  -> 产品页
  -> 设备详情页
```

它应该知道：

- 有哪些产品。
- 账号下有哪些设备。
- 当前选择了哪个产品、哪个设备。

它不应该知道：

- 消防吊桶 MQTT JSON 字段怎么解析。
- 光伏清洗命令怎么下发。
- 某个产品内部 Repository 怎么更新。

### FloatingWindowService

作用：

- Android 悬浮窗服务。
- 创建悬浮窗 Compose 内容。

注意：

- 它不负责 MQTT 订阅。
- 它只展示当前产品的快捷控制，并调用产品自己的快捷控制接口。

### FloatingWindowContent

作用：

- 悬浮窗 UI 外壳。
- 根据当前产品类型显示不同产品的 panel。

---

## 10. DI / 装配层

路径：

```text
app/src/main/java/com/tji/device/di/
```

### AppContainer

作用：

- 手写依赖装配。
- 创建 Repository、MQTT 入站解析类、`MqttEventHandler`、`MqttSubscriptionManager`、`ProductRuntimeRegistry`、ViewModelFactory。

为什么需要：

- 项目目前没有接 Hilt/Koin。
- 用一个集中位置装配依赖，方便看清对象关系。

新增产品时通常要改这里：

- 注册产品 MQTT inbound。
- 注册产品 runtime controller。
- 如果产品有悬浮窗快捷控制，也在这里注册。

### MainViewModelFactory

作用：

- 创建 `MainViewModel` 和 `LoginViewModel`。
- 给 `MainViewModel` 注入 `ProductRuntimeRegistry` 和 `MqttSubscriptionManager`。

为什么需要：

- `MainViewModel` 有构造参数，不能直接无参创建。

---

## 11. 为什么不把所有设备都做成一个通用 Device

可以有通用壳层，比如：

```text
BoundAccountDevice
ProductDeviceRuntimeSnapshot
```

但不能把所有产品状态都塞进一个万能 `Device`。

原因：

- 消防吊桶有 Link、Switch、角度、电流、电压。
- 光伏清洗可能有泵、喷洒角度、航线、下载进度、执行槽位。
- 后续产品还会有别的字段。

如果强行做万能模型，最后会变成：

```kotlin
data class Device(
    val bucketAngle: Double?,
    val pumpStatus: Boolean?,
    val routeProgress: Int?,
    val xxx: String?,
    ...
)
```

这种模型看起来统一，实际最难维护。

所以现在采用：

```text
公共层：只放通用壳
产品层：放产品自己的真实模型
```

---

## 12. 新增产品时怎么做

假设新增产品 `Acme`。

### 第一步：公共产品定义

修改：

```text
data/model/Product.kt
```

增加：

```kotlin
ProductType.Acme
ProductDefinition(...)
ProductCatalog.inferType(...)
```

### 第二步：创建产品包

新增：

```text
product/acme/model/
product/acme/mqtt/
product/acme/runtime/
product/acme/ui/control/
product/acme/ui/floating/
```

### 第三步：定义产品自己的模型

比如：

```text
AcmeDeviceState
AcmeCommand
AcmeAck
AcmeEvent
```

不要复用消防吊桶的 `Switch`。

### 第四步：实现 MQTT 入站解析

新增：

```text
AcmeMqttInbound
AcmeMqttTopics
```

### 第五步：实现 RuntimeController

新增：

```text
AcmeRuntimeController
```

把产品自己的状态转成：

```text
ProductDeviceRuntimeSnapshot
```

### 第六步：接入 AppContainer

在 `AppContainer` 注册：

- `AcmeMqttInbound`
- `AcmeRuntimeController`
- 可选：`AcmeFloatingQuickControl`

### 第七步：接入 UI 分流

在主界面和悬浮窗中增加产品分支：

```text
AcmeControlScreen
AcmeFloatingPanel
```

---

## 13. 当前还需要优化的点

### 1. 后端应该返回稳定产品字段

现在前端还在用 `ProductCatalog.inferType(...)` 根据名字推断产品。

更好的方式：

```json
{
  "serialNumber": "xxx",
  "name": "xxx",
  "productType": "FireBucket"
}
```

或者：

```json
{
  "productId": 2,
  "productCode": "fire_bucket"
}
```

### 2. SolarClean 需要补完整控制 UI

现在 `SolarCleanRepository`、`SolarCleanRuntimeController`、`SolarCleanMqttInbound` 已经接起来。

后续应该补：

```text
SolarCleanControlScreen(state)
SolarCleanCommandRepository 或控制下发接口
SolarCleanFloatingPanel 真实控制按钮
```

这样光伏清洗不只是有运行时状态，也能在详情页和悬浮窗里真实控制。

### 3. MqttEventHandler 还可以继续注册表化

现在入站分发还有 `when (productType)`。

后续可以改成：

```text
Map<ProductType, ProductMqttInbound>
```

这样新增产品时少改公共分发代码。

### 4. AppContainer 后续可以换 DI 框架

现在手写 `AppContainer` 能用，也直观。

如果产品继续增多，可以考虑 Hilt/Koin，减少手动装配。

---

## 14. 判断代码该放哪里的规则

| 问题 | 放哪里 |
|------|--------|
| 登录、账号信息 | `data/viewmodel/LoginViewModel`、`data/repository/AuthRepository` |
| 产品类型、产品展示名 | `data/model/Product.kt` |
| 账号绑定设备清单 | `data/model/BoundAccountDevice.kt` |
| 首页通用设备快照 | `product/runtime/ProductRuntime.kt` |
| MQTT 订阅管理 | `service/MqttSubscriptionManager.kt` |
| MQTT 消息分发 | `service/MqttEventHandler.kt` |
| 消防吊桶模型 | `product/firebucket/model/` |
| 消防吊桶 MQTT 解析 | `product/firebucket/mqtt/` |
| 消防吊桶控制 UI | `product/firebucket/ui/` |
| 光伏清洗模型 | `product/solarclean/model/` |
| 光伏清洗 MQTT 解析 | `product/solarclean/mqtt/` |
| 光伏清洗控制 UI | `product/solarclean/ui/` |
| 依赖装配 | `di/AppContainer.kt` |

---

## 15. 最重要的边界

后面写代码时记住这几条：

1. `MainViewModel` 不能直接依赖某个产品模型。
2. 公共 `data/model` 不能放 `Switch`、航线、泵状态这类产品私有字段。
3. 首页可以展示 `ProductDeviceRuntimeSnapshot`，但不要解析 `payload`。
4. 产品 MQTT JSON 解析必须放到对应产品包里。
5. 新产品要有自己的 model、mqtt、runtime、ui，不要复用消防吊桶的数据结构。

这套边界守住了，这个 App 才能从“水桶 App”真正变成“多产品设备 App”。
