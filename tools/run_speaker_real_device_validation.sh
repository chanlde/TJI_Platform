#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DURATION_S="${DURATION_S:-120}"
UDP_PORT="${UDP_PORT:-47000}"
EXPECT_PACKETS="${EXPECT_PACKETS:-1}"
QT_MONITOR="${QT_MONITOR:-$HOME/Desktop/code/QT/tji-speaker-desktop/build/apps/qt-speaker-monitor/tji_speaker_monitor}"
APK="${APK:-}"
SKIP_BUILD="${SKIP_BUILD:-0}"
SKIP_ADB="${SKIP_ADB:-0}"
SKIP_MONITOR="${SKIP_MONITOR:-0}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

usage() {
  cat >&2 <<EOF
Usage: $0 [extra run_speaker_field_validation.py args]

Environment:
  DURATION_S=<seconds>       Capture window, default: $DURATION_S
  UDP_PORT=<port>            Qt monitor UDP port, default: $UDP_PORT
  EXPECT_PACKETS=<count>     Minimum UDP packets, default: $EXPECT_PACKETS
  QT_MONITOR=<path>          tji_speaker_monitor path
  APK=<path>                 Use a specific APK instead of newest debug APK
  SERIAL=<adb-serial>        Required when more than one Android device is attached
  OUTPUT_DIR=<path>          Write logs/report to a fixed directory
  SKIP_BUILD=1               Do not run :app:assembleDebug first
  SKIP_ADB=1                 Skip Android install/launch/logcat collection
  SKIP_MONITOR=1             Skip Qt UDP monitor

Pass --help-field to print the underlying Python field validation options.
EOF
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--help-field" ]]; then
  exec tools/run_speaker_field_validation.py --help
fi

if [[ "$SKIP_MONITOR" != "1" && ! -x "$QT_MONITOR" ]]; then
  echo "Qt monitor is missing or not executable: $QT_MONITOR" >&2
  echo "Build it first from $HOME/Desktop/code/QT/tji-speaker-desktop:" >&2
  echo "  source $HOME/Desktop/code/QT/scripts/qt-env.sh && cmake --build build --target tji_speaker_monitor" >&2
  exit 2
fi

if [[ "$SKIP_ADB" != "1" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb is not available on PATH." >&2
    exit 2
  fi
  connected_devices="$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')"
  device_count="$(printf '%s\n' "$connected_devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ -z "${SERIAL:-}" && "$device_count" != "1" ]]; then
    echo "Exactly one Android device is required unless SERIAL is set; connected=$device_count" >&2
    if [[ -n "$connected_devices" ]]; then
      printf '%s\n' "$connected_devices" >&2
    fi
    exit 2
  fi
fi

if [[ "$SKIP_BUILD" != "1" ]]; then
  ./gradlew :app:assembleDebug
fi

args=(
  --duration-s "$DURATION_S"
  --expect-shadow-events 1
  --require-shadow-path tts-temp-file
  --require-shadow-path local-kokoro-tts-file
  --require-shadow-path record-save
  --require-shadow-path live-legacy-udp
  --require-shadow-path recorded-v2-udp
)

if [[ "$SKIP_ADB" == "1" ]]; then
  args+=(--skip-adb)
else
  args+=(--install-apk --launch-app)
fi

if [[ "$SKIP_MONITOR" == "1" ]]; then
  args+=(--skip-monitor)
else
  args+=(--qt-monitor "$QT_MONITOR" --udp-port "$UDP_PORT")
fi

if [[ -n "$APK" ]]; then
  args+=(--apk "$APK")
fi

if [[ -n "${SERIAL:-}" ]]; then
  args+=(--serial "$SERIAL")
fi

if [[ -n "$OUTPUT_DIR" ]]; then
  args+=(--output-dir "$OUTPUT_DIR")
fi

if [[ "$SKIP_MONITOR" != "1" && "$EXPECT_PACKETS" != "0" ]]; then
  args+=(--expect-packets "$EXPECT_PACKETS")
fi

cat <<EOF
realDeviceValidation=starting
durationS=$DURATION_S
udpPort=$UDP_PORT
expectPackets=$EXPECT_PACKETS
qtMonitor=$QT_MONITOR
skipAdb=$SKIP_ADB
skipMonitor=$SKIP_MONITOR
EOF

tools/run_speaker_field_validation.py "${args[@]}" "$@"
