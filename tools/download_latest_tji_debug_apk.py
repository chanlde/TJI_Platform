#!/usr/bin/env python3
"""Download the latest successful TJI_Platform debug APK artifact."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPO_SLUG = "chanlde/TJI_Platform"
WORKFLOW = "CI"
ARTIFACT_NAME = "TJI_Platform-debug-apk"
DEFAULT_OUTPUT_DIR = ROOT / "build/ci-debug-apk"


def run(command: list[str], timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )


def latest_successful_run_id() -> int:
    result = run(
        [
            "gh",
            "run",
            "list",
            "--repo",
            REPO_SLUG,
            "--workflow",
            WORKFLOW,
            "--limit",
            "10",
            "--json",
            "databaseId,status,conclusion,headSha,createdAt",
        ],
        timeout=60,
    )
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout).strip() or "failed to query workflow runs")
    runs = json.loads(result.stdout)
    for item in runs:
        if item.get("status") == "completed" and item.get("conclusion") == "success":
            return int(item["databaseId"])
    raise RuntimeError("no successful CI run found")


def clean_output_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def download_artifact(run_id: int, output_dir: Path) -> None:
    result = run(
        [
            "gh",
            "run",
            "download",
            str(run_id),
            "--repo",
            REPO_SLUG,
            "--name",
            ARTIFACT_NAME,
            "--dir",
            str(output_dir),
        ],
        timeout=180,
    )
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout).strip() or "failed to download APK artifact")


def find_apk(output_dir: Path) -> Path:
    apks = sorted(output_dir.glob("*.apk"))
    if len(apks) != 1:
        raise RuntimeError(f"expected exactly one APK in {output_dir}, found {len(apks)}")
    return apks[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", type=int, default=None, help="GitHub Actions run id. Defaults to latest successful CI run.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    if shutil.which("gh") is None:
        raise RuntimeError("gh is not available on PATH")
    output_dir = args.output_dir.resolve()
    run_id = args.run_id or latest_successful_run_id()
    clean_output_dir(output_dir)
    download_artifact(run_id, output_dir)
    apk = find_apk(output_dir)
    metadata = output_dir / "output-metadata.json"
    print(f"runId={run_id}")
    print(f"artifact={ARTIFACT_NAME}")
    print(f"apk={apk}")
    print(f"metadata={metadata if metadata.exists() else 'missing'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
