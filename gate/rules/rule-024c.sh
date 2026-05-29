#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 24.c — runlifecycle_cancel_reauthz_shipped. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 24.c — runlifecycle_cancel_reauthz_shipped (enforcer E106)
# agent-service RunController MUST expose POST /v1/runs/{runId}/cancel
# with tenant re-validation + RunStateMachine validation + audit log.
# ---------------------------------------------------------------------------
_r24_fail=0
_r24_path='agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java'
if [[ ! -f "$_r24_path" ]]; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path missing — Rule 24.c expects RunController to host the cancel surface"
  _r24_fail=1
elif ! grep -qE '/v1/runs/\{[a-zA-Z]+\}/cancel' "$_r24_path" 2>/dev/null; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path missing the POST /v1/runs/{runId}/cancel mapping"
  _r24_fail=1
elif ! grep -qE 'tenantId\(\)' "$_r24_path" 2>/dev/null; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path cancel handler does not re-validate tenantId"
  _r24_fail=1
fi
if [[ $_r24_fail -eq 0 ]]; then pass_rule "runlifecycle_cancel_reauthz_shipped"; fi

# ---------------------------------------------------------------------------
