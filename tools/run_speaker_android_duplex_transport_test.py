#!/usr/bin/env python3
"""Run the real Android bidirectional speaker transport test without acoustic input."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from datetime import datetime
from pathlib import Path

from run_speaker_virtual_app_stress import EventLog, MqttControl, SerialCapture


ROOT = Path(__file__).resolve().parents[1]
MCU_ROOT = Path("/Users/wangtianlong/Desktop/code/TJI_Bucket-1.0.15/micro")
TEST_CLASS = (
    "com.tji.device.product.speaker.audio."
    "SpeakerAndroidDuplexTransportTest"
)
EXPECTED_ROUNDS = 8
DEVICE_ID = "T5TNBFM4Q"
FEEDBACK_SESSION_ID = "LABAND_FB_01"
FEEDBACK_TALK_ID = "LABAND_MON_01"


def relay_token() -> str:
    host = os.environ.get("HYDROLINK_SERVER_HOST", "146.56.250.203")
    user = os.environ.get("HYDROLINK_SERVER_USER", "root")
    key = Path(
        os.environ.get("HYDROLINK_SSH_KEY", "~/.ssh/tji_kokoro_deploy")
    ).expanduser()
    service = os.environ.get(
        "HYDROLINK_SERVICE_NAME", "hydrolink-udp-relay.service"
    )
    remote = f"""
pid=$(systemctl show '{service}' -p MainPID --value)
token=$(tr '\\000' '\\n' < /proc/$pid/environ | sed -n 's/^TJI_SPEAKER_RELAY_TOKEN=//p' | head -n 1)
if [ -z "$token" ]; then
    previous=''
    while IFS= read -r argument; do
        if [ "$previous" = '--token' ]; then token="$argument"; break; fi
        previous="$argument"
    done < <(tr '\\000' '\\n' < /proc/$pid/cmdline)
