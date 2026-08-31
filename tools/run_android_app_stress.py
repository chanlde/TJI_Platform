#!/usr/bin/env python3
"""Run non-actuating Android lifecycle stress against the TJI Platform app.

The runner never taps UI controls, logs in, publishes MQTT, flashes hardware, or
clears application data. It exercises only Android lifecycle and process edges.
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import tempfile
import time
from pathlib import Path


PACKAGE = "com.tji.device"
ACTIVITY = "com.tji.device/.ui.main.MainActivity"
TOTAL_TIME_RE = re.compile(r"^TotalTime:\s+(\d+)$", re.MULTILINE)
TOTAL_PSS_RE = re.compile(r"^\s*TOTAL:\s+(\d+)\b", re.MULTILINE)


class StressFailure(RuntimeError):
    pass


def command(*args: str, timeout: float = 20.0, check: bool = True) -> str:
    completed = subprocess.run(
        args,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=timeout,
    )
    if check and completed.returncode != 0:
        raise StressFailure(
            f"command failed ({completed.returncode}): {' '.join(args)}\n{completed.stdout}"
        )
    return completed.stdout


def adb(serial: str, *args: str, timeout: float = 20.0, check: bool = True) -> str:
    return command("adb", "-s", serial, *args, timeout=timeout, check=check)


def shell(serial: str, *args: str, timeout: float = 20.0, check: bool = True) -> str:
    return adb(serial, "shell", *args, timeout=timeout, check=check)


def connected_serial(requested: str | None) -> str:
    output = command("adb", "devices")
    devices = [
        line.split()[0]
        for line in output.splitlines()[1:]
        if line.strip().endswith("\tdevice")
    ]
    if requested:
        if requested not in devices:
            raise StressFailure(f"requested device is not connected: {requested}")
        return requested
    if len(devices) != 1:
        raise StressFailure(f"expected exactly one authorized device, found {len(devices)}")
    return devices[0]


def installed_version(serial: str) -> dict[str, object]:
    output = shell(serial, "dumpsys", "package", PACKAGE)
    code = re.search(r"versionCode=(\d+)", output)
    name = re.search(r"versionName=([^\s]+)", output)
    user_id = re.search(r"userId=(\d+)", output)
    if not (code and name and user_id):
        raise StressFailure(f"{PACKAGE} is not installed or package metadata is incomplete")
    return {
        "versionCode": int(code.group(1)),
        "versionName": name.group(1),
        "userId": int(user_id.group(1)),
    }


def launch(serial: str) -> int:
    output = shell(serial, "am", "start", "-W", "-n", ACTIVITY, timeout=30.0)
    if "Status: ok" not in output:
        raise StressFailure(f"activity launch did not report success:\n{output}")
    match = TOTAL_TIME_RE.search(output)
    if match is None:
        raise StressFailure(f"activity launch did not report TotalTime:\n{output}")
    pid = shell(serial, "pidof", PACKAGE, check=False).strip()
    if not pid:
        raise StressFailure("application process is absent after successful activity launch")
    return int(match.group(1))


def pid_of(serial: str) -> str:
    return shell(serial, "pidof", PACKAGE, check=False).strip()


def wait_for_pid_exit(serial: str, previous_pid: str, timeout_seconds: float = 3.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        current_pid = pid_of(serial)
        if not current_pid or current_pid != previous_pid:
            return
        time.sleep(0.1)
    raise StressFailure(f"process {previous_pid} did not exit after am kill")


def total_pss_kib(serial: str) -> int:
    output = shell(serial, "dumpsys", "meminfo", PACKAGE)
    match = TOTAL_PSS_RE.search(output)
    if match is None:
        raise StressFailure("could not parse TOTAL PSS from dumpsys meminfo")
    return int(match.group(1))


def progress(label: str, completed: int, total: int) -> None:
    if completed == total or completed % 10 == 0:
        print(f"{label}: {completed}/{total}", flush=True)


def crash_evidence(log_text: str) -> list[str]:
    lines = log_text.splitlines()
    findings: list[str] = []
    for index, line in enumerate(lines):
        window = "\n".join(lines[index : index + 10])
        fatal_for_app = "FATAL EXCEPTION" in line and f"Process: {PACKAGE}" in window
        anr_for_app = f"ANR in {PACKAGE}" in line
        native_for_app = f">>> {PACKAGE} <<<" in line
        if fatal_for_app or anr_for_app or native_for_app:
            findings.append(window[:1500])
    return findings


def percentile(values: list[int], fraction: float) -> int:
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial")
    parser.add_argument("--cold-starts", type=int, default=50)
    parser.add_argument("--foreground-cycles", type=int, default=100)
    parser.add_argument("--process-restarts", type=int, default=30)
    parser.add_argument("--rotations", type=int, default=40)
    args = parser.parse_args()
    for value in (
        args.cold_starts,
        args.foreground_cycles,
        args.process_restarts,
        args.rotations,
    ):
        if value < 0:
            raise StressFailure("stress iteration counts cannot be negative")

    serial = connected_serial(args.serial)
    before = installed_version(serial)
    manufacturer = shell(serial, "getprop", "ro.product.manufacturer").strip()
    model = shell(serial, "getprop", "ro.product.model").strip()
    android = shell(serial, "getprop", "ro.build.version.release").strip()
    original_auto_rotation = shell(
        serial, "settings", "get", "system", "accelerometer_rotation"
    ).strip()
    original_user_rotation = shell(
        serial, "settings", "get", "system", "user_rotation"
    ).strip()

    with tempfile.TemporaryDirectory(prefix="tji-app-stress-") as temp_directory:
        log_path = Path(temp_directory) / "logcat.txt"
        with log_path.open("w", encoding="utf-8") as log_output:
            log_process = subprocess.Popen(
                ["adb", "-s", serial, "logcat", "-v", "threadtime"],
                stdout=log_output,
                stderr=subprocess.STDOUT,
                text=True,
            )
            try:
                launch(serial)
                pss_before = total_pss_kib(serial)

                cold_times: list[int] = []
                for iteration in range(1, args.cold_starts + 1):
                    shell(serial, "am", "force-stop", PACKAGE)
                    cold_times.append(launch(serial))
                    progress("cold-start", iteration, args.cold_starts)

                foreground_times: list[int] = []
                for iteration in range(1, args.foreground_cycles + 1):
                    shell(serial, "input", "keyevent", "KEYCODE_HOME")
                    foreground_times.append(launch(serial))
                    progress("foreground", iteration, args.foreground_cycles)

                restart_times: list[int] = []
                for iteration in range(1, args.process_restarts + 1):
                    previous_pid = pid_of(serial)
                    if not previous_pid:
                        raise StressFailure("process is absent before process-restart test")
                    shell(serial, "input", "keyevent", "KEYCODE_HOME")
                    # MIUI may still classify the task as foreground for a short
                    # transition window; am kill is intentionally ignored then.
                    time.sleep(1.0)
                    shell(serial, "am", "kill", PACKAGE)
                    wait_for_pid_exit(serial, previous_pid)
                    restart_times.append(launch(serial))
                    if pid_of(serial) == previous_pid:
                        raise StressFailure("process PID did not change after process restart")
                    progress("process-restart", iteration, args.process_restarts)

                for iteration in range(1, args.rotations + 1):
                    rotation = str(iteration % 4)
                    shell(serial, "cmd", "window", "set-user-rotation", "lock", rotation)
                    time.sleep(0.15)
                    if not shell(serial, "pidof", PACKAGE, check=False).strip():
                        raise StressFailure(f"process disappeared during rotation {iteration}")
                    progress("rotation", iteration, args.rotations)
            finally:
                if original_auto_rotation == "1":
                    shell(
                        serial,
                        "cmd",
                        "window",
                        "set-user-rotation",
                        "free",
                        check=False,
                    )
                else:
                    shell(
                        serial,
                        "cmd",
                        "window",
                        "set-user-rotation",
                        "lock",
                        original_user_rotation or "0",
                        check=False,
                    )
                time.sleep(0.5)
                log_process.terminate()
                try:
                    log_process.wait(timeout=5.0)
                except subprocess.TimeoutExpired:
                    log_process.kill()
                    log_process.wait(timeout=5.0)

        pss_after_stress = total_pss_kib(serial)
        shell(serial, "input", "keyevent", "KEYCODE_HOME")
        time.sleep(3.0)
        pss_recovered = total_pss_kib(serial)
        final_launch_ms = launch(serial)
        pss_after_final_launch = total_pss_kib(serial)
        after = installed_version(serial)
        findings = crash_evidence(log_path.read_text(encoding="utf-8", errors="replace"))

    if after != before:
        raise StressFailure(f"installed package identity changed: before={before}, after={after}")
    if findings:
        raise StressFailure("crash/ANR evidence found:\n" + "\n---\n".join(findings))
    if pss_after_final_launch > 512 * 1024:
        raise StressFailure(
            f"final TOTAL PSS exceeds 512 MiB: {pss_after_final_launch} KiB"
        )
    if pss_recovered - pss_before > 32 * 1024:
        raise StressFailure(
            "background-recovered TOTAL PSS grew by more than 32 MiB: "
            f"before={pss_before}, recovered={pss_recovered} KiB"
        )

    def timing_summary(values: list[int]) -> dict[str, int] | None:
        if not values:
            return None
        return {
            "count": len(values),
            "minMs": min(values),
            "medianMs": round(statistics.median(values)),
            "p95Ms": percentile(values, 0.95),
            "maxMs": max(values),
        }

    result = {
        "status": "PASS",
        "device": {
            "serial": serial,
            "manufacturer": manufacturer,
            "model": model,
            "android": android,
        },
        "app": after,
        "iterations": {
            "coldStarts": args.cold_starts,
            "foregroundCycles": args.foreground_cycles,
            "processRestarts": args.process_restarts,
            "rotations": args.rotations,
        },
        "timings": {
            "coldStart": timing_summary(cold_times),
            "foreground": timing_summary(foreground_times),
            "processRestart": timing_summary(restart_times),
            "finalLaunchMs": final_launch_ms,
        },
        "memory": {
            "totalPssBeforeKiB": pss_before,
            "totalPssAfterStressKiB": pss_after_stress,
            "totalPssRecoveredInBackgroundKiB": pss_recovered,
            "recoveredGrowthKiB": pss_recovered - pss_before,
            "totalPssAfterFinalLaunchKiB": pss_after_final_launch,
        },
        "crashOrAnrFindings": len(findings),
        "safety": {
            "uiTaps": 0,
            "logins": 0,
            "mqttPublishes": 0,
            "mcuOrJlinkOperations": 0,
            "appDataClears": 0,
        },
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
