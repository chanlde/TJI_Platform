#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SERVER_HOST="${KOKORO_SERVER_HOST:-146.56.250.203}"
SERVER_USER="${KOKORO_SERVER_USER:-root}"
SSH_KEY="${KOKORO_SSH_KEY:-$HOME/.ssh/tji_kokoro_deploy}"
REMOTE_ROOT="${KOKORO_REMOTE_ROOT:-/opt/tji/kokoro-tts}"
REMOTE_SERVER_DIR="$REMOTE_ROOT/server"
SERVICE_NAME="${KOKORO_SERVICE_NAME:-tji-kokoro-tts.service}"

SSH=(ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST")
RSYNC_SSH="ssh -i $SSH_KEY"

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH key not found: $SSH_KEY" >&2
  exit 1
fi

echo "Syncing speaker record transfer service to $SERVER_USER@$SERVER_HOST:$REMOTE_SERVER_DIR ..."
rsync -az --delete \
  --exclude '__pycache__/' \
  --exclude '.pytest_cache/' \
  -e "$RSYNC_SSH" \
  "$SCRIPT_DIR/" "$SERVER_USER@$SERVER_HOST:$REMOTE_SERVER_DIR/"

echo "Installing dependencies and restarting $SERVICE_NAME ..."
"${SSH[@]}" "set -e
cd '$REMOTE_ROOT'
if [ ! -d venv ]; then
  python3 -m venv venv
fi
venv/bin/pip install -r '$REMOTE_SERVER_DIR/requirements.txt'
cp '$REMOTE_SERVER_DIR/$SERVICE_NAME' '/etc/systemd/system/$SERVICE_NAME'
systemctl daemon-reload
systemctl enable --now '$SERVICE_NAME'
systemctl restart '$SERVICE_NAME'
ufw allow 8008/tcp >/dev/null || true
systemctl is-active '$SERVICE_NAME'
for attempt in \$(seq 1 30); do
  if curl -fsS http://127.0.0.1:8008/health; then
    exit 0
  fi
  sleep 1
done
echo 'Speaker record transfer service did not become healthy in time' >&2
systemctl status --no-pager -l '$SERVICE_NAME' >&2
exit 1
"

echo
echo "Public health check:"
curl -sS "http://$SERVER_HOST:8008/health"
echo
