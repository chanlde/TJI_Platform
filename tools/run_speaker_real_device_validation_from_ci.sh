#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APK_OUTPUT_DIR="${APK_OUTPUT_DIR:-build/ci-debug-apk}"

download_output="$(tools/download_latest_tji_debug_apk.py --output-dir "$APK_OUTPUT_DIR")"
printf '%s\n' "$download_output"

apk_path="$(printf '%s\n' "$download_output" | sed -n 's/^apk=//p')"
if [[ -z "$apk_path" || ! -f "$apk_path" ]]; then
  echo "Downloaded APK not found: ${apk_path:-missing}" >&2
  exit 2
fi

SKIP_BUILD=1 APK="$apk_path" tools/run_speaker_real_device_validation.sh "$@"
