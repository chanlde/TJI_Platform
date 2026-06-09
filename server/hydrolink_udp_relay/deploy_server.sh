#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_HOST="${HYDROLINK_SERVER_HOST:-146.56.250.203}"
SERVER_USER="${HYDROLINK_SERVER_USER:-root}"
SSH_KEY="${HYDROLINK_SSH_KEY:-$HOME/.ssh/tji_kokoro_deploy}"
REMOTE_DIR="${HYDROLINK_REMOTE_DIR:-/opt/hydrolink}"
SERVICE_NAME="${HYDROLINK_SERVICE_NAME:-hydrolink-udp-relay.service}"

SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST")
SCP=(scp -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=no)

python3 -m py_compile "$SCRIPT_DIR/udp_4g_relay_server.py"

echo "Syncing UDP relay to $SERVER_USER@$SERVER_HOST:$REMOTE_DIR ..."
"${SSH[@]}" "mkdir -p '$REMOTE_DIR'"
"${SCP[@]}" "$SCRIPT_DIR/udp_4g_relay_server.py" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/udp_4g_relay_server.py"
"${SSH[@]}" "python3 -m py_compile '$REMOTE_DIR/udp_4g_relay_server.py' && systemctl restart '$SERVICE_NAME' && systemctl status '$SERVICE_NAME' --no-pager -l | sed -n '1,60p'"

