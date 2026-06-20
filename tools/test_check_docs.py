#!/usr/bin/env python3
"""Unit tests for check_docs.py."""

from __future__ import annotations

import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

import check_docs


class CheckDocsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name).resolve()
        self.context = check_docs.DocCheckContext(root=self.root)
        self._create_valid_repo()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_valid_documentation_layout_passes(self) -> None:
        self.assertEqual(check_docs.run_checks(self.context), [])

    def test_cli_root_option_validates_requested_checkout(self) -> None:
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root), "--quiet"])

        self.assertEqual(exit_code, 0)
        self.assertEqual(stdout.getvalue(), "")

    def test_cli_failure_output_groups_issues_by_rule(self) -> None:
        self.write("README.md", "# Root\n\n[Missing](missing.md)\n")
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root)])

        output = stdout.getvalue()
        self.assertEqual(exit_code, 1)
        self.assertIn("[markdown-links]", output)
        self.assertIn("- README.md: broken relative link: missing.md", output)

    def test_cli_list_rules_prints_available_checks(self) -> None:
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--list-rules"])

        output = stdout.getvalue()
        self.assertEqual(exit_code, 0)
        self.assertIn("doc-root-layout:", output)
        self.assertIn("root-readme-docs-entry:", output)
        self.assertIn("local-environment-references:", output)
        self.assertIn("quality-gate-documentation:", output)
        self.assertIn("markdown-links:", output)
        self.assertIn("migration-map:", output)

    def test_cli_list_rules_json_output_is_machine_readable(self) -> None:
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--list-rules", "--format", "json"])

        payload = json.loads(stdout.getvalue())
        self.assertEqual(exit_code, 0)
        self.assertEqual(payload["schemaVersion"], 1)
        self.assertTrue(any(rule["name"] == "markdown-links" for rule in payload["rules"]))
        self.assertTrue(all("description" in rule for rule in payload["rules"]))

    def test_cli_rule_option_runs_only_selected_rule(self) -> None:
        self.write("Doc/features/orphan-flow.md", "# Orphan Flow\n")
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root), "--rule", "markdown-links", "--quiet"])

        self.assertEqual(exit_code, 0)
        self.assertEqual(stdout.getvalue(), "")

    def test_cli_rule_option_reports_selected_rule_failures(self) -> None:
        self.write("README.md", "# Root\n\n[Missing](missing.md)\n")
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root), "--rule", "markdown-links"])

        output = stdout.getvalue()
        self.assertEqual(exit_code, 1)
        self.assertIn("[markdown-links]", output)
        self.assertNotIn("[directory-indexes]", output)

    def test_cli_json_success_output_is_machine_readable(self) -> None:
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root), "--format", "json"])

        payload = json.loads(stdout.getvalue())
        self.assertEqual(exit_code, 0)
        self.assertEqual(payload["schemaVersion"], 1)
        self.assertTrue(payload["ok"])
        self.assertEqual(payload["issueCount"], 0)
        self.assertEqual(payload["issues"], [])
        self.assertIn("markdown-links", payload["rules"])

    def test_cli_json_failure_output_contains_rule_path_and_message(self) -> None:
        self.write("Doc/README.md", "# Docs\n\n[Missing](missing.md)\n")
        stdout = StringIO()

        with redirect_stdout(stdout):
            exit_code = check_docs.main(["--root", str(self.root), "--rule", "markdown-links", "--format", "json"])

        payload = json.loads(stdout.getvalue())
        self.assertEqual(exit_code, 1)
        self.assertEqual(payload["schemaVersion"], 1)
        self.assertFalse(payload["ok"])
        self.assertEqual(payload["issueCount"], 1)
        self.assertEqual(
            payload["issues"][0],
            {
                "rule": "markdown-links",
                "path": "Doc/README.md",
                "message": "broken relative link: missing.md",
            },
        )

    def test_broken_relative_link_is_reported(self) -> None:
        self.write("Doc/README.md", "# Docs\n\n[Missing](missing.md)\n")

        issues = check_docs.check_markdown_links(self.context)

        self.assertTrue(any("broken relative link: missing.md" in issue.message for issue in issues))

    def test_unexpected_top_level_doc_directory_is_reported(self) -> None:
        self.write("Doc/misc/README.md", "# Misc\n")

        issues = check_docs.check_doc_root_layout(self.context)

        self.assertEqual(
            issues,
            [
                check_docs.Issue(
                    self.root / "Doc/misc",
                    "unexpected top-level Doc directory: misc",
                )
            ],
        )

    def test_personal_local_path_in_markdown_is_reported(self) -> None:
        self.write(
            "Doc/features/local-machine-note.md",
            "# Local Machine Note\n\n## 状态\n\n- 状态：draft\n\nKeystore: `/Users/alice/Desktop/key.jks`\n",
        )

        issues = check_docs.check_local_environment_references(self.context)

        self.assertEqual(
            issues,
            [
                check_docs.Issue(
                    self.root / "Doc/features/local-machine-note.md",
                    "personal local path should not be committed: /Users/alice/Desktop/key.jks",
                )
            ],
        )

    def test_non_kebab_markdown_file_name_is_reported(self) -> None:
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/ADR-002-New-Rule.md",
            "# ADR-002: New Rule\n",
        )

        issues = check_docs.check_file_names(self.context)

        self.assertEqual(
            issues,
            [
                check_docs.Issue(
                    self.root / "Doc/architecture/2026-06-docs-reorganization/ADR-002-New-Rule.md",
                    "markdown file name should be kebab-case",
                )
            ],
        )

    def test_missing_directory_readme_is_reported(self) -> None:
        (self.root / "Doc/features/ota").mkdir(parents=True)

        issues = check_docs.check_directory_indexes(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/features/ota/README.md"
                and "must include README.md" in issue.message
                for issue in issues
            )
        )

    def test_missing_top_level_directory_index_link_is_reported(self) -> None:
        self.write("Doc/README.md", "# Docs\n")

        issues = check_docs.check_directory_indexes(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/README.md"
                and "missing top-level directory index link: architecture/README.md" in issue.message
                for issue in issues
            )
        )

    def test_missing_directory_readme_child_link_is_reported(self) -> None:
        self.write("Doc/features/orphan-flow.md", "# Orphan Flow\n\n## 状态\n\n- 状态：draft\n")

        issues = check_docs.check_directory_indexes(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/features/README.md"
                and "missing directory README link: orphan-flow.md" in issue.message
                for issue in issues
            )
        )

    def test_missing_root_readme_docs_entry_is_reported(self) -> None:
        self.write("README.md", "# Root\n")

        issues = check_docs.check_root_readme_docs_entry(self.context)

        self.assertEqual(
            issues,
            [
                check_docs.Issue(
                    self.root / "README.md",
                    "root README must link to Doc/README.md",
                )
            ],
        )

    def test_missing_lifecycle_status_is_reported(self) -> None:
        self.write("Doc/features/new-flow.md", "# New Flow\n")

        issues = check_docs.check_document_lifecycle_status(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/features/new-flow.md"
                and "must include a lifecycle status section" in issue.message
                for issue in issues
            )
        )

    def test_invalid_lifecycle_status_is_reported(self) -> None:
        self.write("Doc/features/new-flow.md", "# New Flow\n\n## 状态\n\n- 状态：maybe\n")

        issues = check_docs.check_document_lifecycle_status(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/features/new-flow.md"
                and "lifecycle status must be active, draft, or deprecated" in issue.message
                for issue in issues
            )
        )

    def test_deprecated_document_without_notes_is_reported(self) -> None:
        self.write("Doc/features/new-flow.md", "# New Flow\n\n## 状态\n\n- 状态：deprecated\n")

        issues = check_docs.check_document_lifecycle_status(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/features/new-flow.md"
                and "deprecated document must include a deprecation notes section" in issue.message
                for issue in issues
            )
        )

    def test_missing_product_docs_for_app_product_directory_is_reported(self) -> None:
        (self.root / "app/src/main/java/com/tji/device/product/speaker").mkdir(parents=True)

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/speaker"
                and "missing product-line docs" in issue.message
                for issue in issues
            )
        )

    def test_missing_product_line_index_entry_is_reported(self) -> None:
        self.write("Doc/product-lines/README.md", "# 产品线文档\n")

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any("missing product-line index entry: firebucket/README.md" in issue.message for issue in issues)
        )

    def test_missing_reserved_product_catalog_entry_is_reported(self) -> None:
        self.write(
            "Doc/product-lines/README.md",
            "# 产品线文档\n\n| 产品线 | 文档 |\n|--------|------|\n"
            "| 模板 | [_template/README.md](_template/README.md) |\n"
            "| 消防吊桶 | [firebucket/README.md](firebucket/README.md) |\n",
        )

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                "missing reserved ProductCatalog product entry: 探照灯 (Searchlight, product/searchlight)"
                in issue.message
                for issue in issues
            )
        )

    def test_missing_product_line_status_is_reported(self) -> None:
        self.write("Doc/product-lines/firebucket/protocol.md", "# Protocol\n")

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/firebucket/protocol.md"
                and "must include a status section" in issue.message
                for issue in issues
            )
        )

    def test_invalid_product_line_status_is_reported(self) -> None:
        self.write(
            "Doc/product-lines/firebucket/protocol.md",
            "# Protocol\n\n## 状态\n\n- 状态：maybe\n- 产品代码：`firebucket`\n",
        )

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/firebucket/protocol.md"
                and "status must be active, draft, or deprecated" in issue.message
                for issue in issues
            )
        )

    def test_missing_product_code_is_reported(self) -> None:
        self.write("Doc/product-lines/firebucket/protocol.md", "# Protocol\n\n## 状态\n\n- 状态：draft\n")

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/firebucket/protocol.md"
                and "must include product code" in issue.message
                for issue in issues
            )
        )

    def test_mismatched_product_code_is_reported(self) -> None:
        self.write(
            "Doc/product-lines/firebucket/protocol.md",
            "# Protocol\n\n## 状态\n\n- 状态：draft\n- 产品代码：`speaker`\n",
        )

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/firebucket/protocol.md"
                and "product code must match directory name: firebucket" in issue.message
                for issue in issues
            )
        )

    def test_template_placeholder_in_real_product_doc_is_reported(self) -> None:
        self.write(
            "Doc/product-lines/firebucket/protocol.md",
            "# {ProductName} Protocol\n\n## 状态\n\n- 状态：draft\n- 产品代码：`firebucket`\n",
        )

        issues = check_docs.check_product_line_docs(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/product-lines/firebucket/protocol.md"
                and "template placeholder remains" in issue.message
                for issue in issues
            )
        )

    def test_old_doc_path_is_allowed_only_in_migration_map(self) -> None:
        self.write("Doc/README.md", "Legacy path: Doc/ARCHITECTURE.md\n")
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/doc-migration-map.md",
            self.migration_map_content() + "\nLegacy path: Doc/ARCHITECTURE.md\n",
        )

        issues = check_docs.check_old_references(self.context)

        self.assertEqual(len(issues), 1)
        self.assertEqual(issues[0].path, self.root / "Doc/README.md")

    def test_old_doc_path_in_repository_text_file_is_reported(self) -> None:
        self.write(
            "app/src/main/java/com/tji/device/DocLink.kt",
            'const val LEGACY_DOC = "Doc/ARCHITECTURE.md"\n',
        )

        issues = check_docs.check_old_references(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "app/src/main/java/com/tji/device/DocLink.kt"
                and "old Doc path reference: Doc/ARCHITECTURE.md" in issue.message
                for issue in issues
            )
        )

    def test_missing_migration_map_entry_is_reported(self) -> None:
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/doc-migration-map.md",
            self.migration_map_content().replace(
                "| `Doc/ARCHITECTURE.md` | 无 | 测试迁移 |\n",
                "",
            ),
        )

        issues = check_docs.check_migration_map(self.context)

        self.assertTrue(
            any("missing migration entry: Doc/ARCHITECTURE.md" in issue.message for issue in issues)
        )

    def test_missing_migration_target_is_reported(self) -> None:
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/doc-migration-map.md",
            "| 旧路径 | 新路径 | 处理 |\n"
            "|--------|--------|------|\n"
            "| `Doc/ARCHITECTURE.md` | `Doc/architecture/missing.md` | 测试迁移 |\n"
            + self.migration_map_content(exclude={"Doc/ARCHITECTURE.md"}),
        )

        issues = check_docs.check_migration_map(self.context)

        self.assertTrue(
            any("migration target does not exist: Doc/architecture/missing.md" in issue.message for issue in issues)
        )

    def test_missing_quality_gate_rule_documentation_is_reported(self) -> None:
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/quality-gates.md",
            self.quality_gates_content(exclude={"markdown-links"}),
        )

        issues = check_docs.check_quality_gate_documentation(self.context)

        self.assertTrue(
            any("quality gate documentation missing rule: markdown-links" in issue.message for issue in issues)
        )

    def test_unknown_quality_gate_rule_documentation_is_reported(self) -> None:
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/quality-gates.md",
            self.quality_gates_content(extra={"retired-rule"}),
        )

        issues = check_docs.check_quality_gate_documentation(self.context)

        self.assertTrue(
            any("quality gate documentation references unknown rule: retired-rule" in issue.message for issue in issues)
        )

    def test_missing_quality_gates_link_from_doc_readme_is_reported(self) -> None:
        self.write(
            "Doc/README.md",
            "\n".join(
                (
                    "# Docs",
                    "",
                    "- [Architecture](architecture/README.md)",
                    "- [Features](features/README.md)",
                    "- [Operations](operations/README.md)",
                    "- [Product Lines](product-lines/README.md)",
                    "- [Protocols](protocols/README.md)",
                    "",
                )
            ),
        )

        issues = check_docs.check_quality_gate_documentation(self.context)

        self.assertTrue(
            any(
                issue.path == self.root / "Doc/README.md"
                and "Doc README must link to quality gates" in issue.message
                for issue in issues
            )
        )

    def _create_valid_repo(self) -> None:
        self.write("README.md", "# Root\n\nDocs: [Doc/README.md](Doc/README.md)\n")
        self.write(
            "Doc/README.md",
            "\n".join(
                (
                    "# Docs",
                    "",
                    "- [Architecture](architecture/README.md)",
                    "- [Features](features/README.md)",
                    "- [Operations](operations/README.md)",
                    "- [Product Lines](product-lines/README.md)",
                    "- [Protocols](protocols/README.md)",
                    "- [Quality Gates](architecture/2026-06-docs-reorganization/quality-gates.md)",
                    "",
                )
            ),
        )
        self.write("Doc/architecture/README.md", "# Architecture\n\n- [Round](2026-06-docs-reorganization/README.md)\n")
        self.write("Doc/features/README.md", "# Features\n\n- [New Flow](new-flow.md)\n")
        self.write("Doc/features/new-flow.md", "# New Flow\n\n## 状态\n\n- 状态：draft\n")
        self.write("Doc/operations/README.md", "# Operations\n")
        self.write("Doc/protocols/README.md", "# Protocols\n")
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/doc-migration-map.md",
            self.migration_map_content(),
        )
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/README.md",
            "# Migration Round\n\n- [Migration](doc-migration-map.md)\n- [Quality Gates](quality-gates.md)\n",
        )
        self.write(
            "Doc/architecture/2026-06-docs-reorganization/quality-gates.md",
            self.quality_gates_content(),
        )
        self.write(
            "Doc/product-lines/README.md",
            "# 产品线文档\n\n| 产品线 | 文档 |\n|--------|------|\n"
            "| 模板 | [_template/README.md](_template/README.md) |\n"
            "| 消防吊桶 | [firebucket/README.md](firebucket/README.md) |\n"
            "\n"
            "## 已在 ProductCatalog 登记但暂未建独立代码目录\n\n"
            "| 产品线 | ProductCatalog productCode | 计划代码目录 | 当前状态 |\n"
            "|--------|----------------------------|--------------|----------|\n"
            "| 探照灯 | `Searchlight` | `product/searchlight` | 已预留 |\n",
        )
        self.write(
            "app/src/main/java/com/tji/device/data/model/Product.kt",
            """
package com.tji.device.data.model

enum class ProductType {
    FireBucket,
    Searchlight
}

object ProductCatalog {
    val definitions = listOf(
        ProductDefinition(
            type = ProductType.FireBucket,
            productId = 2,
            productCode = "FireBucket",
            displayName = "消防吊桶",
            shortLabel = "FireBucket",
            description = "消防吊桶控制产品线",
            platformSubtitle = "无人机消防吊桶系统",
            platformValueLine = "高效灭火"
        ),
        ProductDefinition(
            type = ProductType.Searchlight,
            productId = 8,
            productCode = "Searchlight",
            displayName = "探照灯",
            shortLabel = "Searchlight",
            description = "无人机探照灯产品线",
            platformSubtitle = "无人机照明搜索系统",
            platformValueLine = "强光照明"
        )
    )
}
""",
        )

        for directory in (
            "Doc/product-lines/_template",
            "Doc/product-lines/firebucket",
        ):
            for name in check_docs.REQUIRED_PRODUCT_FILES:
                product_code = "{productCode}" if directory.endswith("_template") else "firebucket"
                child_links = ""
                if name == "README.md":
                    child_links = (
                        "\n- [Protocol](protocol.md)\n"
                        "- [MCU](mcu.md)\n"
                        "- [Server](server.md)\n"
                        "- [App](app.md)\n"
                    )
                self.write(
                    f"{directory}/{name}",
                    f"# {name}\n\n## 状态\n\n- 状态：draft\n- 产品代码：`{product_code}`\n{child_links}",
                )

        for product_dir in (
            "firebucket",
            "ota",
            "runtime",
        ):
            (self.root / f"app/src/main/java/com/tji/device/product/{product_dir}").mkdir(parents=True)

    def migration_map_content(self, exclude: set[str] | None = None) -> str:
        excluded = exclude or set()
        rows = [
            "| 旧路径 | 新路径 | 处理 |",
            "|--------|--------|------|",
        ]
        rows.extend(
            f"| `{old_reference}` | 无 | 测试迁移 |"
            for old_reference in check_docs.OLD_DOC_REFERENCES
            if old_reference not in excluded and not old_reference.endswith("/")
        )
        return "\n".join(rows) + "\n"

    def quality_gates_content(self, exclude: set[str] | None = None, extra: set[str] | None = None) -> str:
        excluded = exclude or set()
        rule_names = [
            rule
            for rule, _description, _check in check_docs.CHECK_RULES
            if rule not in excluded
        ]
        rule_names.extend(sorted(extra or set()))
        rows = [
            "# Quality Gates",
            "",
            "## 状态",
            "",
            "- 状态：active",
            "",
            "| 规则 | 保护点 | 常见修复方式 |",
            "|------|--------|--------------|",
        ]
        rows.extend(
            f"| `{rule}` | 测试规则 | 测试修复 |"
            for rule in rule_names
        )
        return "\n".join(rows) + "\n"

    def write(self, relative_path: str, content: str) -> None:
        path = self.root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
