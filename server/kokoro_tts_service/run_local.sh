#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

MODEL_DIR="${KOKORO_MODEL_DIR:-$PROJECT_ROOT/.tmp_tts_probe/kokoro-multi-lang-v1_0}"
VENV_DIR="${KOKORO_VENV_DIR:-$PROJECT_ROOT/.tmp_tts_probe/venv}"
HOST="${KOKORO_TTS_HOST:-127.0.0.1}"
PORT="${KOKORO_TTS_PORT:-8008}"

if [[ ! -f "$MODEL_DIR/model.onnx" ]]; then
  echo "Kokoro model not found: $MODEL_DIR" >&2
  echo "Set KOKORO_MODEL_DIR or extract kokoro-multi-lang-v1_0 under .tmp_tts_probe/." >&2
  exit 1
fi

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  python3 -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/pip" install -r "$SCRIPT_DIR/requirements.txt"

cd "$SCRIPT_DIR"
export KOKORO_MODEL_DIR="$MODEL_DIR"
export KOKORO_TTS_HOST="$HOST"
export KOKORO_TTS_PORT="$PORT"
exec "$VENV_DIR/bin/uvicorn" app:app --host "$HOST" --port "$PORT"
