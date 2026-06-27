#!/usr/bin/env bash
# v0.3 W30–W35 basics sign-off: offline validators + live API probes (no LLM by default)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

WITH_LIVE=false
for arg in "$@"; do
  case "$arg" in
    --live) WITH_LIVE=true ;;
    -h|--help)
      echo "Usage: $0 [--live]"
      echo "  default: offline validator fixtures + Maven memory unit tests"
      echo "  --live:  also run live API probes (audit, memory, limits, files) — requires :8080"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

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

WORKMATE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

run_step "offline validators" "$SCRIPT_DIR/run-dogfood-validators.sh"

run_step "W32 memory offline tests" bash -c "
  cd '$WORKMATE_ROOT' && \
  ../../mvnw -q -f workmate-api/pom.xml \
    -Dtest=MemorySummarizerHeuristicTest,MemoryControllerIntegrationTest test
"

if $WITH_LIVE; then
  dogfood_load_env
  require_api

  run_step "W31 audit chain" "$SCRIPT_DIR/dogfood-audit-chain.sh"
  run_step "W32 memory live" "$SCRIPT_DIR/dogfood-memory.sh"
  run_step "W33 session limits" "$SCRIPT_DIR/dogfood-session-limits.sh"

  run_step "W35 files probe" "$SCRIPT_DIR/dogfood-files-probe.sh"
fi

echo
echo "=== v0.3 basics: $PASS passed, $FAIL failed ==="
[[ "$FAIL" -eq 0 ]]
