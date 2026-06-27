#!/usr/bin/env bash
# Run offline dogfood validator fixture tests (no LLM).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

FAIL=0
for test in \
  test_dogfood_message_bus_validate.py \
  test_dogfood_gv_validate.py \
  test_dogfood_ss_validate.py \
  test_dogfood_orchestrator_validate.py \
  test_dogfood_teamagent_validate.py \
  test_dogfood_gpt_researcher_validate.py \
  test_dogfood_agent_team_validate.py \
  test_dogfood_audit_chain_validate.py; do
  echo "========== $test =========="
  if python3 "$SCRIPT_DIR/$test"; then
    echo "✓ $test"
  else
    echo "✗ $test"
    FAIL=$((FAIL + 1))
  fi
done

if [[ "$FAIL" -gt 0 ]]; then
  echo "=== Validator fixtures: $FAIL failed ==="
  exit 1
fi
echo "=== Validator fixtures: all passed ==="
