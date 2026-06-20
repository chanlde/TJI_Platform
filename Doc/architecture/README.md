# 架构文档

这里放平台级架构、跨产品规则、类职责说明和架构决策记录。产品自己的细节不要写到这里，放到 `../product-lines/{productCode}/`。

## 推荐阅读顺序

1. [platform-architecture.md](platform-architecture.md)：平台模块边界、运行时、MQTT 和依赖装配。
2. [product-line-onboarding.md](product-line-onboarding.md)：新增产品线的接入规则和检查清单。
3. [class-responsibility-guide.md](class-responsibility-guide.md)：核心类职责和调用关系。
4. [2026-06-docs-reorganization/](2026-06-docs-reorganization/README.md)：本轮文档架构优化记录。

## 写作规则

- 平台级决策使用 ADR，放到对应架构优化目录，文件名使用小写 `adr-xxx-*.md`。
- 只记录跨产品共性和稳定边界，不记录单个产品的临时需求。
- 如果文档里的规则已经被代码替代或废弃，要标明状态并链接到新位置。
