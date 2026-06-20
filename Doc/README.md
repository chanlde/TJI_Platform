# TJI Platform 文档入口

本目录按用途拆分文档，避免所有说明平铺在同一个 `Doc` 根目录。新增文档优先放入对应领域目录；如果是某一轮架构整理的过程文档，放入 `architecture/{yyyy-mm-topic}/`。

## 目录结构

| 目录 | 用途 |
|------|------|
| [architecture/](architecture/README.md) | 平台架构、类职责、产品线接入规范、架构决策记录 |
| [features/](features/README.md) | 功能方案和 UI/UX 优化方案 |
| [features/ota/](features/ota/README.md) | OTA 专项方案 |
| [protocols/](protocols/README.md) | 跨产品或平台级协议说明 |
| [product-lines/](product-lines/README.md) | 单个产品线自己的需求、协议和接入说明 |
| [operations/](operations/README.md) | 测试、接口待办、人工 review 清单 |

## 常用入口

| 主题 | 文档 |
|------|------|
| 平台总架构 | [architecture/platform-architecture.md](architecture/platform-architecture.md) |
| 类职责说明 | [architecture/class-responsibility-guide.md](architecture/class-responsibility-guide.md) |
| 新产品线接入 | [architecture/product-line-onboarding.md](architecture/product-line-onboarding.md) |
| 本轮文档架构优化 | [architecture/2026-06-docs-reorganization/README.md](architecture/2026-06-docs-reorganization/README.md) |
| 文档质量门说明 | [architecture/2026-06-docs-reorganization/quality-gates.md](architecture/2026-06-docs-reorganization/quality-gates.md) |
| 光伏清洗 MQTT 协议 | [protocols/solar-clean-mqtt-protocol.md](protocols/solar-clean-mqtt-protocol.md) |
| 光伏清洗 OTA 总方案 | [features/ota/solar-clean-ota-plan.md](features/ota/solar-clean-ota-plan.md) |
| OTA 第一版职责与协议 | [features/ota/ota-v1-responsibility-contract.md](features/ota/ota-v1-responsibility-contract.md) |
| 产品线文档模板 | [product-lines/_template/README.md](product-lines/_template/README.md) |
| 设备绑定流程 | [features/device-binding.md](features/device-binding.md) |
| App UI/UX 优化 | [features/app-ui-ux-optimization.md](features/app-ui-ux-optimization.md) |
| 悬浮窗 UI 优化 | [features/floating-window-ui-optimization.md](features/floating-window-ui-optimization.md) |
| 喊话器 Payload UI 重做 | [features/speaker-payload-ui-redesign.md](features/speaker-payload-ui-redesign.md) |
| 喊话器 C++ Core 与 Qt 上位机 | [features/speaker-cpp-core-qt-plan.md](features/speaker-cpp-core-qt-plan.md) |
| 客户端自动化测试 | [operations/client-automation-checklist.md](operations/client-automation-checklist.md) |
| 文档删除与归档规则 | [operations/document-retirement-policy.md](operations/document-retirement-policy.md) |
| 服务器接口待办 | [operations/server-api-backlog.md](operations/server-api-backlog.md) |
| 待人工 Review 问题 | [operations/review-questions.md](operations/review-questions.md) |

## 命名规则

- 文件名使用英文 kebab-case，便于跨系统检索和链接。
- 文档标题继续使用中文，保证业务沟通效率。
- `Doc/` 顶层只放 `architecture/`、`features/`、`protocols/`、`product-lines/`、`operations/` 和 `README.md`；新增顶层分类前先更新质量门规则和说明。
- 产品线文档放在 `product-lines/{productCode}/`。
- 新产品线从 `product-lines/_template/` 复制，保持 README、protocol、mcu、server、app 五类文档齐全。
- 架构决策使用 ADR，放在对应架构优化轮次目录，文件名使用小写 `adr-xxx-*.md`。
- 业务正文文档必须包含 `## 状态`，状态值只使用 `active`、`draft`、`deprecated`。
- `deprecated` 文档必须补充 `## 废弃说明`，写明替代文档和删除条件。
- 已确认废弃的文档可以删除；不确定的先移入合适目录并在 `operations/review-questions.md` 记录。

## 本地检查

整理文档后运行：

```bash
./gradlew checkDocs
```

检查内容包括：文档检查脚本单元测试、目录入口、文件命名、生命周期状态、Markdown 相对链接、个人本机路径、产品线五件套、旧路径残留、迁移表和质量门说明同步。完整规则见 [文档质量门说明](architecture/2026-06-docs-reorganization/quality-gates.md)。

如果只想直接运行脚本：

```bash
python3 tools/check_docs.py
```

失败时脚本会按规则名分组输出，例如 `[markdown-links]`、`[product-line-docs]`、`[migration-map]`，便于快速定位是哪一类文档治理规则没有满足。

查看当前所有规则：

```bash
python3 tools/check_docs.py --list-rules
```

只运行某一类规则：

```bash
python3 tools/check_docs.py --rule markdown-links
```

输出 JSON，供 CI 或脚本解析：

```bash
python3 tools/check_docs.py --format json
```

JSON 输出包含 `schemaVersion`，外部脚本应先检查版本再解析问题列表。

也可以组合规则过滤和 JSON 输出：

```bash
python3 tools/check_docs.py --rule markdown-links --format json
```

也可以检查其他 worktree 或临时目录：

```bash
python3 tools/check_docs.py --root /path/to/TJI_Platform
```

如果只想运行文档检查脚本的单元测试：

```bash
python3 -m unittest discover -s tools -p "test_*.py"
```
