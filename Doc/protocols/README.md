# 协议文档

这里放平台级或跨产品协议。单个产品的私有协议优先放到 `../product-lines/{productCode}/protocol.md`。

## 当前文档

| 主题 | 文档 |
|------|------|
| 光伏清洗 App 侧协议落地 | [solar-clean-mqtt-protocol.md](solar-clean-mqtt-protocol.md) |

## 写作规则

- 协议文档要写清 topic、payload、ACK、retain、错误处理和代码落点。
- 如果协议来自服务器、MCU 或外部系统，要标明来源和当前状态。
- 不把 UI 文案、视觉方案和协议细节混在同一份文档里。
