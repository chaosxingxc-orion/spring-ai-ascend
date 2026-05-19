#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 11 — contract_spine_tenant_id_required. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 11 — contract_spine_tenant_id_required (enforcer E105)
# Every persistent record under
#   agent-service/src/main/java/ascend/springai/service/runtime/runs/Run.java
# OR
#   agent-service/src/main/java/ascend/springai/service/runtime/idempotency/IdempotencyRecord.java
# MUST declare a String tenantId component. Scope path relocated from
# agent-runtime-core to agent-service per ADR-0088 (rc13 dissolution).
# Process-internal opt-out via "// scope: process-internal" same-line comment.
# ---------------------------------------------------------------------------
_r11_fail=0
_r11_roots=(
  'agent-service/src/main/java/ascend/springai/service/runtime/runs'
  'agent-service/src/main/java/ascend/springai/service/runtime/idempotency'
)
for _r11_root in "${_r11_roots[@]}"; do
  [[ -d "$_r11_root" ]] || continue
  _r11_hits="$(grep -rEln 'public[[:space:]]+record[[:space:]]' "$_r11_root" 2>/dev/null || true)"
  while IFS= read -r _r11_f; do
    [[ -z "$_r11_f" ]] && continue
    if grep -qE 'scope:[[:space:]]*process-internal' "$_r11_f" 2>/dev/null; then
      continue
    fi
    if ! grep -qE 'String[[:space:]]+tenantId' "$_r11_f" 2>/dev/null; then
      fail_rule "contract_spine_tenant_id_required" "$_r11_f declares a record without a String tenantId component (Rule R-C.c / E105)"
      _r11_fail=1
    fi
  done <<< "$_r11_hits"
done
if [[ $_r11_fail -eq 0 ]]; then pass_rule "contract_spine_tenant_id_required"; fi

# ---------------------------------------------------------------------------
# Rule 24.c — runlifecycle_cancel_reauthz_shipped (enforcer E106)
# agent-service RunController MUST expose POST /v1/runs/{runId}/cancel
# with tenant re-validation + RunStateMachine validation + audit log.
# ---------------------------------------------------------------------------
_r24_fail=0
_r24_path='agent-service/src/main/java/ascend/springai/service/platform/web/runs/RunController.java'
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
# Rule 29.c — quickstart_smoke_job_present (enforcer E107)
# .github/workflows/ci.yml MUST contain a job named quickstart-smoke that
# polls /v1/health.
# ---------------------------------------------------------------------------
_r29c_fail=0
_r29c_path='.github/workflows/ci.yml'
if [[ ! -f "$_r29c_path" ]]; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path missing — Rule 29.c requires a CI workflow"
  _r29c_fail=1
elif ! grep -qE '^[[:space:]]*quickstart-smoke:' "$_r29c_path" 2>/dev/null; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path missing job 'quickstart-smoke' — Rule 29.c"
  _r29c_fail=1
elif ! grep -qF '/v1/health' "$_r29c_path" 2>/dev/null; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path quickstart-smoke job does not poll /v1/health"
  _r29c_fail=1
fi
if [[ $_r29c_fail -eq 0 ]]; then pass_rule "quickstart_smoke_job_present"; fi

# ---------------------------------------------------------------------------
