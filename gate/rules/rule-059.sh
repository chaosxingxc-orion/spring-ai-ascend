#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 59 — evolution_scope_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 59 — evolution_scope_yaml_present_and_wellformed (enforcer E86, Rule 47 / P-M, ADR-0075)
#
# docs/governance/evolution-scope.v1.yaml MUST exist with schema: header, three
# discriminator blocks (in_scope, out_of_scope_default, opt_in_export), the
# first two non-empty, and opt_in_export referencing telemetry-export.v1.yaml
# (W3 placeholder). Drift would let the evolution plane silently widen its
# surface beyond the server-sovereign boundary.
# ---------------------------------------------------------------------------
_r59_fail=0
_r59_path="docs/governance/evolution-scope.v1.yaml"
if [[ ! -f "$_r59_path" ]]; then
  fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing -- Rule 47 / P-M evolution scope unenforced"
  _r59_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+evolution-scope/v1[[:space:]]*$' "$_r59_path"; then
    fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing 'schema: evolution-scope/v1' header"
    _r59_fail=1
  fi
  for _r59_block in in_scope out_of_scope_default opt_in_export; do
    if ! grep -qE "^${_r59_block}:" "$_r59_path"; then
      fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing top-level discriminator block '${_r59_block}:'"
      _r59_fail=1
    fi
  done
  for _r59_block in in_scope out_of_scope_default; do
    _r59_count=$(awk -v b="^${_r59_block}:" '$0 ~ b {f=1; next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/ {n++} END{print n+0}' "$_r59_path")
    if [[ "${_r59_count:-0}" -lt 1 ]]; then
      fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path block '${_r59_block}:' is empty -- at least one entry required"
      _r59_fail=1
    fi
  done
  if ! grep -qE 'contract_required:[[:space:]]+telemetry-export\.v1\.yaml' "$_r59_path"; then
    fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path opt_in_export.contract_required must reference 'telemetry-export.v1.yaml' (W3 placeholder)"
    _r59_fail=1
  fi
fi
if [[ $_r59_fail -eq 0 ]]; then pass_rule "evolution_scope_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
