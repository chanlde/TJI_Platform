# 架构文档

这里放平台级架构、跨产品规则、类职责说明和架构决策记录。产品自己的细节不要写到这里，放到 `../product-lines/{productCode}/`。

## 推荐阅读顺序

1. [platform-architecture.md](platform-architecture.md)：平台模块边界、运行时、MQTT 和依赖装配。
2. [product-line-onboarding.md](product-line-onboarding.md)：新增产品线的接入规则和检查清单。
3. [class-responsibility-guide.md](class-responsibility-guide.md)：核心类职责和调用关系。
4. [adr-002-session-and-mqtt-state-ownership.md](adr-002-session-and-mqtt-state-ownership.md)：会话状态与 MQTT 真值归属。
5. [adr-003-standalone-speaker-relay-credential.md](adr-003-standalone-speaker-relay-credential.md)：独立喊话 Relay 的部署期凭证。
6. [adr-004-product-control-state-machine-and-lifecycle.md](adr-004-product-control-state-machine-and-lifecycle.md)：产品控制状态机与页面任务生命周期。
7. [2026-06-docs-reorganization/](2026-06-docs-reorganization/README.md)：本轮文档架构优化记录。

## 写作规则

- 平台级决策使用 ADR，放到对应架构优化目录，文件名使用小写 `adr-xxx-*.md`。
- 只记录跨产品共性和稳定边界，不记录单个产品的临时需求。
- 如果文档里的规则已经被代码替代或废弃，要标明状态并链接到新位置。
