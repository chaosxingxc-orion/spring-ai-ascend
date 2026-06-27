#!/usr/bin/env bash
# Shared helpers for WorkMate dogfood scripts.
set -euo pipefail

WORKMATE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API="${WORKMATE_API_URL:-http://localhost:8080}"

dogfood_load_env() {
  local env_file="$WORKMATE_ROOT/workmate-api/.env.local"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

require_llm() {
  if [[ -z "${WORKMATE_LLM_API_KEY:-}" ]]; then
    echo "Set WORKMATE_LLM_API_KEY (e.g. in workmate-api/.env.local)" >&2
    exit 1
  fi
}

require_api() {
  if curl -sf "$API/actuator/health/readiness" >/dev/null 2>&1; then
    return 0
  fi
  if curl -sf "$API/actuator/health" >/dev/null 2>&1; then
    return 0
  fi
  if curl -sf "$API/api/v1/sessions/limits" >/dev/null 2>&1; then
    return 0
  fi
  echo "workmate-api not reachable at $API" >&2
  exit 1
}

require_expert() {
  local expert_id="$1"
  if curl -sf "$API/api/v1/experts/$expert_id" >/dev/null 2>&1; then
    return 0
  fi
  echo "Expert not found: $expert_id at $API" >&2
  echo "Restart workmate-api to reload office/experts (./scripts/run-local.sh)" >&2
  exit 1
}

require_mcp() {
  dogfood_load_env
  if [[ "${WORKMATE_MCP_ENABLED:-false}" != "true" ]]; then
    cat >&2 <<EOF
WORKMATE_MCP_ENABLED is not true.

MCP is read only at JVM startup. To run MCP dogfood:
  1. Set WORKMATE_MCP_ENABLED=true in workmate-api/.env.local
  2. Restart workmate-api (./scripts/run-local.sh)
  3. Re-run this script
EOF
    exit 1
  fi
}

require_oa_mcp() {
  require_mcp
  if [[ "${WORKMATE_MCP_OA_ENABLED:-false}" != "true" ]]; then
    echo "Set WORKMATE_MCP_OA_ENABLED=true in workmate-api/.env.local and restart API" >&2
    exit 1
  fi
}

json_field() {
  python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"
}

create_session() {
  local title="$1"
  curl -sf -X POST "$API/api/v1/sessions" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"autoArchive\":true}"
}

ensure_session_capacity() {
  local limits_json active max archivable
  limits_json=$(curl -sf "$API/api/v1/sessions/limits")
  read -r active max archivable <<<"$(echo "$limits_json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('activeCount', 0), d.get('maxActive', 50), d.get('archivableCount', 0))
")"
  if [[ "$active" -lt "$max" ]]; then
    return 0
  fi
  if [[ "$archivable" -lt 1 ]]; then
    echo "FAIL: session limit reached ($active/$max) and no archivable sessions" >&2
    exit 1
  fi
  echo "Session cap reached ($active/$max) — auto-archiving 1 slot..."
  curl -sf -X POST "$API/api/v1/sessions/auto-archive" \
    -H 'Content-Type: application/json' \
    -d '{"count":1}' >/dev/null
}

prompt_json() {
  python3 -c "import json,sys; print(json.dumps({'message': sys.stdin.read()}))"
}

send_prompt() {
  local session_id="$1"
  curl -sf -N -X POST "$API/api/v1/sessions/$session_id/prompt" \
    -H 'Content-Type: application/json' \
    -d "$(prompt_json <<<"$2")"
}

send_prompt_quiet() {
  local session_id="$1"
  send_prompt "$session_id" "$2" >/dev/null
}

wait_for_pending_approval() {
  local session_id="$1"
  local max_attempts="${2:-120}"
  local i approval_id
  for ((i = 0; i < max_attempts; i++)); do
    approval_id=$(curl -sf "$API/api/v1/sessions/$session_id/pending-approvals" | python3 -c "
import sys, json
items = json.load(sys.stdin)
print(items[0]['approvalId'] if items else '')
" 2>/dev/null || true)
    if [[ -n "$approval_id" ]]; then
      echo "$approval_id"
      return 0
    fi
    sleep 0.5
  done
  return 1
}

decide_approval() {
  local approval_id="$1"
  local decision="$2"
  curl -sf -X POST "$API/api/v1/approvals/$approval_id" \
    -H 'Content-Type: application/json' \
    -d "{\"decision\":\"$decision\",\"always\":false}"
}
