#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28i — plan_enforcer_table_in_sync. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28i — plan_enforcer_table_in_sync (enforcer E32)
# The L1 plan §11 table E<n> IDs MUST equal the set of `id:` fields in
# docs/governance/enforcers.yaml. The plan and the index are two views of the
# same truth.
# ---------------------------------------------------------------------------
_r28i_fail=0
_plan_file="$HOME/.claude/plans/l1-modular-russell.md"
# Fall back to alternative locations (Windows: /d/.claude/plans/...).
if [[ ! -f "$_plan_file" ]]; then
  _plan_file="/d/.claude/plans/l1-modular-russell.md"
fi
if [[ ! -f "$_plan_file" ]]; then
  # Plan lives outside the repo (user home). Skip with a NOTE.
  pass_rule "plan_enforcer_table_in_sync"
else
  _yaml_ids=$(grep -E '^- id: E[0-9]+' "$_efile" 2>/dev/null | sed -E 's/^- id:\s*//' | sort -u)
  _plan_ids=$(grep -oE '\| E[0-9]+ \|' "$_plan_file" 2>/dev/null | sed -E 's/\| (E[0-9]+) \|/\1/' | sort -u)
  if [[ -n "$_plan_ids" ]] && [[ "$_yaml_ids" != "$_plan_ids" ]]; then
    fail_rule "plan_enforcer_table_in_sync" "plan §11 enforcer IDs and enforcers.yaml IDs diverge. Per Rule 28i / enforcer E32."
    _r28i_fail=1
  fi
  if [[ $_r28i_fail -eq 0 ]]; then pass_rule "plan_enforcer_table_in_sync"; fi
fi

# ---------------------------------------------------------------------------
