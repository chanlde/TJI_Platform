#!/usr/bin/env python3
"""Audit TJI speaker desktop readiness across the Android and Qt repos."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_QT_REPO = Path.home() / "Desktop/code/QT/tji-speaker-desktop"
DEFAULT_OUTPUT_DIR = ROOT / "build/speaker-desktop-readiness"
QT_REPO_SLUG = "chanlde/tji-speaker-desktop"


@dataclass(frozen=True)
class Check:
    name: str
    status: str
    detail: str


def run(command: list[str], cwd: Path, timeout: int = 30) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )


def git_value(repo: Path, *args: str) -> str:
    result = run(["git", *args], repo)
    return result.stdout.strip() if result.returncode == 0 else ""


def git_check(repo: Path, label: str, expected_origin: str | None = None) -> list[Check]:
    checks: list[Check] = []
    if not repo.exists():
        return [Check(label, "FAIL", f"missing repo: {repo}")]

    branch = git_value(repo, "branch", "--show-current")
    head = git_value(repo, "rev-parse", "--short", "HEAD")
    status = git_value(repo, "status", "--short")
    origin = git_value(repo, "remote", "get-url", "origin")
    upstream = git_value(repo, "rev-parse", "--short", "@{u}")

    clean = not status
    synced = bool(head and upstream and head == upstream)
    origin_ok = bool(origin) and (expected_origin is None or origin == expected_origin)

    checks.append(Check(f"{label} branch", "PASS" if branch == "main" else "WARN", branch or "unknown"))
    checks.append(Check(f"{label} clean", "PASS" if clean else "WARN", "clean" if clean else status.replace("\n", "; ")))
    checks.append(Check(f"{label} origin", "PASS" if origin_ok else "WARN", origin or "missing"))
    checks.append(Check(f"{label} pushed", "PASS" if synced else "WARN", f"HEAD={head or 'unknown'} upstream={upstream or 'missing'}"))
    return checks


def executable_check(path: Path, name: str) -> Check:
    return Check(name, "PASS" if path.exists() and path.stat().st_mode & 0o111 else "WARN", str(path))


def file_check(path: Path, name: str) -> Check:
    return Check(name, "PASS" if path.exists() else "WARN", str(path))


def latest_qt_ci() -> Check:
    if shutil.which("gh") is None:
        return Check("Qt GitHub Actions CI", "WARN", "gh not available")
    result = run(
        [
            "gh",
            "run",
            "list",
            "--repo",
            QT_REPO_SLUG,
            "--workflow",
            "CI",
            "--limit",
            "1",
            "--json",
            "databaseId,status,conclusion,headSha,displayTitle,url,createdAt",
        ],
        ROOT,
        timeout=60,
    )
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        return Check("Qt GitHub Actions CI", "WARN", detail or "query failed")
    try:
        runs = json.loads(result.stdout)
    except json.JSONDecodeError:
        return Check("Qt GitHub Actions CI", "WARN", "invalid gh JSON")
    if not runs:
        return Check("Qt GitHub Actions CI", "WARN", "no CI runs found")
    latest = runs[0]
    ok = latest.get("status") == "completed" and latest.get("conclusion") == "success"
    detail = (
        f"id={latest.get('databaseId')} status={latest.get('status')} "
        f"conclusion={latest.get('conclusion')} sha={str(latest.get('headSha', ''))[:7]} "
        f"url={latest.get('url')}"
    )
    return Check("Qt GitHub Actions CI", "PASS" if ok else "FAIL", detail)


def adb_devices() -> tuple[list[str], list[str]]:
    if shutil.which("adb") is None:
        return [], []
    result = run(["adb", "devices"], ROOT)
    devices: list[str] = []
    physical: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) != 2 or parts[1] != "device":
            continue
        serial = parts[0]
        devices.append(serial)
        if not serial.startswith("emulator-"):
            physical.append(serial)
    return devices, physical


def write_report(output_dir: Path, checks: list[Check], devices: list[str], physical: list[str]) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / "readiness-report.md"
    result = "FAIL" if any(check.status == "FAIL" for check in checks) else "PASS_WITH_PENDING_REAL_DEVICE"
    if physical:
        result = "PASS"
    lines = [
        "# Speaker Desktop Readiness Report",
        "",
        f"- Generated: {datetime.now().isoformat(timespec='seconds')}",
        f"- Result: {result}",
        f"- Android devices: {','.join(devices) if devices else 'none'}",
        f"- Physical Android devices: {','.join(physical) if physical else 'none'}",
        "",
        "## Checks",
        "",
        "| Check | Status | Detail |",
        "| --- | --- | --- |",
    ]
    for check in checks:
        detail = check.detail.replace("|", "\\|")
        lines.append(f"| {check.name} | {check.status} | `{detail}` |")
    lines.extend(
        [
            "",
            "## Remaining Field Gate",
            "",
            "Run `tools/run_speaker_real_device_validation.sh` with a physical Android device attached.",
            "The final gate should show `shadowStatus=ok`, empty `missingShadowPaths`, `udpMonitorStatus=ok`, and matching Qt monitor packet counts.",
        ]
    )
    tmp_path = output_dir / f".{path.name}.{os.getpid()}.tmp"
    tmp_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    tmp_path.replace(path)
    return path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qt-repo", type=Path, default=DEFAULT_QT_REPO)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--require-real-device", action="store_true")
    args = parser.parse_args(argv)

    qt_repo = args.qt_repo.resolve()
    checks: list[Check] = []
    checks.extend(git_check(ROOT, "TJI_Platform", "https://github.com/chanlde/TJI_Platform.git"))
    checks.extend(git_check(qt_repo, "Qt desktop", "https://github.com/chanlde/tji-speaker-desktop.git"))
    checks.append(file_check(ROOT / "native/speaker-core/include/tji_speaker_core.h", "speaker-core C ABI header"))
    checks.append(executable_check(qt_repo / "build/apps/qt-speaker-control/tji_speaker_control", "Qt Widgets smoke executable"))
    checks.append(executable_check(qt_repo / "build/apps/qt-speaker-monitor/tji_speaker_monitor", "Qt UDP monitor executable"))
    checks.append(file_check(qt_repo / "dist/TJI-Speaker-Control-macOS.manifest.json", "Qt package manifest"))
    checks.append(file_check(qt_repo / "dist/SHA256SUMS", "Qt package checksum"))
    checks.append(latest_qt_ci())

    devices, physical = adb_devices()
    if args.require_real_device:
        checks.append(
            Check(
                "physical Android device",
                "PASS" if physical else "FAIL",
                ",".join(physical) if physical else "none",
            )
        )
    else:
        checks.append(
            Check(
                "physical Android device",
                "PASS" if physical else "PENDING",
                ",".join(physical) if physical else "none",
            )
        )

    report = write_report(args.output_dir.resolve(), checks, devices, physical)
    for check in checks:
        print(f"{check.name}: {check.status} {check.detail}")
    print(f"androidDevices={','.join(devices) if devices else 'none'}")
    print(f"physicalAndroidDevices={','.join(physical) if physical else 'none'}")
    print(f"reportOutput={report}")
    return 1 if any(check.status == "FAIL" for check in checks) else 0


if __name__ == "__main__":
    raise SystemExit(main())
