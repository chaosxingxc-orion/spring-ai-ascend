#!/usr/bin/env bash
# Load an env file, install agent-runtime into the local Maven repo, then run
# the single-runtime/single-openJiuwen-agent A2A E2E suite.
#
# Usage: bash scripts/test-e2e.sh [env-file]   (default: .env)
#   bash scripts/test-e2e.sh .env.ollama.example
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; . "$ENV_FILE"; set +a
  echo "loaded env: $ENV_FILE  (provider=${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:-} apiBase=${SAA_SAMPLE_OPENJIUWEN_API_BASE:-} model=${SAA_SAMPLE_LLM_MODEL:-})"
else
  echo "env file not found: $ENV_FILE - using process env / application.yaml defaults"
fi
if [[ -z "${SAA_SAMPLE_LLM_API_KEY:-}" ]]; then
  echo "WARNING: SAA_SAMPLE_LLM_API_KEY is blank - the real-LLM branch will be SKIPPED (assumeTrue)."
fi
cd "$REPO"
./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml test
