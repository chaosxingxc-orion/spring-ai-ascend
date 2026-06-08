#!/usr/bin/env bash
# Load an env file, then run the example A2A console client against a running server.
# Start the server first (scripts/run-server.sh). Usage: bash scripts/run-client.sh [env-file]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; . "$ENV_FILE"; set +a
  echo "loaded env: $ENV_FILE"
else
  echo "env file not found: $ENV_FILE — using application.yaml defaults"
fi
cd "$REPO"
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
