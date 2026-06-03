# 无线电检测产品线接入流程

## 当前目标

先把无线电检测做成可运行、可验证的真实产品页。当前优先级：

1. 接入高德 Android 地图 SDK，先让监控页显示真实地图。
2. 在真实地图上叠加模拟目标、飞手、探测范围和预警区。
3. 接入无线电检测设备协议，先按 App 现有 MQTT 架构落地，再对齐最终设备协议。
4. 将模拟数据替换为真实上报数据。

## 代码分层

无线电检测按产品线独立放在：

```text
app/src/main/java/com/tji/device/product/radiodetection/
```

当前分层约定：

| 目录 | 职责 |
| --- | --- |
| `model/` | UI 状态、运行时状态、目标/轨迹/名单/执法等领域模型和临时模拟数据 |
| `map/` | 高德地图 SDK 包装、地图配置、Marker/Circle/Polyline 渲染 |
| `mqtt/` | 无线电检测 MQTT topic 布局，后续放协议入站解析 |
| `ui/control/RadioDetectionControlScreen.kt` | 产品控制页入口，只负责状态协调、顶层弹层和通用控件 |
| `ui/control/RadioDetectionMonitorMap.kt` | 监控地图区域、地图/列表切换控件 |
| `ui/control/RadioDetectionTargetSheet.kt` | 实时目标底部卡、目标列表和目标卡片 |
| `ui/control/RadioDetectionSecondaryPages.kt` | 轨迹、预警区、执法、名单等二级页面 |
| `ui/icon/` | 产品图标 |

后续协议接入时，不允许把 MQTT JSON 解析写进 Compose UI。协议数据流统一按：

```text
MQTT JSON -> mqtt inbound/parser -> repository/runtime -> viewmodel -> RadioDetectionUiState -> UI
```

## 高德 Key 使用规则

当前 App 可以使用高德 Android Key 的前提：

- 高德控制台中该 Key 的服务平台是 Android。
- Key 绑定的包名包含当前 App 包名：`com.tji.device`。
- Key 绑定的 SHA1 是当前调试包或正式包的签名 SHA1。
- 调试包和正式包如果签名不同，需要在高德控制台分别配置对应 SHA1，或拆成两个 Key。

明文 Key 不写进 Kotlin 源码和仓库文档。项目从 `local.properties` 或 Gradle property 读取：

```properties
AMAP_API_KEY=你的高德AndroidKey
```

当前 App 高德 Android Key 建议配置：

| 类型 | 包名 | SHA1 |
| --- | --- | --- |
| 调试版 | `com.tji.device` | `D7:D8:04:BE:F7:A0:80:8C:6E:FE:A3:6B:81:72:A4:D7:5D:25:51:6F` |
| 发布版 | `com.tji.device` | `92:0A:D3:98:01:7C:9E:97:39:47:19:CA:25:CA:55:B6:F9:3C:E2:D2` |

发布版 SHA1 来源：`/Users/wangtianlong/Desktop/key.jks`，alias 为 `key0`。当前项目仍未配置 release signingConfig，后续发版需要把该 keystore 接入 Gradle release 签名配置。

## Step 1：高德地图接入

状态：已完成代码接入，待真机验收。

### 1.1 依赖

使用高德 3D 地图 SDK Maven 包：

```kotlin
implementation("com.amap.api:3dmap:10.0.600")
```

说明：Maven Central 当前可见的 `com.amap.api:3dmap` 最新稳定版本为 `10.0.600`。

### 1.2 Manifest

