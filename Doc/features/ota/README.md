# OTA 文档

本目录保存 OTA 专项方案、职责边界和协议约定。平台级通用协议放在 `Doc/protocols/`，具体产品线的 OTA 差异放在 `Doc/product-lines/{productCode}/`。

## 文档

| 文档 | 用途 |
|------|------|
| [solar-clean-ota-plan.md](solar-clean-ota-plan.md) | 光伏清洗产品 OTA 总方案 |
| [ota-v1-responsibility-contract.md](ota-v1-responsibility-contract.md) | OTA 第一版 App、服务端、MCU 职责与协议 |

## 维护规则

- 新增 OTA 文档先判断是平台通用、产品特定还是执行清单，再放入对应目录。
- 涉及接口或设备协议变更时，同步检查 `Doc/protocols/` 和相关产品线文档。
- 只确认已经废弃的 OTA 方案才标记 deprecated 或删除。
