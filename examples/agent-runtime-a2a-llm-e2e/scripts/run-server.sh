#!/usr/bin/env bash
# Load an env file, install agent-runtime, then start the example A2A + AgentScope server.
# Usage: bash scripts/run-server.sh [env-file] [agent-profile]
#   bash scripts/run-server.sh .env                        # default agentscope
#   bash scripts/run-server.sh .env retail-wealth-advisor  # retail wealth advisor
#   bash scripts/run-server.sh .env.ollama.example
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env}"
AGENT_PROFILE="${2:-agentscope}"
if [[ -f "$ENV_FILE" ]]; then
  set -a; . "$ENV_FILE"; set +a
  echo "loaded env: $ENV_FILE  (agent=$AGENT_PROFILE apiBase=${SAA_SAMPLE_AGENTSCOPE_API_BASE:-} model=${SAA_SAMPLE_LLM_MODEL:-})"
else
  echo "env file not found: $ENV_FILE — using application.yaml defaults"
fi
cd "$REPO"
./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.a2a.agent=$AGENT_PROFILE"