fi
printf '%s' "$token"
"""
    completed = subprocess.run(
        [
            "ssh", "-i", str(key), "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=8",
            f"{user}@{host}", remote,
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    token = completed.stdout
    if not token or any(char.isspace() for char in token):
        raise RuntimeError("relay credential is missing or invalid")
    return token


def config_define(name: str) -> str:
    text = (MCU_ROOT / "Config/speaker_config.h").read_text(encoding="utf-8")
    marker = f"#define {name}"
    for line in text.splitlines():
        normalized = " ".join(line.split())
        if normalized.startswith(marker):
            return normalized[len(marker):].strip().strip('"').rstrip("U")
    raise RuntimeError(f"missing MCU config: {name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-id", default=DEVICE_ID)
    parser.add_argument("--serial-port", default="/dev/cu.usbmodem0000697303491")
    parser.add_argument("--serial-baud", type=int, default=115200)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument(
        "--instrument-only",
        action="store_true",
        help="run the already-installed test APK without triggering a USB install prompt",
    )
    args = parser.parse_args()

    output_dir = args.output_dir or (
        ROOT / "build/speaker-android-duplex" /
        datetime.now().strftime("%Y%m%d-%H%M%S")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    events = EventLog()
    serial = SerialCapture(
        None if args.serial_port == "none" else args.serial_port,
        args.serial_baud,
    )
    control = MqttControl(
        config_define("SPEAKER_CFG_MQTT_HOST"),
        int(config_define("SPEAKER_CFG_MQTT_PORT")),
        os.environ.get("TJI_MQTT_USERNAME", ""),
        args.device_id,
        events,
    )
    result = 1
    error = ""
    instrumentation_output = ""
    android_logcat = ""
    feedback_enabled = False
    serial.start()
    control.start()
    try:
        control.set_feedback(False)
        control.set_feedback(True, FEEDBACK_SESSION_ID, FEEDBACK_TALK_ID)
        feedback_enabled = True
        if args.instrument_only:
            subprocess.run(["adb", "logcat", "-c"], check=False)
            command = [
                "adb", "shell", "am", "instrument", "-w", "-r",
                "-e", "class", TEST_CLASS,
                "com.tji.device.test/androidx.test.runner.AndroidJUnitRunner",
            ]
            environment = None
        else:
            environment = os.environ.copy()
            environment["TJI_SPEAKER_RELAY_TOKEN"] = relay_token()
            command = [
                str(ROOT / "gradlew"),
                ":app:connectedNoMapDebugAndroidTest",
                f"-Pandroid.testInstrumentationRunnerArguments.class={TEST_CLASS}",
                "--no-daemon",
            ]
        completed = subprocess.run(
            command,
            cwd=ROOT,
            env=environment,
            check=False,
            stdout=subprocess.PIPE if args.instrument_only else None,
            stderr=subprocess.STDOUT if args.instrument_only else None,
            text=args.instrument_only,
        )
        result = completed.returncode
        if args.instrument_only:
            instrumentation_output = completed.stdout or ""
            print(instrumentation_output, end="")
            if (
                "FAILURES!!!" in instrumentation_output or
                "INSTRUMENTATION_FAILED" in instrumentation_output or
                "OK (1 test)" not in instrumentation_output
            ):
                result = 1
        if environment is not None:
            environment.pop("TJI_SPEAKER_RELAY_TOKEN", None)
        if args.instrument_only:
            android_logcat = subprocess.run(
                ["adb", "logcat", "-d", "-v", "brief"],
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            ).stdout
    except Exception as exc:
        error = str(exc)
    finally:
        if feedback_enabled:
            try:
                control.set_feedback(False)
            except Exception as exc:
                error = f"{error}; feedback cleanup: {exc}".strip("; ")
        control.close()
        serial.stop()

    serial_text = serial.data.decode("utf-8", errors="replace")
    (output_dir / "mcu-serial.log").write_text(serial_text, encoding="utf-8")
    if instrumentation_output:
        (output_dir / "android-instrumentation.log").write_text(
            instrumentation_output, encoding="utf-8"
        )
    if android_logcat:
        (output_dir / "android-logcat.log").write_text(
            android_logcat, encoding="utf-8"
        )
    android_rounds = [
        {
            "round": int(match.group(1)),
            "mode": match.group(2),
            "durationMs": int(match.group(3)),
            "opusBytes": int(match.group(4)),
            "receivedDuringTransfer": int(match.group(5)),
            "playedDuringTransfer": int(match.group(6)),
            "receivedThroughPlayback": int(match.group(7)),
            "playedThroughPlayback": int(match.group(8)),
        }
        for match in re.finditer(
            r"SPEAKER_DUPLEX round=(\d+) mode=([a-z-]+) "
            r"durationMs=(\d+) opusBytes=(\d+) "
            r"duringTransfer=(\d+)/(\d+) throughPlayback=(\d+)/(\d+)",
            android_logcat,
        )
    ]
    stream_records = [
        {
            "recordId": match.group(1),
            "frames": int(match.group(2)),
            "status": int(match.group(3)),
            "rebuffer": int(match.group(4)),
        }
        for match in re.finditer(
            r"stream done id=(\S+) frames=(\d+) status=(\d+) rebuffer=(\d+)",
            serial_text,
        )
    ]
    media_complete = len(re.findall(r"media_rx:\s+complete session=", serial_text))
    underruns = len(re.findall(r"underrun", serial_text, flags=re.IGNORECASE))
    busy = len(re.findall(r"media_rx:\s+busy", serial_text, flags=re.IGNORECASE))
    mcu_passed = (
        media_complete == EXPECTED_ROUNDS and
        len(stream_records) == EXPECTED_ROUNDS and
        all(item["status"] == 0 and item["rebuffer"] == 0 for item in stream_records) and
        underruns == 0 and
        busy == 0
    )
    report = {
        "passed": (
            result == 0 and not error and mcu_passed and
            len(android_rounds) == EXPECTED_ROUNDS
        ),
        "gradleExitCode": result,
        "error": error,
        "serialError": serial.error or "",
        "androidRounds": android_rounds,
        "mcu": {
            "mediaComplete": media_complete,
            "streamDone": stream_records,
            "underruns": underruns,
            "busy": busy,
        },
    }
    (output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print(f"report={output_dir / 'report.json'}")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
