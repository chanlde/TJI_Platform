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
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

import verify_speaker_shadow


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = ROOT / "build/speaker-field-validation"
DEFAULT_QT_MONITOR = (
    Path.home()
    / "Desktop/code/QT/tji-speaker-desktop/build/apps/qt-speaker-monitor/tji_speaker_monitor"
)
DEFAULT_ANDROID_PACKAGE = "com.tji.device"
DEFAULT_ANDROID_ACTIVITY = ".ui.main.MainActivity"


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


@dataclass
class MonitorProcess:
    process: subprocess.Popen[str]
    output_file: object


@dataclass
class ValidationReport:
    output_dir: Path
    apk: Path
    apk_ok: bool = False
    install_status: str = "skipped"
    launch_status: str = "skipped"
    adb_serial: str | None = None
    shadow_output: Path | None = None
    shadow_summary: list[str] = field(default_factory=list)
    shadow_status: str = "skipped"
    expected_shadow_events: int = 0
    shadow_non_match: bool = False
    monitor_output: Path | None = None
    monitor_summary: list[str] = field(default_factory=list)
    monitor_status: str = "skipped"
    expected_udp_packets: int = 0
    exit_code: int = 0

    def write(self) -> Path:
        path = self.output_dir / "field-validation-report.md"
        lines = [
            "# Speaker Field Validation Report",
            "",
            f"- Result: {'PASS' if self.exit_code == 0 else 'FAIL'}",
            f"- APK: `{self.apk}`",
            f"- APK native libs: {'ok' if self.apk_ok else 'failed'}",
            f"- Install: {self.install_status}",
            f"- Launch: {self.launch_status}",
            f"- ADB serial: {self.adb_serial or 'skipped'}",
            f"- Android shadow log: `{self.shadow_output}`" if self.shadow_output else "- Android shadow log: skipped",
            f"- Qt monitor log: `{self.monitor_output}`" if self.monitor_output else "- Qt monitor log: skipped",
            f"- Expected shadow events: {self.expected_shadow_events}",
            f"- Expected UDP packets: {self.expected_udp_packets}",
            "",
            "## Android Shadow",
            "",
            f"- `shadowStatus={self.shadow_status}`",
        ]
        lines.extend(f"- `{line}`" for line in (self.shadow_summary or ["skipped"]))
        lines.extend(["", "## Qt UDP Monitor", ""])
        lines.append(f"- `udpMonitorStatus={self.monitor_status}`")
        lines.extend(f"- `{line}`" for line in self.monitor_summary)
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path


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


def shadow_ok(event_count: int, expect_events: int) -> bool:
    return expect_events <= 0 or event_count >= expect_events


def timestamped_output_dir(base: Path = DEFAULT_OUTPUT_DIR) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return base / stamp


def start_monitor(
    monitor_path: Path,
    port: int,
    duration_s: int,
    expect_packets: int,
    output_path: Path,
) -> MonitorProcess:
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
    process = subprocess.Popen(command, text=True, stdout=output_file, stderr=subprocess.STDOUT)
    return MonitorProcess(process=process, output_file=output_file)


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


def activity_component(package_name: str, activity: str) -> str:
    if activity.startswith("."):
        return f"{package_name}/{activity}"
    if "/" in activity:
        return activity
    return f"{package_name}/{activity}"


def install_apk(adb: str, serial: str, apk: Path) -> int:
    result = verify_speaker_shadow.run_adb(adb, serial, ["install", "-r", str(apk)], timeout=120)
    if result.returncode != 0:
        print("installStatus=failed")
        if result.stdout.strip():
            print(f"installStdout={result.stdout.strip()}")
        if result.stderr.strip():
            print(f"installStderr={result.stderr.strip()}")
        return 1
    print("installStatus=ok")
    return 0


def launch_app(adb: str, serial: str, package_name: str, activity: str) -> int:
    component = activity_component(package_name, activity)
    result = verify_speaker_shadow.run_adb(
        adb,
        serial,
        ["shell", "am", "start", "-n", component],
        timeout=30,
    )
    if result.returncode != 0:
        print(f"launchStatus=failed component={component}")
        if result.stdout.strip():
            print(f"launchStdout={result.stdout.strip()}")
        if result.stderr.strip():
            print(f"launchStderr={result.stderr.strip()}")
        return 1
    print(f"launchStatus=ok component={component}")
    return 0


