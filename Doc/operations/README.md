# 运维与协作文档

这里放测试、接口待办和需要人工确认的问题。它们不是产品需求本身，但会影响交付质量和协作节奏。

## 当前文档

| 主题 | 文档 |
|------|------|
| 客户端自动化测试 | [client-automation-checklist.md](client-automation-checklist.md) |
| 文档删除与归档规则 | [document-retirement-policy.md](document-retirement-policy.md) |
| 服务器接口待办 | [server-api-backlog.md](server-api-backlog.md) |
| 待人工 Review 问题 | [review-questions.md](review-questions.md) |

## 写作规则

- 已确认能修的问题直接进代码改动和测试。
- 暂时不能修但已确认的问题，后续应同步到 issue。
- 不确定的问题先放 `review-questions.md`，避免凭猜测改业务逻辑。
- 文档结构调整后运行 `./gradlew checkDocs`，确认检查脚本单元测试、路径、模板、产品线索引和旧引用没有回退。
