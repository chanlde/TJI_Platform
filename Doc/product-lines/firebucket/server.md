# 消防吊桶服务器职责

## 状态

- 状态：active
- 产品代码：`firebucket`
- productId：`2`

## 设备绑定

消防吊桶是当前绑定设备流程的主要现网产品之一。现网 App 侧绑定流程见：

```text
Doc/features/device-binding.md
```

当前约束：

- 已有设备记录先在服务端存在。
- App 当前走查询未绑定池并绑定到当前用户。
- `createAndBind` 属于预研协议，线上多数环境尚未部署时不作为客户入口。

## 登录返回

- 服务端需要返回当前账号绑定的消防吊桶设备。
- 字段需能映射到 `BoundAccountDevice` 和 `ProductType.FireBucket`。
- productId 为 `2` 时，App 识别为消防吊桶。

## 上线检查

- 登录接口能返回消防吊桶设备。
- 修改设备名、绑定设备、Token header 等接口和 App 保持一致。
- 设备 `deviceId` 与 MQTT topic 中的设备身份一致。