def status_from_code(code: int) -> str:
    return "ok" if code == 0 else "failed"


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
    parser.add_argument("--expect-shadow-events", type=int, default=0, help="minimum Android shadow events expected.")
    parser.add_argument("--install-apk", action="store_true", help="install the APK on the selected Android device before capture.")
    parser.add_argument("--launch-app", action="store_true", help="launch the Android app before capture.")
    parser.add_argument("--android-package", default=DEFAULT_ANDROID_PACKAGE, help="Android package name to launch.")
    parser.add_argument("--android-activity", default=DEFAULT_ANDROID_ACTIVITY, help="Android Activity to launch.")
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
    report = ValidationReport(output_dir=output_dir, apk=apk)
    missing = verify_speaker_shadow.missing_native_libs(apk)
    print(f"apkPath={apk}")
    if missing:
        print("apkStatus=missingNativeLibs")
        for lib in missing:
            print(f"missingNativeLib={lib}")
        exit_code = 1
    else:
        print("apkStatus=ok")
        report.apk_ok = True
    report.expected_shadow_events = max(0, args.expect_shadow_events)
    report.expected_udp_packets = max(0, args.expect_packets)

    monitor_process: MonitorProcess | None = None
    monitor_output = output_dir / "qt-monitor.log"
    if not args.skip_monitor:
        monitor_path = args.qt_monitor.resolve()
        if not monitor_path.exists():
            print(f"udpMonitorStatus=missing path={monitor_path}")
            exit_code = max(exit_code, 2)
            report.monitor_status = "missing"
        else:
            monitor_process = start_monitor(
                monitor_path=monitor_path,
                port=args.udp_port,
                duration_s=args.duration_s,
                expect_packets=max(0, args.expect_packets),
                output_path=monitor_output,
            )
            print(f"udpMonitorStatus=running port={args.udp_port} output={monitor_output}")
            report.monitor_output = monitor_output
            report.monitor_status = "running"

    if not args.skip_adb:
        status, serial = select_serial(args.adb, args.serial)
        if status != 0 or serial is None:
            exit_code = max(exit_code, status)
        else:
            report.adb_serial = serial
            if args.install_apk:
                install_status = install_apk(args.adb, serial, apk)
                report.install_status = status_from_code(install_status)
                exit_code = max(exit_code, install_status)
            if args.launch_app:
                launch_status = launch_app(args.adb, serial, args.android_package, args.android_activity)
                report.launch_status = status_from_code(launch_status)
                exit_code = max(exit_code, launch_status)
            shadow_output = output_dir / "android-shadow.log"
            report.shadow_output = shadow_output
            print(f"adbStatus=ok serial={serial}")
            print(f"shadowOutput={shadow_output}")
            print("action=trigger speaker TTS, local Kokoro file, record-save, live talk, and recorded playback flows now")
            lines = verify_speaker_shadow.collect_logcat(args.adb, serial, max(1, args.duration_s), shadow_output)
            events = verify_speaker_shadow.parse_shadow_events(lines)
            report.shadow_summary = verify_speaker_shadow.summarize_events(events)
            for line in report.shadow_summary:
                print(line)
            if not shadow_ok(len(events), max(0, args.expect_shadow_events)):
                print(f"shadowStatus=failed expectedEvents={args.expect_shadow_events} actualEvents={len(events)}")
                report.shadow_status = "failed"
                exit_code = max(exit_code, 1)
            else:
                print(f"shadowStatus=ok expectedEvents={args.expect_shadow_events} actualEvents={len(events)}")
                report.shadow_status = "ok"
            if any(event.status != "match" for event in events):
                report.shadow_non_match = True
                report.shadow_status = "failed"
                exit_code = max(exit_code, 1)

    if monitor_process is not None:
        monitor_return = monitor_process.process.wait(timeout=max(5, args.duration_s + 10))
        monitor_process.output_file.close()
        monitor_text = monitor_output.read_text(encoding="utf-8") if monitor_output.exists() else ""
        summary = parse_monitor_summary(monitor_text)
        report.monitor_summary = monitor_summary_lines(summary, max(0, args.expect_packets))
        for line in report.monitor_summary:
            print(line)
        if monitor_return != 0 or not monitor_ok(summary, max(0, args.expect_packets)):
            print(f"udpMonitorStatus=failed exitCode={monitor_return}")
            report.monitor_status = "failed"
            exit_code = max(exit_code, 1)
        else:
            print(f"udpMonitorStatus=ok exitCode={monitor_return}")
            report.monitor_status = "ok"

    print(f"outputDir={output_dir}")
    report.exit_code = exit_code
    report_path = report.write()
    print(f"reportOutput={report_path}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
