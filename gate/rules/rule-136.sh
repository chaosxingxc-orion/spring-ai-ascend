#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 136 — autoload_tier_integrity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 136 — autoload_tier_integrity (enforcer E184, kernel Rule G-19)
#
# Phase A Wave 5 (BLOCKING from landing). gate/always-loaded-budget.txt MUST
# contain product/PRODUCT.md at a non-zero ceiling AND CLAUDE.md MUST have a
# ceiling <= 12000 bytes. Enforces the Tier-1 product-authority + collab-only
# kernel discipline established by the 2026-05-28 surgery.
# ---------------------------------------------------------------------------
_r136_fail=0
_r136_budget="gate/always-loaded-budget.txt"
if [[ ! -f "$_r136_budget" ]]; then
  fail_rule "autoload_tier_integrity" "$_r136_budget missing -- Rule G-19 / E184"
  _r136_fail=1
else
  if ! grep -qE '^product/PRODUCT\.md=[1-9][0-9]*' "$_r136_budget"; then
    fail_rule "autoload_tier_integrity" "$_r136_budget missing or zero-ceiling product/PRODUCT.md entry -- Rule G-19 / E184"
    _r136_fail=1
  fi
  _r136_claude_ceiling=$(awk -F= '/^CLAUDE\.md=/{print $2; exit}' "$_r136_budget")
  if [[ -z "$_r136_claude_ceiling" ]]; then
    fail_rule "autoload_tier_integrity" "$_r136_budget missing CLAUDE.md entry -- Rule G-19 / E184"
    _r136_fail=1
  elif [[ "$_r136_claude_ceiling" -gt 12000 ]]; then
    fail_rule "autoload_tier_integrity" "$_r136_budget CLAUDE.md ceiling=$_r136_claude_ceiling exceeds 12000 (collab-only kernel discipline) -- Rule G-19 / E184"
    _r136_fail=1
  fi
fi
[[ $_r136_fail -eq 0 ]] && pass_rule "autoload_tier_integrity"

# ---------------------------------------------------------------------------
