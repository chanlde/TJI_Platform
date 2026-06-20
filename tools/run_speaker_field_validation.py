#!/usr/bin/env python3
"""Run a coordinated speaker field validation session.

This tool combines two checks used during real-device speaker debugging:

- Android shadow validation from ``verify_speaker_shadow.py``.
- Qt UDP monitor capture from ``tji_speaker_monitor``.

It is safe to run with either side skipped when a device or UDP source is not
available yet.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import verify_speaker_shadow


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = ROOT / "build/speaker-field-validation"
DEFAULT_QT_MONITOR = (
    Path.home()
    / "Desktop/code/QT/tji-speaker-desktop/build/apps/qt-speaker-monitor/tji_speaker_monitor"
)


@dataclass(frozen=True)
class MonitorSummary:
    values: dict[str, str]

    @property
    def total_packets(self) -> int:
        return int(self.values.get("totalPackets", "0"))

    @property
    def unknown_packets(self) -> int:
        return int(self.values.get("unknownPackets", "0"))

    @property
    def first_sequence(self) -> int:
        return int(self.values.get("firstSequence", "0"))

    @property
    def last_sequence(self) -> int:
        return int(self.values.get("lastSequence", "0"))


def parse_monitor_summary(text: str) -> MonitorSummary:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return MonitorSummary(values)


def monitor_summary_lines(summary: MonitorSummary, expect_packets: int) -> list[str]:
    lines = [f"udpMonitorPackets={summary.total_packets}"]
    lines.append(f"udpMonitorUnknownPackets={summary.unknown_packets}")
    lines.append(f"udpMonitorSequence={summary.first_sequence}..{summary.last_sequence}")
    if expect_packets > 0:
        lines.append(f"udpMonitorExpectedPackets={expect_packets}")
    for key in ("legacyPackets", "v2Packets", "avgGapMs", "firstHeader"):
        if key in summary.values:
            lines.append(f"udpMonitor{key[0].upper() + key[1:]}={summary.values[key]}")
    return lines


def monitor_ok(summary: MonitorSummary, expect_packets: int) -> bool:
    if summary.unknown_packets != 0:
        return False
    if expect_packets > 0 and summary.total_packets < expect_packets:
        return False
    if expect_packets > 0 and summary.first_sequence == 0 and summary.last_sequence < expect_packets - 1:
        return False
    return True


def timestamped_output_dir(base: Path = DEFAULT_OUTPUT_DIR) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return base / stamp


def start_monitor(
    monitor_path: Path,
    port: int,
    duration_s: int,
    expect_packets: int,
    output_path: Path,
) -> subprocess.Popen[str]:
    command = [
        str(monitor_path),
        "--port",
        str(port),
        "--duration-ms",
        str(max(1, duration_s) * 1000),
    ]
    if expect_packets > 0:
        command.extend(["--expect-packets", str(expect_packets)])
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_file = output_path.open("w", encoding="utf-8")
    return subprocess.Popen(command, text=True, stdout=output_file, stderr=subprocess.STDOUT)


def resolve_apk(apk: Path | None) -> Path | None:
    return apk.resolve() if apk is not None else verify_speaker_shadow.latest_apk()


def select_serial(adb: str, serial: str | None) -> tuple[int, str | None]:
    if shutil.which(adb) is None:
        print(f"adbStatus=missing executable={adb}")
        return 2, None
    devices = verify_speaker_shadow.connected_devices(adb)
    if serial is None:
        if len(devices) != 1:
            print(f"adbStatus=deviceSelectionRequired connected={','.join(devices) or 'none'}")
            return 2, None
        return 0, devices[0]
    if serial not in devices:
        print(f"adbStatus=deviceNotConnected serial={serial} connected={','.join(devices) or 'none'}")
        return 2, None
    return 0, serial


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=None, help="APK to inspect. Defaults to newest app/build/outputs/apk/**/*.apk.")
    parser.add_argument("--adb", default="adb", help="adb executable path.")
    parser.add_argument("--serial", default=None, help="adb device serial.")
    parser.add_argument("--duration-s", type=int, default=120, help="capture duration.")
    parser.add_argument("--output-dir", type=Path, default=None, help="directory for logs and summaries.")
    parser.add_argument("--qt-monitor", type=Path, default=DEFAULT_QT_MONITOR, help="path to tji_speaker_monitor.")
    parser.add_argument("--udp-port", type=int, default=47000, help="UDP port to monitor.")
    parser.add_argument("--expect-packets", type=int, default=0, help="minimum UDP packets expected by the monitor.")
    parser.add_argument("--skip-adb", action="store_true", help="skip Android shadow log collection.")
    parser.add_argument("--skip-monitor", action="store_true", help="skip Qt UDP monitor.")
    args = parser.parse_args(argv)

    output_dir = (args.output_dir or timestamped_output_dir()).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    exit_code = 0

    apk = resolve_apk(args.apk)
    if apk is None or not apk.exists():
        print(f"apkStatus=missing apkPath={apk or 'none'}")
        return 2
    missing = verify_speaker_shadow.missing_native_libs(apk)
    print(f"apkPath={apk}")
    if missing:
        print("apkStatus=missingNativeLibs")
        for lib in missing:
            print(f"missingNativeLib={lib}")
        exit_code = 1
    else:
        print("apkStatus=ok")

    monitor_process: subprocess.Popen[str] | None = None
    monitor_output = output_dir / "qt-monitor.log"
    if not args.skip_monitor:
        monitor_path = args.qt_monitor.resolve()
        if not monitor_path.exists():
            print(f"udpMonitorStatus=missing path={monitor_path}")
            exit_code = max(exit_code, 2)
        else:
            monitor_process = start_monitor(
                monitor_path=monitor_path,
                port=args.udp_port,
                duration_s=args.duration_s,
                expect_packets=max(0, args.expect_packets),
                output_path=monitor_output,
            )
            print(f"udpMonitorStatus=running port={args.udp_port} output={monitor_output}")

    if not args.skip_adb:
        status, serial = select_serial(args.adb, args.serial)
        if status != 0 or serial is None:
            exit_code = max(exit_code, status)
        else:
            shadow_output = output_dir / "android-shadow.log"
            print(f"adbStatus=ok serial={serial}")
            print(f"shadowOutput={shadow_output}")
            print("action=trigger speaker TTS, local Kokoro file, record-save, live talk, and recorded playback flows now")
            lines = verify_speaker_shadow.collect_logcat(args.adb, serial, max(1, args.duration_s), shadow_output)
            events = verify_speaker_shadow.parse_shadow_events(lines)
            for line in verify_speaker_shadow.summarize_events(events):
                print(line)
            if any(event.status != "match" for event in events):
                exit_code = max(exit_code, 1)

    if monitor_process is not None:
        monitor_return = monitor_process.wait(timeout=max(5, args.duration_s + 10))
        monitor_text = monitor_output.read_text(encoding="utf-8") if monitor_output.exists() else ""
        summary = parse_monitor_summary(monitor_text)
        for line in monitor_summary_lines(summary, max(0, args.expect_packets)):
            print(line)
        if monitor_return != 0 or not monitor_ok(summary, max(0, args.expect_packets)):
            print(f"udpMonitorStatus=failed exitCode={monitor_return}")
            exit_code = max(exit_code, 1)
        else:
            print(f"udpMonitorStatus=ok exitCode={monitor_return}")

    print(f"outputDir={output_dir}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
