#!/usr/bin/env python3
"""Verify Android speaker-core JNI packaging and collect shadow logs.

The script intentionally has no third-party dependencies. It can run in two
modes:

- APK-only: verify the debug/release APK contains the native speaker-core lib.
- Device: clear logcat, collect SpeakerAudioData lines, and summarize
  speakerCoreShadow status/path markers while a tester triggers speaker flows.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import time
import zipfile
from collections import Counter
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = ROOT / "build/speaker-shadow"
REQUIRED_NATIVE_LIBS = (
    "lib/arm64-v8a/libtji_speaker_core_jni.so",
    "lib/arm64-v8a/libc++_shared.so",
)
SHADOW_STATUS_RE = re.compile(r"\bspeakerCoreShadow\s+status=([A-Za-z0-9_]+)")
SHADOW_FIELD_RE = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)=([^\s]+)")


@dataclass(frozen=True)
class ShadowEvent:
    line: str
    status: str
    fields: dict[str, str]

    @property
    def path(self) -> str:
        return self.fields.get("path", "unknown")


def latest_apk(root: Path = ROOT) -> Path | None:
    candidates = sorted(
        (root / "app/build/outputs/apk").glob("**/*.apk"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    return candidates[0] if candidates else None


def apk_entries(apk_path: Path) -> set[str]:
    with zipfile.ZipFile(apk_path) as archive:
        return set(archive.namelist())


def missing_native_libs(apk_path: Path) -> list[str]:
    entries = apk_entries(apk_path)
    return [lib for lib in REQUIRED_NATIVE_LIBS if lib not in entries]


def parse_shadow_events(lines: list[str]) -> list[ShadowEvent]:
    events: list[ShadowEvent] = []
    for line in lines:
        status_match = SHADOW_STATUS_RE.search(line)
        if not status_match:
            continue
        fields = {key: value for key, value in SHADOW_FIELD_RE.findall(line)}
        events.append(
            ShadowEvent(
                line=line.rstrip("\n"),
                status=status_match.group(1),
                fields=fields,
            )
        )
    return events


def summarize_events(events: list[ShadowEvent]) -> list[str]:
    status_counts = Counter(event.status for event in events)
    path_counts = Counter(event.path for event in events)
    lines = [
        f"shadowEvents={len(events)}",
        "statusCounts=" + ",".join(f"{key}:{status_counts[key]}" for key in sorted(status_counts)),
        "pathCounts=" + ",".join(f"{key}:{path_counts[key]}" for key in sorted(path_counts)),
    ]
    bad = [event for event in events if event.status != "match"]
    lines.append(f"nonMatchEvents={len(bad)}")
    lines.extend(f"nonMatchLine={event.line}" for event in bad[:20])
    return lines


def run_adb(adb: str, serial: str | None, args: list[str], timeout: int = 15) -> subprocess.CompletedProcess[str]:
    command = [adb]
    if serial:
        command.extend(["-s", serial])
    command.extend(args)
    return subprocess.run(command, text=True, capture_output=True, timeout=timeout, check=False)


def connected_devices(adb: str) -> list[str]:
    result = run_adb(adb, None, ["devices"], timeout=15)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "adb devices failed")
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def collect_logcat(adb: str, serial: str | None, duration_s: int, output_path: Path) -> list[str]:
    clear = run_adb(adb, serial, ["logcat", "-c"], timeout=15)
    if clear.returncode != 0:
        raise RuntimeError(clear.stderr.strip() or "adb logcat -c failed")

    time.sleep(duration_s)
    dump = run_adb(
        adb,
        serial,
        ["logcat", "-d", "-v", "time", "-s", "SpeakerAudioData:D", "*:S"],
        timeout=30,
    )
    if dump.returncode != 0:
        raise RuntimeError(dump.stderr.strip() or "adb logcat -d failed")
    lines = dump.stdout.splitlines(keepends=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("".join(lines), encoding="utf-8")
    return lines


def default_output_path(output_dir: Path = DEFAULT_OUTPUT_DIR) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return output_dir / f"speaker-shadow-{stamp}.log"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=None, help="APK to inspect. Defaults to newest app/build/outputs/apk/**/*.apk.")
    parser.add_argument("--adb", default="adb", help="adb executable path.")
    parser.add_argument("--serial", default=None, help="adb device serial.")
    parser.add_argument("--duration-s", type=int, default=120, help="logcat collection duration.")
    parser.add_argument("--output", type=Path, default=None, help="log output path.")
    parser.add_argument("--apk-only", action="store_true", help="Only inspect APK native libraries; skip adb/logcat.")
    args = parser.parse_args(argv)

    apk_path = args.apk or latest_apk()
    if apk_path is None:
        print("apkStatus=missing apkPath=none")
        return 2
    apk_path = apk_path.resolve()
    if not apk_path.exists():
        print(f"apkStatus=missing apkPath={apk_path}")
        return 2

    missing = missing_native_libs(apk_path)
    print(f"apkPath={apk_path}")
    if missing:
        print("apkStatus=missingNativeLibs")
        for lib in missing:
            print(f"missingNativeLib={lib}")
        return 1
    print("apkStatus=ok")
    for lib in REQUIRED_NATIVE_LIBS:
        print(f"nativeLib={lib}")

    if args.apk_only:
        return 0
    if shutil.which(args.adb) is None:
        print(f"adbStatus=missing executable={args.adb}")
        return 2

    devices = connected_devices(args.adb)
    serial = args.serial
    if serial is None:
        if len(devices) != 1:
            print(f"adbStatus=deviceSelectionRequired connected={','.join(devices) or 'none'}")
            return 2
        serial = devices[0]
    elif serial not in devices:
        print(f"adbStatus=deviceNotConnected serial={serial} connected={','.join(devices) or 'none'}")
        return 2

    output = (args.output or default_output_path()).resolve()
    print(f"adbStatus=ok serial={serial}")
    print(f"logOutput={output}")
    print("action=trigger speaker TTS, local Kokoro file, record-save, live talk, and recorded playback flows now")
    lines = collect_logcat(args.adb, serial, max(1, args.duration_s), output)
    events = parse_shadow_events(lines)
    for line in summarize_events(events):
        print(line)
    return 1 if any(event.status != "match" for event in events) else 0


if __name__ == "__main__":
    raise SystemExit(main())
