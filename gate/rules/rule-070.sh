#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 70 — always_loaded_budget_enforced. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 70 — always_loaded_budget_enforced (enforcer E100)
#
# Invokes gate/measure_always_loaded_tokens.sh which walks every file listed
# in gate/always-loaded-budget.txt and fails if any file exceeds its ceiling.
# This is the primary defence against CLAUDE.md regressing back to its
# pre-shrink size after PR1 lands.
# ---------------------------------------------------------------------------
_r70_fail=0
_r70_script='gate/measure_always_loaded_tokens.sh'
if [[ ! -f "$_r70_script" ]]; then
  fail_rule "always_loaded_budget_enforced" "$_r70_script missing"
  _r70_fail=1
else
  _r70_out=$(bash "$_r70_script" 2>&1)
  _r70_rc=$?
  if [[ $_r70_rc -ne 0 ]]; then
    # Extract just the OVER / MISSING lines for the error message.
    _r70_violations=$(printf '%s\n' "$_r70_out" | grep -E '(OVER|MISSING)' | tr '\n' ';' | sed 's/;$//')
    fail_rule "always_loaded_budget_enforced" "${_r70_violations:-budget script exited $_r70_rc}"
    _r70_fail=1
  fi
fi
if [[ $_r70_fail -eq 0 ]]; then pass_rule "always_loaded_budget_enforced"; fi

# ---------------------------------------------------------------------------
