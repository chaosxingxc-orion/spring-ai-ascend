#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 11 — contract_spine_tenant_id_required. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 11 — contract_spine_tenant_id_required (enforcer E105)
# Every persistent record under
#   agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/Run.java
# OR
#   agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency/IdempotencyRecord.java
# MUST declare a String tenantId component. Scope path relocated from
# agent-runtime-core to agent-service per ADR-0088 (rc13 dissolution).
# Process-internal opt-out via "// scope: process-internal" same-line comment.
# ---------------------------------------------------------------------------
_r11_fail=0
_r11_roots=(
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/runs'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency'
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
