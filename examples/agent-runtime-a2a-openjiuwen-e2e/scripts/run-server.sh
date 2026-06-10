#!/usr/bin/env bash
# Load an env file, install agent-runtime, then start the OpenJiuwen-only A2A server.
#
# Usage: bash scripts/run-server.sh [env-file]   (default: .env)
#   bash scripts/run-server.sh .env.ollama.example
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; . "$ENV_FILE"; set +a
  echo "loaded env: $ENV_FILE  (provider=${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:-} apiBase=${SAA_SAMPLE_OPENJIUWEN_API_BASE:-} model=${SAA_SAMPLE_LLM_MODEL:-})"
else
  echo "env file not found: $ENV_FILE - using application.yaml defaults"
fi
cd "$REPO"
./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml spring-boot:run
