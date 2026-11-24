#!/usr/bin/env bash
set -euo pipefail

name="rpc-test-$$"
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_VIRTUAL_HOST=simple-rpc-it
export SPRING_RABBITMQ_USERNAME=it
export SPRING_RABBITMQ_PASSWORD=it

docker build -t simple-rpc-rabbit:it "$(dirname "$0")"
docker run -d --name "$name" -p 127.0.0.1::5672 \
    -e RABBITMQ_DEFAULT_USER="$SPRING_RABBITMQ_USERNAME" \
    -e RABBITMQ_DEFAULT_PASS="$SPRING_RABBITMQ_PASSWORD" \
    -e RABBITMQ_DEFAULT_VHOST="$SPRING_RABBITMQ_VIRTUAL_HOST" simple-rpc-rabbit:it

cleanup() {
    if (( $? != 0 )); then docker logs --tail 150 "$name" >&2 || true; fi
    docker rm -fv "$name" >/dev/null
}
trap cleanup EXIT

address=$(docker port "$name" 5672/tcp)
export SPRING_RABBITMQ_PORT="${address##*:}"
broker() { docker exec --user rabbitmq "$name" "$@"; }
for ((i = 0; i < 30; i++)); do
    if broker test -s /var/lib/rabbitmq/.erlang.cookie &&
        broker rabbitmq-diagnostics -q check_port_connectivity &&
        broker rabbitmqctl -q list_vhosts name | grep -Fx "$SPRING_RABBITMQ_VIRTUAL_HOST"; then
        "$@"
        exit 0
    fi
    sleep 2
done
echo 'Timed out waiting for RabbitMQ' >&2
exit 1
