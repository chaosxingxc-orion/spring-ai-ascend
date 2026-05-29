#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 51 — skill_capacity_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 51 — skill_capacity_yaml_present_and_wellformed (enforcer E70, Rule 41 / P-K)
#
# docs/governance/skill-capacity.yaml MUST exist; each skill row MUST have
# capacity_per_tenant + global_capacity + queue_strategy ∈ {suspend, fail}.
# ---------------------------------------------------------------------------
_r51_fail=0
_r51_path="docs/governance/skill-capacity.yaml"
if [[ ! -f "$_r51_path" ]]; then
  fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path missing — Rule 41 / P-K ironclad rule unenforced"
  _r51_fail=1
else
  # Count skill ids vs required-field occurrences. Each id row should be
  # followed by capacity_per_tenant, global_capacity, queue_strategy.
  _r51_ids="$(grep -cE '^[[:space:]]+- id:[[:space:]]+' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_caps_per="$(grep -cE '^[[:space:]]+capacity_per_tenant:' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_caps_global="$(grep -cE '^[[:space:]]+global_capacity:' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_queue="$(grep -cE '^[[:space:]]+queue_strategy:[[:space:]]+(suspend|fail)([[:space:]#].*)?$' "$_r51_path" 2>/dev/null || echo 0)"
  if [[ "$_r51_ids" -lt 1 ]]; then
    fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path declares zero skills — at least one required"
    _r51_fail=1
  fi
  if [[ "$_r51_caps_per" -ne "$_r51_ids" ]] || [[ "$_r51_caps_global" -ne "$_r51_ids" ]] || [[ "$_r51_queue" -ne "$_r51_ids" ]]; then
    fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path schema-incomplete: $_r51_ids skill ids vs $_r51_caps_per capacity_per_tenant / $_r51_caps_global global_capacity / $_r51_queue queue_strategy(suspend|fail)"
    _r51_fail=1
  fi
fi
if [[ $_r51_fail -eq 0 ]]; then pass_rule "skill_capacity_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
