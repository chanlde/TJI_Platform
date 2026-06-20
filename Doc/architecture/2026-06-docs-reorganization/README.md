# 2026-06 文档架构优化

## 目标

本轮只优化文档信息架构，不修改业务代码。核心目标是把原来平铺在 `Doc/` 根目录的文档按领域拆开，并建立稳定命名规则，降低后续新增产品线、协议和架构决策时的查找成本。

## 本轮已处理

- 将根目录文档拆分到 `architecture`、`features`、`protocols`、`product-lines`、`operations`。
- 将中文或混合大小写文件名改为英文 kebab-case。
- 新增 `Doc/README.md` 作为统一文档入口。
- 新增本轮架构优化目录，用于保存决策记录和迁移表。
- 新增 `Doc/product-lines/_template/`，统一产品线 README、协议、MCU、服务端和 App 文档骨架。
- 新增各分目录 README，减少从总入口跳转后的二次迷路。
- 新增 `tools/check_docs.py`，用于本地检查文档结构、相对链接、模板完整性、旧路径残留和迁移表目标有效性。
- 为迁移后的业务正文文档补充 `active`、`draft`、`deprecated` 生命周期状态，作为后续删除或归档的依据。
- 删除明确无用的系统文件 `Doc/.DS_Store`。

## 保留原则

除 `Doc/.DS_Store` 外，本轮没有删除业务文档。原因是旧文档虽然命名混乱，但仍覆盖 OTA、绑定设备、服务器接口、产品线接入、UI/UX 和自动化测试等当前仍可能被引用的信息。后续如果确认某份方案已完全过期，再按迁移表删除或归档。

## 新增文档

- [adr-001-document-information-architecture.md](adr-001-document-information-architecture.md)：记录为什么采用当前文档分层。
- [doc-migration-map.md](doc-migration-map.md)：记录旧路径到新路径的迁移关系。
- [quality-gates.md](quality-gates.md)：解释 `checkDocs` 文档质量门、规则意图和维护原则。

## 后续建议

- 新增产品线时，先建 `Doc/product-lines/{productCode}/README.md`。
- 更推荐先复制 `Doc/product-lines/_template/`，再按产品实际情况填写或标记“待定”。
- 新增平台架构决策时，放入 `Doc/architecture/{yyyy-mm-topic}/adr-xxx-*.md`。
- 涉及真实接口或协议的文档，尽量补上状态：`draft`、`active`、`deprecated`。
