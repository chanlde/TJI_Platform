#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELAY_HOST="${HYDROLINK_SERVER_HOST:-146.56.250.203}"
RELAY_USER="${HYDROLINK_SERVER_USER:-root}"
RELAY_KEY="${HYDROLINK_SSH_KEY:-${HOME}/.ssh/tji_kokoro_deploy}"
RELAY_SERVICE="${HYDROLINK_SERVICE_NAME:-hydrolink-udp-relay.service}"

SSH=(
    ssh
    -i "$RELAY_KEY"
    -o BatchMode=yes
    -o StrictHostKeyChecking=no
    -o ConnectTimeout=8
    "$RELAY_USER@$RELAY_HOST"
)

relay_token="$("${SSH[@]}" "
    pid=\$(systemctl show '$RELAY_SERVICE' -p MainPID --value)
    token=\$(tr '\\000' '\\n' < \"/proc/\$pid/environ\" | sed -n 's/^TJI_SPEAKER_RELAY_TOKEN=//p' | head -n 1)
    if [ -z \"\$token\" ]; then
        previous=''
        while IFS= read -r argument; do
            if [ \"\$previous\" = '--token' ]; then
                token=\"\$argument\"
                break
            fi
            previous=\"\$argument\"
        done < <(tr '\\000' '\\n' < \"/proc/\$pid/cmdline\")
    fi
    printf '%s' \"\$token\"
")"

if [[ -z "$relay_token" || "$relay_token" =~ [[:space:]] ]]; then
    echo "Relay credential is missing or invalid; Android build was not started." >&2
    exit 2
fi

echo "Relay credential loaded securely; running speaker tests and installing noMap debug APK."
(
    cd "$REPO_DIR"
    TJI_SPEAKER_RELAY_TOKEN="$relay_token" ./gradlew \
        :app:testNoMapDebugUnitTest \
        --tests 'com.tji.device.product.speaker.*' \
        :app:installNoMapDebug \
        --no-daemon
)
unset relay_token