`AndroidManifest.xml` 在 `application` 节点下配置：

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="${AMAP_API_KEY}" />
```

当前项目已经预留该占位符，`app/build.gradle.kts` 会从 `AMAP_API_KEY` 读取。

### 1.3 隐私合规初始化

高德 SDK 在使用地图能力前需要完成隐私合规声明。接入点放在 `MyApplication.onCreate()`：

```kotlin
MapsInitializer.updatePrivacyShow(this, true, true)
MapsInitializer.updatePrivacyAgree(this, true)
```

后续如果 App 有真实隐私协议弹窗，需要把这里改成“用户同意后再置为 true”。

### 1.4 Compose 地图容器

无线电检测地图层单独放在：

```text
app/src/main/java/com/tji/device/product/radiodetection/map/
```

目标结构：

- `RadioDetectionMapConfig.kt`：地图 provider、中心点、缩放级别、Manifest key 名称。
- `RadioDetectionAmapView.kt`：Compose `AndroidView` 包装 `MapView`，负责生命周期。
- 控制页只使用地图组件，不直接依赖高德 SDK 细节。

### 1.5 第一版验收

第一版只验收地图能显示：

- 无线电检测监控页打开后显示真实高德底图。
- 默认中心点：`37.863209, 116.293095`。
- 默认缩放：`15.5`。
- 不崩溃，不出现隐私合规初始化错误。
- 如果地图空白，优先检查 Key 的包名和 SHA1。

当前代码接入点：

- `app/build.gradle.kts`：加入 `com.amap.api:3dmap:10.0.600`，读取 `AMAP_API_KEY`。
- `app/src/main/AndroidManifest.xml`：写入高德 `meta-data`。
- `app/src/main/java/com/tji/device/MyApplication.kt`：高德隐私合规初始化。
- `app/src/main/java/com/tji/device/product/radiodetection/map/RadioDetectionAmapView.kt`：Compose 包装高德 `MapView`。
- `app/src/main/java/com/tji/device/product/radiodetection/ui/control/RadioDetectionControlScreen.kt`：监控页使用真实地图承载层。

真机验收前置：

- `local.properties` 已在本机配置 `AMAP_API_KEY`。
- 高德控制台需要确认该 Key 绑定 `com.tji.device` 和当前安装包签名 SHA1。

## Step 2：地图业务叠加

状态：已完成模拟数据第一版 Overlay 接入。

第一版真实地图跑通后，再把当前 Compose 原型层迁移到高德 Overlay：

- 目标点：`Marker`，已接入模拟目标经纬度，图标显示“机”。
- 飞手点：`Marker`，已按目标一对一接入模拟飞手经纬度，图标显示“飞”。
- 探测范围：`Circle`，已按 `detectionRange` 绘制。
- 目标到飞手连线：`Polyline`，每组飞机/飞手使用同色图标和同色连线。
- 预警区：`Polygon` / `Circle`。

坐标统一使用高德 GCJ-02 坐标。设备如果上报 WGS-84，需要先转换后再显示。

定位说明：

- 当前 App 启动时已经申请 `ACCESS_FINE_LOCATION`。
- 无线电检测地图已启用高德定位图层；只有用户授权定位后才会显示手机当前位置蓝点。
- 设备/目标位置仍以设备上报或模拟数据为准，不等同于手机定位。

## Step 3：无线电检测协议接入

状态：待开始。

原则：

- MQTT topic 布局优先沿用 App 现有产品架构。
- 字段解析单独放在无线电检测产品线内，不塞进通用 MQTT handler。
- UI 层只消费 `RadioDetectionUiState`，不直接读 MQTT JSON。

建议文件：

```text
app/src/main/java/com/tji/device/product/radiodetection/mqtt/
app/src/main/java/com/tji/device/product/radiodetection/repository/
app/src/main/java/com/tji/device/product/radiodetection/viewmodel/
```

第一版数据流：

```text
MQTT JSON -> RadioDetectionMqttInbound -> Repository/Runtime -> ViewModel -> RadioDetectionUiState -> UI
```

## 参考

- 高德开放平台：https://lbs.amap.com/
- 高德 SDK 合规使用说明：https://lbs.amap.com/news/sdkhgsy
- Maven `com.amap.api:3dmap` 版本列表：https://mvnrepository.com/artifact/com.amap.api/3dmap
