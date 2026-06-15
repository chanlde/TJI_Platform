#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

VENV_DIR="${SPEAKER_RECORD_VENV_DIR:-$PROJECT_ROOT/.tmp_speaker_record_transfer/venv}"
HOST="${SPEAKER_RECORD_HOST:-127.0.0.1}"
PORT="${SPEAKER_RECORD_PORT:-8008}"

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  python3 -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/pip" install -r "$SCRIPT_DIR/requirements.txt"

cd "$SCRIPT_DIR"
exec "$VENV_DIR/bin/uvicorn" app:app --host "$HOST" --port "$PORT"
