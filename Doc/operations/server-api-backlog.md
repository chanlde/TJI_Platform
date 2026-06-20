# 服务器接口待办

更新时间：2026-05-08

## 状态

- 状态：active
- 说明：当前需要服务器配合的接口和协议待办，App 侧不单边改变这些约定。

本文只记录需要服务器配合才能彻底解决的问题。App 侧不要单边改协议，避免和现有后台、单片机联调断开。

## 1. 登录接口安全

现状：

- App 当前按历史接口使用 `GET /userManager/user/login?account=...&password=...`。
- 密码出现在 URL query 中，容易进入代理、网关、客户端日志、抓包工具历史记录。

建议：

- 改为 `POST /userManager/user/login`。
- 账号密码放在 HTTPS request body。
- 返回结构保持多产品设备列表，但字段命名需要稳定。

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
