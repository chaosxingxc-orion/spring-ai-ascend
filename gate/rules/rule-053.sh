#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 53 — cursor_flow_integration_test_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 53 — cursor_flow_integration_test_present (enforcer E72, Rule 36.b / P-F, ADR-0070)
#
# A Phase 8 / Rule 36.b integration test MUST exist that drives the cursor-flow
# contract end-to-end: POST /v1/runs returns 202 within 200 ms even when the
# registered AsyncRunDispatcher synchronously blocks. The gate greps for the
# canonical method name + the elapsed-millis assertion shape so any future
# refactor that drops this coverage fails the gate.
# ---------------------------------------------------------------------------
_r53_fail=0
_r53_path="agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunCursorFlowIT.java"
if [[ ! -f "$_r53_path" ]]; then
  fail_rule "cursor_flow_integration_test_present" "$_r53_path missing — Rule 36.b / P-F integration test not landed"
  _r53_fail=1
else
  if ! grep -qE 'void[[:space:]]+createReturns202WithCursorWithin200ms[[:space:]]*\(' "$_r53_path"; then
    fail_rule "cursor_flow_integration_test_present" "$_r53_path missing canonical method createReturns202WithCursorWithin200ms() — Rule 36.b cursor flow IT contract"
    _r53_fail=1
  fi
  if ! grep -qE 'isLessThan\([[:space:]]*200L?[[:space:]]*\)' "$_r53_path"; then
    fail_rule "cursor_flow_integration_test_present" "$_r53_path missing elapsed-ms < 200 assertion — Rule 36.b requires response within 200 ms"
    _r53_fail=1
  fi
fi
if [[ $_r53_fail -eq 0 ]]; then pass_rule "cursor_flow_integration_test_present"; fi

# ---------------------------------------------------------------------------
