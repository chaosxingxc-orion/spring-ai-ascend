#!/usr/bin/env bash
# Smoke scenarios: basic agent loop + optional MCP (requires API on :8080 + WORKMATE_LLM_API_KEY).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

WITH_MCP=false
for arg in "$@"; do
  case "$arg" in
    --with-mcp) WITH_MCP=true ;;
    -h|--help)
      echo "Usage: $0 [--with-mcp]"
      echo "  default: write-hello + prd-draft + hitl-probe (deny)"
      echo "  --with-mcp: also run read-readme-via-mcp.sh (requires WORKMATE_MCP_ENABLED=true at API startup)"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

dogfood_load_env
require_llm
require_api

PASS=0
FAIL=0

run_step() {
  local name="$1"
  shift
  echo
  echo "========== $name =========="
  if "$@"; then
    echo "✓ $name"
    PASS=$((PASS + 1))
  else
    echo "✗ $name"
    FAIL=$((FAIL + 1))
  fi
}

run_step "write-hello" "$SCRIPT_DIR/write-hello.sh"
run_step "PRD draft" "$SCRIPT_DIR/dogfood-prd-draft.sh"
run_step "HITL probe (deny)" "$SCRIPT_DIR/hitl-probe.sh" deny

if $WITH_MCP; then
  require_mcp
  run_step "MCP summary" "$SCRIPT_DIR/read-readme-via-mcp.sh"
fi

echo
echo "=== Smoke: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
