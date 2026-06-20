# 消防吊桶协议说明

## 状态

- 状态：active
- 产品代码：`firebucket`

## Topic 规则

消防吊桶沿用产品私有 topic 布局，实际 topic 以 `FireBucketMqttTopics.kt` 为准。

代码落点：

```text
app/src/main/java/com/tji/device/product/firebucket/mqtt/FireBucketMqttTopics.kt
app/src/main/java/com/tji/device/product/firebucket/mqtt/FireBucketMqttInbound.kt
```

## Payload 语义

- Link 设备和桶设备运行时状态放在消防吊桶私有模型内。
- 控制命令由 `FireBucketSwitchRepository` / `SwitchRepo` 负责发布。
- 入站 MQTT 由 `FireBucketMqttInbound` 解析，不进入通用 MQTT handler 之外的 UI 层。

## ACK 规则

- App 需要展示控制成功、失败、超时反馈。
- ACK 的最终字段以当前 MCU 协议和代码解析为准。
- 后续如调整字段名，必须同步更新 `FireBucketMqttInbound` 和单元测试。

## 架构约束

- 消防吊桶的 Link / Switch 不能作为新产品通用模型。
- 其他产品不能写入 `FireBucketLinkRepository`。
- 首页只消费产品运行时快照，不读取消防吊桶私有 payload。
