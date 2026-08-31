# 运维与协作文档

这里放测试、接口待办和需要人工确认的问题。它们不是产品需求本身，但会影响交付质量和协作节奏。

## 当前文档

| 主题 | 文档 |
|------|------|
| 客户端自动化测试 | [client-automation-checklist.md](client-automation-checklist.md) |
| 量产优化串行清单 | [production-readiness-checklist.md](production-readiness-checklist.md) |
| MQTT 量产基线 | [mqtt-production-baseline.md](mqtt-production-baseline.md) |
| OTA 与 App 更新量产基线 | [ota-production-baseline.md](ota-production-baseline.md) |
| App 更新模拟器证据 | [app-update-emulator-evidence.md](app-update-emulator-evidence.md) |
| App 更新 MI 8 真机证据 | [app-update-mi8-device-evidence.md](app-update-mi8-device-evidence.md) |
| MI 8 App 压力测试证据 | [app-mi8-stress-evidence.md](app-mi8-stress-evidence.md) |
| 现场诊断与故障处理 | [field-diagnostics-runbook.md](field-diagnostics-runbook.md) |
| OTA/App 目标设备验收记录 | [ota-device-acceptance-record.md](ota-device-acceptance-record.md) |
| 文档删除与归档规则 | [document-retirement-policy.md](document-retirement-policy.md) |
| 服务器接口待办 | [server-api-backlog.md](server-api-backlog.md) |
| 待人工 Review 问题 | [review-questions.md](review-questions.md) |

## 写作规则

- 已确认能修的问题直接进代码改动和测试。
- 暂时不能修但已确认的问题，后续应同步到 issue。
- 不确定的问题先放 `review-questions.md`，避免凭猜测改业务逻辑。
- 文档结构调整后运行 `./gradlew checkDocs`，确认检查脚本单元测试、路径、模板、产品线索引和旧引用没有回退。
