# 无线电检测服务器职责

## 状态

- 状态：draft
- 产品代码：`radiodetection`

## 高德 Key

App 使用高德 Android Key 的前提：

- Key 的服务平台是 Android。
- Key 绑定包名包含 `com.tji.device`。
- Key 绑定 SHA1 覆盖当前调试包和正式包签名。

明文 Key 不写进 Kotlin 源码和仓库文档。项目从 `local.properties` 或 Gradle property 读取：

```properties
AMAP_API_KEY=你的高德AndroidKey
```

## 设备绑定

- 登录返回字段：当前模型已有 `radiodetectionsns`。
- productId / productType：待后端最终确认。
- 是否需要单独设备详情接口：待定。

## MQTT / Broker

- topic 前缀：待最终协议确认。
- retained state 策略：待确认。
- 目标轨迹是否由服务端存储和回放：待确认。

## 上线检查

- 测试账号能返回无线电检测设备。
- 高德 Key 的包名和 SHA1 覆盖 debug / release。
- 地图空白时能快速判断是 Key、签名还是网络问题。
