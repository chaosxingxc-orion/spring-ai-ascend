#!/usr/bin/env bash
# Load an env file, install agent-runtime into the local Maven repo, then run the
# example A2A + AgentScope E2E suite (real-LLM tests run when SAA_SAMPLE_LLM_API_KEY
# is set).
#
# Usage: bash scripts/test-e2e.sh [env-file]   (default: .env)
#   bash scripts/test-e2e.sh .env.ollama.example
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; . "$ENV_FILE"; set +a
  echo "loaded env: $ENV_FILE  (apiBase=${SAA_SAMPLE_AGENTSCOPE_API_BASE:-} model=${SAA_SAMPLE_LLM_MODEL:-})"
else
  echo "env file not found: $ENV_FILE — using process env / application.yaml defaults"
fi
if [[ -z "${SAA_SAMPLE_LLM_API_KEY:-}" ]]; then
  echo "WARNING: SAA_SAMPLE_LLM_API_KEY is blank — the real-LLM e2e branch will be SKIPPED (assumeTrue)."
fi
cd "$REPO"
./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
# The example pom defaults skipTests=true for reactor hygiene; this script's whole
# purpose is to run the suite, so the override is mandatory here.
LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test -DskipTests=false | tee "$LOG_FILE"
if grep -q "Tests are skipped." "$LOG_FILE"; then
  echo "ERROR: surefire skipped the tests — the E2E suite did not run." >&2
  exit 1
fi
