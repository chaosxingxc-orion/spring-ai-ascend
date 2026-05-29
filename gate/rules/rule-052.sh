#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 52 — sandbox_policies_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 52 — sandbox_policies_yaml_present_and_wellformed (enforcer E71, Rule 42 / P-L)
#
# docs/governance/sandbox-policies.yaml MUST exist with default_policy:
# declaring all 6 required keys.
# ---------------------------------------------------------------------------
_r52_fail=0
_r52_path="docs/governance/sandbox-policies.yaml"
if [[ ! -f "$_r52_path" ]]; then
  fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path missing — Rule 42 / P-L ironclad rule unenforced"
  _r52_fail=1
else
  if ! grep -qE '^default_policy:[[:space:]]*$' "$_r52_path"; then
    fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path missing default_policy: block"
    _r52_fail=1
  else
    for _r52_key in outbound_network filesystem_read filesystem_write cpu_cap_millicores memory_cap_megabytes wall_clock_cap_seconds; do
      if ! grep -qE "^[[:space:]]+${_r52_key}:" "$_r52_path"; then
        fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path default_policy missing required key: $_r52_key"
        _r52_fail=1
      fi
    done
  fi
fi
if [[ $_r52_fail -eq 0 ]]; then pass_rule "sandbox_policies_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
