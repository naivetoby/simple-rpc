#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."

for role in server client; do
    mvn --batch-mode --no-transfer-progress -Pnative,native-interop \
        -Dnative.role="$role" -Dmaven.compiler.proc=full "$@" \
        clean compile spring-boot:process-aot native:add-reachability-metadata native:compile-no-fork
done
