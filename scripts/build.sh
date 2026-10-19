#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${HEXGATE_JAVA_HOME:-}" ]]; then export JAVA_HOME="$HEXGATE_JAVA_HOME"; fi
exec ./gradlew --console=plain "$@"
