# 文档质量门

## 状态

- 状态：active

## 目标

`tools/check_docs.py` 是本轮文档架构整理的质量门。它不是为了限制写文档，而是为了防止 `Doc/` 再次变成平铺目录、断链集合或只有少数人知道怎么维护的知识仓库。

## 规则分组

| 规则 | 保护点 | 常见修复方式 |
|------|--------|--------------|
| `doc-root-layout` | `Doc/` 根目录只保留统一入口和批准的顶层分类 | 新文档移动到 `architecture/`、`features/`、`protocols/`、`product-lines/` 或 `operations/`；新增顶层分类前先更新规则和说明 |
| `root-readme-docs-entry` | 仓库首页能跳到文档入口 | 在根 `README.md` 保留指向 `Doc/README.md` 的 Markdown 链接 |
| `directory-indexes` | 每个目录都有可浏览索引 | 给目录补 `README.md`，并在 README 中链接同层文档和子目录 |
| `product-line-docs` | 产品代码和产品线文档同步演进 | 从 `_template/` 复制五件套，填写状态、产品代码和产品线索引 |
| `file-names` | 文件名可跨系统检索和链接 | 使用小写英文 kebab-case；ADR 也使用 `adr-xxx-*.md` |
| `document-lifecycle-status` | 业务文档有生命周期状态 | 给正文文档补 `## 状态`，废弃文档补 `## 废弃说明` |
| `markdown-links` | 相对链接不失效、不跳出仓库 | 修正目标路径，或先创建缺失文档再链接 |
| `local-environment-references` | 文档不绑定个人电脑环境 | 删除个人主目录绝对路径，改成通用配置说明 |
| `old-doc-references` | 旧路径不会继续扩散 | 改成迁移后的新路径；只有迁移表和校验器测试可保留旧路径 |
| `migration-map` | 迁移关系可追溯 | 每个旧文档路径都写入迁移表，目标路径必须存在 |
| `system-files` | 系统临时文件不进入文档树 | 删除 `.DS_Store` 等无业务价值文件 |
| `quality-gate-documentation` | 质量门说明不落后于脚本 | 新增、删除或重命名规则时，同步更新本文档 |

## 修改规则的原则

- 先确认规则保护的维护成本是真实存在的，再把它加入脚本。
- 新规则必须有单元测试，至少覆盖一个失败路径。
- 新规则如果影响写作方式，要同步更新 `Doc/README.md` 或对应目录 README。
- 新规则必须同步补充到本文档，`quality-gate-documentation` 会检查规则表是否和脚本一致。
- 规则描述面向维护者，不写成内部实现细节。
- 如果某类文档确实需要例外，优先缩小例外范围，不把整条规则关掉。

## 本地验证

常规验证入口：

```bash
./gradlew checkDocs
```

只看规则列表：

```bash
python3 tools/check_docs.py --list-rules
```

只跑某一条规则：

```bash
python3 tools/check_docs.py --rule markdown-links
```

给 CI 或脚本解析：

```bash
python3 tools/check_docs.py --format json
```

JSON 输出包含 `schemaVersion`，脚本集成时应先检查该字段再解析 `issues`。
