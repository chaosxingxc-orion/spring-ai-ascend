#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 50 — rls_for_new_tenant_tables. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 50 — rls_for_new_tenant_tables (enforcer E69, Rule 40 / P-J)
#
# Every Flyway migration creating a table with a tenant_id column MUST
# enable RLS in the same file (ENABLE ROW LEVEL SECURITY) OR be listed in
# gate/rls-baseline-grandfathered.txt.
# ---------------------------------------------------------------------------
_r50_fail=0
_r50_baseline="gate/rls-baseline-grandfathered.txt"
_r50_baseline_paths=""
if [[ -f "$_r50_baseline" ]]; then
  _r50_baseline_paths="$(grep -vE '^[[:space:]]*(#|$)' "$_r50_baseline" 2>/dev/null || true)"
fi
while IFS= read -r _r50_mig; do
  [[ -z "$_r50_mig" ]] && continue
  # Does this migration create a table with tenant_id?
  if ! grep -qE 'tenant_id[[:space:]]+(UUID|uuid|VARCHAR|varchar|TEXT|text)' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  if ! grep -qiE 'CREATE[[:space:]]+TABLE' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  # Has it enabled RLS in the same file?
  if grep -qiE 'ENABLE[[:space:]]+ROW[[:space:]]+LEVEL[[:space:]]+SECURITY' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  # Is it grandfathered?
  _r50_norm="$(printf '%s' "$_r50_mig" | sed -E 's|^\./||')"
  if printf '%s\n' "$_r50_baseline_paths" | grep -qFx "$_r50_norm"; then
    continue
  fi
  fail_rule "rls_for_new_tenant_tables" "$_r50_mig creates a tenant-scoped table without ENABLE ROW LEVEL SECURITY; not in $_r50_baseline either"
  _r50_fail=1
done <<< "$(find agent-service/src/main/resources/db/migration agent-service/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' 2>/dev/null || true)"
if [[ $_r50_fail -eq 0 ]]; then pass_rule "rls_for_new_tenant_tables"; fi

# ---------------------------------------------------------------------------
