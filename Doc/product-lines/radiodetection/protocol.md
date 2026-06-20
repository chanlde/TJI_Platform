# 无线电检测协议说明

## 状态

- 状态：draft
- 产品代码：`radiodetection`

## 接入原则

- MQTT topic 布局优先沿用平台现有产品架构。
- 字段解析放在无线电检测产品线内，不塞进通用 MQTT handler。
- UI 层只消费 `RadioDetectionUiState`，不直接读取 MQTT JSON。

## 数据流

```text
MQTT JSON -> RadioDetectionMqttInbound -> Repository/Runtime -> ViewModel -> RadioDetectionUiState -> UI
```

## Topic 规则

最终 topic 仍待设备协议确认。第一版建议使用平台三主题：

```text
RadioDetection/devices/{deviceId}/control
RadioDetection/devices/{deviceId}/status
RadioDetection/devices/{deviceId}/lifecycle
```

## 坐标规则

- 地图显示统一使用高德 GCJ-02 坐标。
- 设备如果上报 WGS-84，需要先转换后再显示。
- 设备/目标位置以设备上报或模拟数据为准，不等同于手机定位。

## 待确认

- RID payload 字段结构。
- 目标、飞手、轨迹、名单、执法记录的 topic 或 payload 分流方式。
- retained state 策略。
- 告警区和黑白名单是否由服务端下发。
