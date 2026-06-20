#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DURATION_S="${DURATION_S:-120}"
UDP_PORT="${UDP_PORT:-47000}"
EXPECT_PACKETS="${EXPECT_PACKETS:-0}"
QT_MONITOR="${QT_MONITOR:-$HOME/Desktop/code/QT/tji-speaker-desktop/build/apps/qt-speaker-monitor/tji_speaker_monitor}"
APK="${APK:-}"
SKIP_BUILD="${SKIP_BUILD:-0}"

if [[ "$SKIP_BUILD" != "1" ]]; then
  ./gradlew :app:assembleDebug
fi

args=(
  --duration-s "$DURATION_S"
  --install-apk
  --launch-app
  --qt-monitor "$QT_MONITOR"
  --udp-port "$UDP_PORT"
  --expect-shadow-events 1
  --require-shadow-path tts-temp-file
  --require-shadow-path local-kokoro-tts-file
  --require-shadow-path record-save
  --require-shadow-path live-legacy-udp
  --require-shadow-path recorded-v2-udp
)

if [[ -n "$APK" ]]; then
  args+=(--apk "$APK")
fi

if [[ -n "${SERIAL:-}" ]]; then
  args+=(--serial "$SERIAL")
fi

if [[ "$EXPECT_PACKETS" != "0" ]]; then
  args+=(--expect-packets "$EXPECT_PACKETS")
fi

tools/run_speaker_field_validation.py "${args[@]}" "$@"
