#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."

logs=$(mktemp -d)
target/native-server/native-server >"$logs/server.log" 2>&1 &
pid=$!
cleanup() {
    if (( $? != 0 )); then tail -150 "$logs/server.log" >&2; fi
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    rm -rf "$logs"
}
trap cleanup EXIT

for ((i = 0; i < 60; i++)); do
    kill -0 "$pid"
    if grep -q 'Started Application' "$logs/server.log"; then
        target/native-client/native-client | tee "$logs/client.log"
        grep -Fxq RPC_SMOKE_OK "$logs/client.log"
        exit 0
    fi
    sleep 1
done
echo 'Timed out waiting for native-server' >&2
exit 1
