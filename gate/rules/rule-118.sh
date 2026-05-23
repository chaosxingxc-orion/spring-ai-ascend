#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 118 — l1_dev_view_code_mapping. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 118 — l1_dev_view_code_mapping (enforcer E166)
# rc28 fix (NEW-3): fail-closed when helper missing instead of silent pass.
_r118_fail=0
if ! command -v check_l1_dev_view_tree >/dev/null 2>&1; then
  fail_rule "l1_dev_view_code_mapping" "helper-missing: gate/lib/check_l1_dev_view_tree.sh not sourced -- Rule G-1.1.a / E166"
  _r118_fail=1
else
  _r118_out=$(check_l1_dev_view_tree 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ "$_s" == "FAIL" ]] || continue
    fail_rule "l1_dev_view_code_mapping" "$_f: $_d -- Rule G-1.1.a / E166"
    _r118_fail=1
  done <<< "$_r118_out"
fi
[[ $_r118_fail -eq 0 ]] && pass_rule "l1_dev_view_code_mapping"

