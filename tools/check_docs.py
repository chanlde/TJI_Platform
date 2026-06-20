#!/usr/bin/env python3
"""Validate the repository documentation layout.

This intentionally has no third-party dependencies so it can run from a fresh
checkout before Android tooling is installed.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote


@dataclass(frozen=True)
class DocCheckContext:
    root: Path

    @property
    def doc_dir(self) -> Path:
        return self.root / "Doc"

    @property
    def migration_map(self) -> Path:
        return self.doc_dir / "architecture/2026-06-docs-reorganization/doc-migration-map.md"

    @property
    def product_lines_readme(self) -> Path:
        return self.doc_dir / "product-lines/README.md"

    @property
    def doc_readme(self) -> Path:
        return self.doc_dir / "README.md"

    @property
    def root_readme(self) -> Path:
        return self.root / "README.md"

    @property
    def quality_gates(self) -> Path:
        return self.doc_dir / "architecture/2026-06-docs-reorganization/quality-gates.md"

    @property
    def product_catalog(self) -> Path:
        return self.root / "app/src/main/java/com/tji/device/data/model/Product.kt"


DEFAULT_CONTEXT = DocCheckContext(root=Path(__file__).resolve().parents[1])

REQUIRED_PRODUCT_FILES = {
    "README.md",
    "protocol.md",
    "mcu.md",
    "server.md",
    "app.md",
}

ALLOWED_DOC_TOP_LEVEL_DIRECTORIES = {
    "architecture",
    "features",
    "operations",
    "product-lines",
    "protocols",
}

NON_PRODUCT_CODE_DIRS = {
    "ota",
    "runtime",
}

OLD_DOC_REFERENCES = (
    "Doc/ARCHITECTURE.md",
    "Doc/classGuide.md",
    "Doc/addDevice.md",
    "Doc/solarCleanProtocol.md",
    "Doc/products/",
    "Doc/新增产品线接入规范.md",
    "Doc/OTA升级方案.md",
    "Doc/OTA第一版职责与协议.md",
    "Doc/App整体UI_UX优化方案.md",
    "Doc/悬浮窗与App_UI交互优化方案.md",
    "Doc/喊话器Payload_UI重做说明.md",
    "Doc/客户端自动化测试与自迭代清单.md",
    "Doc/服务器接口待办.md",
    "Doc/不确定问题待Review.md",
)

REPOSITORY_TEXT_FILE_SUFFIXES = {
    ".gradle",
    ".java",
    ".json",
    ".kt",
    ".kts",
    ".md",
    ".properties",
    ".py",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
REPOSITORY_TEXT_FILE_NAMES = {
    "README",
}
IGNORED_REPOSITORY_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
}
OLD_REFERENCE_ALLOWED_RELATIVE_PATHS = {
    Path("Doc/architecture/2026-06-docs-reorganization/doc-migration-map.md"),
    Path("tools/check_docs.py"),
    Path("tools/test_check_docs.py"),
}

MARKDOWN_LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
MIGRATION_TABLE_ROW_RE = re.compile(r"^\|\s*`([^`]+)`\s*\|\s*(?:`([^`]+)`|无)\s*\|")
QUALITY_GATE_TABLE_ROW_RE = re.compile(r"^\|\s*`([a-z0-9-]+)`\s*\|")
PRODUCT_CATALOG_DEFINITION_RE = re.compile(
    r"ProductDefinition\(\s*"
    r"type = ProductType\.([A-Za-z0-9_]+),.*?"
    r"productCode = \"([^\"]+)\",.*?"
    r"displayName = \"([^\"]+)\"",
    re.DOTALL,
)
KEBAB_MD_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*\.md$")
PRODUCT_DOC_STATUS_RE = re.compile(r"^- 状态：(active|draft|deprecated)$", re.MULTILINE)
DOC_STATUS_RE = re.compile(r"^- 状态：(active|draft|deprecated)$", re.MULTILINE)
DEPRECATED_STATUS_RE = re.compile(r"^- 状态：deprecated$", re.MULTILINE)
PRODUCT_CODE_RE = re.compile(r"^- (?:适用)?产品代码：`([^`]+)`$", re.MULTILINE)
PERSONAL_PATH_RE = re.compile(r"(/Users/[^\s`)]+|/home/[^\s`)]+|[A-Za-z]:\\Users\\[^\s`)]+)")
REAL_PRODUCT_FORBIDDEN_PLACEHOLDERS = (
    "{ProductName}",
    "{productCode}",
    "{ProductPrefix}",
    "YYYY-MM-DD",
)
LIFECYCLE_STATUS_EXEMPT_DOCS = {
    Path("architecture/2026-06-docs-reorganization/adr-001-document-information-architecture.md"),
    Path("architecture/2026-06-docs-reorganization/doc-migration-map.md"),
}


@dataclass(frozen=True)
class Issue:
    path: Path
    message: str
    rule: str = "docs"

    def format(self, root: Path | None = None) -> str:
        base = root or DEFAULT_CONTEXT.root
        return f"{self.path.relative_to(base)}: {self.message}"


def iter_markdown_files(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Path]:
    files = [context.root / "README.md"]
    files.extend(sorted(context.doc_dir.rglob("*.md")))
    return [path for path in files if path.exists()]


def iter_repository_text_files(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Path]:
    files: list[Path] = []
    for path in sorted(context.root.rglob("*")):
        if not path.is_file():
            continue
        relative_path = path.relative_to(context.root)
        if any(part in IGNORED_REPOSITORY_DIRS for part in relative_path.parts):
            continue
        if path.suffix in REPOSITORY_TEXT_FILE_SUFFIXES or path.name in REPOSITORY_TEXT_FILE_NAMES:
            files.append(path)
    return files


def is_external_link(target: str) -> bool:
    lowered = target.lower()
    return (
        lowered.startswith("http://")
        or lowered.startswith("https://")
        or lowered.startswith("mailto:")
        or lowered.startswith("tel:")
    )


def strip_link_suffix(target: str) -> str:
    target = target.strip()
    if not target or target.startswith("#"):
        return ""
    target = target.split("#", 1)[0]
    target = target.split("?", 1)[0]
    return unquote(target)


def markdown_link_targets(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    targets: set[str] = set()
    for match in MARKDOWN_LINK_RE.finditer(text):
        raw_target = match.group(1)
        if is_external_link(raw_target):
            continue

        target = strip_link_suffix(raw_target)
        if target:
            targets.add(target)
    return targets


def parse_migration_map(path: Path) -> dict[str, str | None]:
    migrations: dict[str, str | None] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = MIGRATION_TABLE_ROW_RE.match(line)
        if not match:
            continue
        old_path, new_path = match.groups()
        migrations[old_path] = new_path
    return migrations


def parse_quality_gate_rules(path: Path) -> set[str]:
    rules: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        match = QUALITY_GATE_TABLE_ROW_RE.match(line)
        if match:
            rules.add(match.group(1))
    return rules


def parse_product_catalog_definitions(path: Path) -> list[tuple[str, str, str]]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    return [
        (product_type, product_code, display_name)
        for product_type, product_code, display_name in PRODUCT_CATALOG_DEFINITION_RE.findall(text)
    ]


def check_markdown_links(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for path in iter_markdown_files(context):
        text = path.read_text(encoding="utf-8")
        for match in MARKDOWN_LINK_RE.finditer(text):
            raw_target = match.group(1)
            if is_external_link(raw_target):
                continue

            target = strip_link_suffix(raw_target)
            if not target:
                continue

            resolved = (path.parent / target).resolve()
            try:
                resolved.relative_to(context.root)
            except ValueError:
                issues.append(Issue(path, f"link escapes repository: {raw_target}"))
                continue

            if not resolved.exists():
                issues.append(Issue(path, f"broken relative link: {raw_target}"))

    return issues


def check_local_environment_references(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for path in iter_markdown_files(context):
        text = path.read_text(encoding="utf-8")
        for match in PERSONAL_PATH_RE.finditer(text):
            issues.append(Issue(path, f"personal local path should not be committed: {match.group(1)}"))
    return issues


def check_doc_root_layout(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for child in context.doc_dir.iterdir():
        if child.is_file() and child.name != "README.md":
            issues.append(Issue(child, "Doc root should only contain README.md and approved directories"))
        if child.is_dir() and child.name not in ALLOWED_DOC_TOP_LEVEL_DIRECTORIES:
            issues.append(Issue(child, f"unexpected top-level Doc directory: {child.name}"))
    return issues


def check_root_readme_docs_entry(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    if "Doc/README.md" not in markdown_link_targets(context.root_readme):
        return [Issue(context.root_readme, "root README must link to Doc/README.md")]
    return []


def check_directory_indexes(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    doc_index = context.doc_readme.read_text(encoding="utf-8")

    for directory in sorted(path for path in context.doc_dir.rglob("*") if path.is_dir()):
        readme = directory / "README.md"
        if not readme.exists():
            issues.append(Issue(readme, "documentation directory must include README.md"))

        if directory.parent == context.doc_dir:
            expected_link = f"]({directory.name}/README.md)"
            if expected_link not in doc_index:
                issues.append(
                    Issue(
                        context.doc_readme,
                        f"missing top-level directory index link: {directory.name}/README.md",
                    )
                )

        if not readme.exists():
            continue

        link_targets = markdown_link_targets(readme)
        for child in sorted(directory.iterdir()):
            if child.name == "README.md":
                continue
            if child.is_file() and child.suffix == ".md":
                expected_link = child.name
                message = f"missing directory README link: {expected_link}"
            elif child.is_dir() and (child / "README.md").exists():
                expected_link = f"{child.name}/README.md"
                message = f"missing child directory README link: {expected_link}"
            else:
                continue

            if expected_link not in link_targets:
                issues.append(Issue(readme, message))

    return issues


def check_product_line_docs(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    product_root = context.doc_dir / "product-lines"
    app_product_root = context.root / "app/src/main/java/com/tji/device/product"
    template_dir = product_root / "_template"
    product_index = context.product_lines_readme.read_text(encoding="utf-8")
    product_index_links = markdown_link_targets(context.product_lines_readme)

    missing_template_files = REQUIRED_PRODUCT_FILES - {
        child.name for child in template_dir.glob("*.md")
    }
    for missing in sorted(missing_template_files):
        issues.append(Issue(template_dir / missing, "missing product-line template file"))

    for child in sorted(product_root.iterdir()):
        if not child.is_dir() or child.name == "_template":
            continue
        existing_files = {file.name for file in child.glob("*.md")}
        missing_files = REQUIRED_PRODUCT_FILES - existing_files
        for missing in sorted(missing_files):
            issues.append(Issue(child / missing, "missing product-line document file"))
        for file in sorted(child.glob("*.md")):
            text = file.read_text(encoding="utf-8")
            for placeholder in REAL_PRODUCT_FORBIDDEN_PLACEHOLDERS:
                if placeholder in text:
                    issues.append(Issue(file, f"template placeholder remains in product-line document: {placeholder}"))
            if "## 状态" not in text:
                issues.append(Issue(file, "product-line document must include a status section"))
            elif not PRODUCT_DOC_STATUS_RE.search(text):
                issues.append(Issue(file, "product-line document status must be active, draft, or deprecated"))
            product_code_match = PRODUCT_CODE_RE.search(text)
            if product_code_match is None:
                issues.append(Issue(file, "product-line document must include product code"))
            elif product_code_match.group(1) != child.name:
                issues.append(
                    Issue(
                        file,
                        f"product-line document product code must match directory name: {child.name}",
                    )
                )
        expected_index_entry = f"{child.name}/README.md"
        if expected_index_entry not in product_index_links:
            issues.append(
                Issue(
                    context.product_lines_readme,
                    f"missing product-line index entry: {expected_index_entry}",
                )
            )

    for child in sorted(app_product_root.iterdir()):
        if not child.is_dir() or child.name in NON_PRODUCT_CODE_DIRS:
            continue
        doc_dir = product_root / child.name
        if not doc_dir.exists():
            issues.append(Issue(doc_dir, "missing product-line docs for app product directory"))

    for product_type, product_code, display_name in parse_product_catalog_definitions(context.product_catalog):
        expected_dir_name = product_type.lower()
        doc_dir = product_root / expected_dir_name
        app_dir = app_product_root / expected_dir_name
        if doc_dir.exists() or app_dir.exists():
            continue

        missing_fields = [
            field
            for field in (display_name, product_code, f"product/{expected_dir_name}")
            if field not in product_index
        ]
        if missing_fields:
            issues.append(
                Issue(
                    context.product_lines_readme,
                    f"missing reserved ProductCatalog product entry: {display_name} ({product_code}, product/{expected_dir_name})",
                )
            )

    return issues


def check_file_names(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for path in context.doc_dir.rglob("*.md"):
        if path.name == "README.md":
            continue
        if not KEBAB_MD_RE.match(path.name):
            issues.append(Issue(path, "markdown file name should be kebab-case"))
    return issues


def check_document_lifecycle_status(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for path in sorted(context.doc_dir.rglob("*.md")):
        if path.name == "README.md":
            continue
        relative_path = path.relative_to(context.doc_dir)
        if relative_path in LIFECYCLE_STATUS_EXEMPT_DOCS:
            continue

        text = path.read_text(encoding="utf-8")
        if "## 状态" not in text:
            issues.append(Issue(path, "document must include a lifecycle status section"))
        elif not DOC_STATUS_RE.search(text):
            issues.append(Issue(path, "document lifecycle status must be active, draft, or deprecated"))
        elif DEPRECATED_STATUS_RE.search(text) and "## 废弃说明" not in text:
            issues.append(Issue(path, "deprecated document must include a deprecation notes section"))

    return issues


def check_old_references(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    for path in iter_repository_text_files(context):
        relative_path = path.relative_to(context.root)
        if relative_path in OLD_REFERENCE_ALLOWED_RELATIVE_PATHS:
            continue
        text = path.read_text(encoding="utf-8")
        for old_reference in OLD_DOC_REFERENCES:
            if old_reference in text:
                issues.append(Issue(path, f"old Doc path reference: {old_reference}"))
    return issues


def check_migration_map(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    issues: list[Issue] = []
    migrations = parse_migration_map(context.migration_map)

    for old_reference in OLD_DOC_REFERENCES:
        if old_reference.endswith("/"):
            continue
        if old_reference not in migrations:
            issues.append(Issue(context.migration_map, f"missing migration entry: {old_reference}"))

    for old_path, new_path in sorted(migrations.items()):
        old_file = context.root / old_path
        if old_file.exists():
            issues.append(Issue(context.migration_map, f"old Doc path still exists after migration: {old_path}"))
        if new_path is None:
            continue
        target = context.root / new_path
        if not target.exists():
            issues.append(Issue(context.migration_map, f"migration target does not exist: {new_path}"))

    return issues


def check_system_files(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    return [
        Issue(path, "system file should not be committed under Doc")
        for path in context.doc_dir.rglob(".DS_Store")
    ]


def check_quality_gate_documentation(context: DocCheckContext = DEFAULT_CONTEXT) -> list[Issue]:
    documented_rules = parse_quality_gate_rules(context.quality_gates)
    expected_rules = {rule for rule, _description, _check in CHECK_RULES}
    issues: list[Issue] = []
    quality_gates_doc_link = "architecture/2026-06-docs-reorganization/quality-gates.md"

    if quality_gates_doc_link not in markdown_link_targets(context.doc_readme):
        issues.append(Issue(context.doc_readme, f"Doc README must link to quality gates: {quality_gates_doc_link}"))

    for missing_rule in sorted(expected_rules - documented_rules):
        issues.append(Issue(context.quality_gates, f"quality gate documentation missing rule: {missing_rule}"))

    for stale_rule in sorted(documented_rules - expected_rules):
        issues.append(Issue(context.quality_gates, f"quality gate documentation references unknown rule: {stale_rule}"))

    return issues


CHECK_RULES = (
    ("doc-root-layout", "Doc root only contains README.md and approved top-level directories.", check_doc_root_layout),
    ("root-readme-docs-entry", "Repository README links to the Doc/README.md documentation entry.", check_root_readme_docs_entry),
    ("directory-indexes", "Every documentation directory has README.md and indexes direct children.", check_directory_indexes),
    ("product-line-docs", "Product-line docs match app product directories, templates, indexes, and ProductCatalog reservations.", check_product_line_docs),
    ("file-names", "Markdown file names under Doc use kebab-case unless they are README.md.", check_file_names),
    ("document-lifecycle-status", "Business documents declare active, draft, or deprecated lifecycle status.", check_document_lifecycle_status),
    ("markdown-links", "Markdown relative links stay inside the repository and resolve to existing targets.", check_markdown_links),
    ("local-environment-references", "Markdown documents do not contain personal local machine paths.", check_local_environment_references),
    ("old-doc-references", "Repository text files do not reference legacy Doc paths outside allowed migration tooling.", check_old_references),
    ("migration-map", "The docs migration map covers known old paths and points to existing targets.", check_migration_map),
    ("system-files", "System files such as .DS_Store are not committed under Doc.", check_system_files),
    ("quality-gate-documentation", "The quality-gates document explains every documentation check rule.", check_quality_gate_documentation),
)


def tag_issues(rule: str, issues: list[Issue]) -> list[Issue]:
    return [Issue(issue.path, issue.message, rule) for issue in issues]


def selected_check_rules(rule_names: set[str] | None = None) -> tuple:
    if not rule_names:
        return CHECK_RULES
    return tuple(rule for rule in CHECK_RULES if rule[0] in rule_names)


def run_checks(context: DocCheckContext = DEFAULT_CONTEXT, rule_names: set[str] | None = None) -> list[Issue]:
    issues: list[Issue] = []
    for rule, _description, check in selected_check_rules(rule_names):
        issues.extend(tag_issues(rule, check(context)))
    return issues


def issues_by_rule(issues: list[Issue]) -> list[tuple[str, list[Issue]]]:
    grouped: dict[str, list[Issue]] = {}
    for issue in issues:
        grouped.setdefault(issue.rule, []).append(issue)
    return list(grouped.items())


def rule_records() -> list[dict[str, str]]:
    return [
        {
            "name": rule,
            "description": description,
        }
        for rule, description, _check in CHECK_RULES
    ]


def issue_record(issue: Issue, root: Path) -> dict[str, str]:
    return {
        "rule": issue.rule,
        "path": str(issue.path.relative_to(root)),
        "message": issue.message,
    }


def print_json(payload: dict[str, object]) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def print_rules() -> None:
    for rule, description, _check in CHECK_RULES:
        print(f"{rule}: {description}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quiet", action="store_true", help="only print failures")
    parser.add_argument("--list-rules", action="store_true", help="list documentation check rules and exit")
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="output format; json always emits a machine-readable object",
    )
    parser.add_argument(
        "--rule",
        action="append",
        choices=[rule for rule, _description, _check in CHECK_RULES],
        help="run only one documentation check rule; can be passed multiple times",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=DEFAULT_CONTEXT.root,
        help="repository root to validate; defaults to the current checkout",
    )
    args = parser.parse_args(argv)

    if args.list_rules:
        if args.format == "json":
            print_json({"schemaVersion": 1, "rules": rule_records()})
            return 0
        print_rules()
        return 0

    context = DocCheckContext(root=args.root.resolve())
    selected_rules = set(args.rule) if args.rule else None
    issues = run_checks(context, selected_rules)
    if args.format == "json":
        print_json(
            {
                "schemaVersion": 1,
                "ok": not issues,
                "issueCount": len(issues),
                "rules": [rule for rule, _description, _check in selected_check_rules(selected_rules)],
                "issues": [issue_record(issue, context.root) for issue in issues],
            }
        )
        return 1 if issues else 0

    if issues:
        print("Doc checks failed:")
        for rule, grouped_issues in issues_by_rule(issues):
            print(f"[{rule}]")
            for issue in grouped_issues:
                print(f"- {issue.format(context.root)}")
        return 1

    if not args.quiet:
        print("Doc checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
