# 服务器接口待办

更新时间：2026-08-11

## 状态

- 状态：active
- 说明：当前需要服务器配合的接口和协议待办，App 侧不单边改变这些约定。

本文只记录需要服务器配合才能彻底解决的问题。App 侧不要单边改协议，避免和现有后台、单片机联调断开。

## 1. 登录接口安全

现状：

- App 当前按历史接口使用 `GET /userManager/user/login?account=...&password=...`。
- 密码出现在 URL query 中，容易进入代理、网关、客户端日志、抓包工具历史记录。
- 2026-08-11 使用假账号做兼容探测：HTTPS GET 返回现有标准认证失败 JSON；同一路径的 POST form 和 POST JSON 都只返回“重定向到登录”，没有执行现有 App 认证。
- App Release 不记录完整登录 URL，HTTP 日志按构建类型关闭/脱敏；本地记住密码只允许加密存储，初始化失败时不保存。

建议：

- 改为 `POST /userManager/user/login`。
- 账号密码放在 HTTPS request body。
- 返回结构保持多产品设备列表，但字段命名需要稳定。
- 服务端在正式启用 POST 前需与 App 做成功、失败、token、全部产品绑定结构回归；客户端不能单边切换，否则现有客户无法登录。

## 2. 登录返回的绑定设备结构

现状：

- 消防吊桶仍有 `bucketsns: ["linkSn,linkName"]`。
- 光伏清洗已开始返回 `cleansns: [{ id, sn1, productName }]`。
- 不同产品字段形态不一致，App 需要兼容旧字符串和新对象。

建议统一返回：

```json
{
  "boundDevices": [
    {
      "id": 184,
      "deviceId": "TGIOSBBIX",
      "name": "光伏清洗 01",
      "productType": "SolarClean"
    }
  ],
  "token": "..."
}
```

约定：

- `id` 是后台绑定设备记录 ID，用于修改设备显示名。
- `deviceId` 是设备正式通信身份，不能被用户修改。
- `name` 是 App 展示名，可以为空；为空时 App 使用 `deviceId` 兜底。
- `productType` 必须由服务器明确返回，App 不再靠名称推断。

## 3. 修改设备名接口

现状：

- `PUT /userManager/user/updatename?id=184&productName=xxx`
- 使用 `token` header 鉴权。

建议：

- 保留 `id` 作为主键没有问题。
- 建议参数名从 `productName` 调整为 `deviceName` 或 `name`，避免误解为“产品名称”。
- 建议服务器校验该 `id` 是否属于当前 token 用户。
- 如果不同产品表可能出现 ID 重叠，接口需要同时校验产品类型，或保证绑定表 ID 全局唯一。

## 4. Token Header 规范

现状：

- App 为了兼容，同时发送 `Authorization: Bearer <token>` 和 `token: <token>`。

建议：

- 服务器统一一种鉴权头。
- 如果后台继续使用 `token` header，文档中明确写死。
- 如果改为标准 `Authorization`，需要所有接口同步支持。

## 5. OTA / 版本接口

现状：

- App 使用 `GET /api/data/appversion/getAppVersion?productId={productId}&type={type}`。
- `type=1` 表示 App 更新包，`type=2` 表示设备固件更新包。
- `type=1` 当前 productId：`1=水枪控制`，`2=水桶控制`。
- `type=2` 当前 productId：`3=光伏清洗`，`4=消防吊桶`。
- 光伏清洗固件检查使用 `productId=3&type=2`。
- App 本地用设备上报的内部版本 `inner_version` 和服务器返回 `innerVersion` 做对比。

建议：

- 服务器按 `productId + type` 返回当前启用的最新版本包。
- 不需要 App 上报当前固件版本来查最新版本。
- `version` 是展示版本，`innerVersion` 是升级判断版本；正式判断以 `innerVersion` 为准。
- `path` 如果返回相对路径，App 会按 API 域名补全后下发给单片机。
- 固件更新必须返回 `fileSize` 和 `sha256`，App 会下发为 MQTT `file_size` 和 `sha256`。
- 如果未来同一产品有多硬件型号，再增加硬件兼容字段，但不要让 App 查询参数过早复杂化。

## 6. 上线前接口检查清单

- 登录接口不再通过 URL query 传密码。
- 登录返回每台设备都有稳定 `id/deviceId/name/productType`。
- 修改设备名接口字段名不再叫 `productName`，或至少文档明确它实际是设备显示名。
- token 鉴权头统一。
- OTA / 版本接口返回字段稳定：`version`、`path`、`productName`、`techDesc`、`innerVersion`、`publishDate`、`type`、`fileSize`、`sha256`。

## 7. MQTT TLS listener

现状：

- 平台与无线电兼容 Broker 的 1883 均可连接。
- 两个 1883 端口对无用户名/密码的 MQTT 3.1.1 CONNECT 返回成功，当前允许公网匿名连接。
- 两个 8883 端口能建立 TCP，但 2026-08-11 使用系统信任库进行标准 TLS handshake 和明文 MQTT CONNECT 均超时，未提供可用 MQTT listener。
- App 已支持 `TJI_MQTT_TLS_ENABLED` 和 `TJI_RADIO_LEGACY_MQTT_TLS_ENABLED`，启用后使用系统信任库和标准主机名校验；当前为保持现有设备业务可用，默认仍关闭。

服务端待办：

- 为两个 Broker 配置真实 TLS listener；
- 分配稳定 DNS 名称并部署公有 CA 证书，证书 SAN 必须覆盖连接主机名；
- 保持现有 topic、QoS、retain、payload 与账号 ACL 不变；
- 禁止匿名访问，并核对 platform/radio profile 只允许各自所需的订阅与发布 topic；
- 联调验证后将 host/port/TLS 开关作为同一个 Release 配置变更，不允许只打开开关；
- 验证成功前不得关闭 1883，避免现有 MCU 与旧 App 同时离线。

## 8. 固件 OTA 元数据

2026-08-11 现网只读探测发现：部分产品缺下载路径/大小/SHA256，部分产品没有固件记录；全部已返回产品都缺硬件版本、签名和最低电量。消防吊桶与无线电检测的 `innerVersion` 还是不可转整数的语义版本字符串。

服务端待办：

- 每个产品返回稳定的数字 `innerVersion`、`productName`、`type=2`、HTTPS 相对路径、正数文件大小和 64 位 SHA256；
- 增加与设备上报一致的 `hardwareVersion`，不能让一个包无差别用于多个硬件；
- 增加 `minBattery`，空间要求需与 MCU 协议共同定义；
- 只有对应量产 Bootloader 确认验签后才发布 `signature`，同时固化算法、编码和签名覆盖范围；
- 六段抛投、光伏清洗等缺包产品在元数据完整前不应向 App 声明可升级。

## 9. TJI Platform App 更新记录

现网 `type=1` 查询没有 TJI Platform 独立记录：`productId=2` 返回旧“水桶控制”APK，其他未知 productId 还会回退到“水枪控制”。固定 HTTP TJI Platform 地址不可作为量产更新源。

服务端需创建独立且不回退的 TJI Platform productId，并返回：`productName=TJI Platform`、`packageName=com.tji.device`、数字 versionCode、HTTPS 相对路径、文件大小、APK SHA256、Release signer SHA256。错误或未知 productId 必须返回 404/业务无记录，不能返回其他 App。记录创建并联调通过后，把该 ID 作为 Release 配置 `TJI_APP_UPDATE_PRODUCT_ID` 注入；默认 `-1` 会安全关闭更新检查。
