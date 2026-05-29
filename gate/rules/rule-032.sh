#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 32 — competitive_baselines_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 32 — competitive_baselines_present_and_wellformed (enforcer E50, ADR-0065)
#
# docs/governance/competitive-baselines.yaml MUST exist and MUST declare four
# dimensions: performance, cost, developer_onboarding, governance.
# ---------------------------------------------------------------------------
_r32_fail=0
_baseline_file="docs/governance/competitive-baselines.yaml"
if [[ ! -f "$_baseline_file" ]]; then
  fail_rule "competitive_baselines_present_and_wellformed" "$_baseline_file is missing (CLAUDE.md Rule 30 / ADR-0065)"
  _r32_fail=1
else
  for _dim in performance cost developer_onboarding governance; do
    if ! grep -qE "^[[:space:]]*${_dim}:" "$_baseline_file" 2>/dev/null; then
      fail_rule "competitive_baselines_present_and_wellformed" "$_baseline_file missing required dimension '${_dim}'"
      _r32_fail=1
    fi
  done
fi
if [[ $_r32_fail -eq 0 ]]; then pass_rule "competitive_baselines_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
