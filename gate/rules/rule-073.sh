#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 73 — gate_config_well_formed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 73 — gate_config_well_formed (enforcer E103)
#
# Sources gate/lib/load_config.sh and runs the validator. Fails if:
#   - gate/config.yaml or gate/config.schema.yaml missing
#   - YAML parser detected malformed input (__ERROR__ sentinel)
#   - Required top-level key missing
#   - Type / range / enum violation on any validated leaf
#
# The validator implementation lives in gate/lib/load_config.sh
# (gate_validate_config_against_schema). This rule is the gate-side wrapper.
# ---------------------------------------------------------------------------
_r73_fail=0
_r73_loader='gate/lib/load_config.sh'
_r73_config='gate/config.yaml'
_r73_schema='gate/config.schema.yaml'
if [[ ! -f "$_r73_loader" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_loader missing -- cannot validate gate/config.yaml"
  _r73_fail=1
elif [[ ! -f "$_r73_config" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_config missing"
  _r73_fail=1
elif [[ ! -f "$_r73_schema" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_schema missing"
  _r73_fail=1
else
  # Run validation in a subshell so we don't pollute the main shell with
  # the loader's exported GATE_* variables. Capture VALID + ERRORS via stdout.
  _r73_result=$(bash -c '
    source '"'$_r73_loader'"'
    gate_load_config >/dev/null 2>&1
    gate_validate_config_against_schema >/dev/null 2>&1
    printf "%s\n" "${GATE_CONFIG_VALID:-false}"
    printf "%s" "${GATE_CONFIG_ERRORS:-}"
  ')
  _r73_valid=$(printf '%s\n' "$_r73_result" | head -1)
  _r73_errors=$(printf '%s\n' "$_r73_result" | tail -n +2)
  if [[ "$_r73_valid" == "true" ]]; then
    pass_rule "gate_config_well_formed"
  else
    fail_rule "gate_config_well_formed" "$(printf '%s' "$_r73_errors" | tr '\n' ';')"
    _r73_fail=1
  fi
fi

# ===========================================================================
# Linux-first dev environment policy (PR-E7, 2026-05-18)
# Authority: docs/governance/rules/rule-74.md + docs/governance/dev-environment.md
# ===========================================================================

# ---------------------------------------------------------------------------
