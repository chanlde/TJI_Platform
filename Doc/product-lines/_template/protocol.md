# {ProductName} 协议说明

## 状态

- 状态：draft
- 适用产品代码：`{productCode}`
- 最近更新：YYYY-MM-DD

## Topic 规则

```text
{ProductPrefix}/devices/{deviceId}/control
{ProductPrefix}/devices/{deviceId}/status
{ProductPrefix}/devices/{deviceId}/lifecycle
```

## Payload 类型

| type | 方向 | 说明 | retain |
|------|------|------|--------|
| command | App -> MCU | 控制命令 | false |
| state | MCU -> App | 周期状态 | true |
| ack | MCU -> App | 命令应答 | false |
| lifecycle | MCU -> App | 上下线或异步事件 | false |

## 命令码

| cmd | cmdName | 说明 | 是否需要 ACK |
|---:|---------|------|---------------|
| 0 | PING | 连通性检查 | 是 |

## 状态 Payload

```json
{
  "type": "state",
  "deviceId": "{deviceId}",
  "online": true
}
```

## ACK 规则

- 成功 ACK：待补充。
- 失败 ACK：待补充。
- 超时策略：待补充。
- seq / requestId 规则：待补充。

## 错误处理

- payload 缺字段：待补充。
- 未知 cmd：待补充。
- retained 旧状态：待补充。

## 代码落点

- MQTT topic：`app/src/main/java/com/tji/device/product/{productCode}/mqtt/`
- inbound 解析：`app/src/main/java/com/tji/device/product/{productCode}/mqtt/`
- 模型：`app/src/main/java/com/tji/device/product/{productCode}/model/`
- repository：`app/src/main/java/com/tji/device/product/{productCode}/repository/`
