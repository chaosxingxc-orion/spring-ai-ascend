#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 58 — s2c_callback_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 58 — s2c_callback_yaml_present_and_wellformed (enforcer E81, Rule 46 / P-M, ADR-0074)
#
# docs/contracts/s2c-callback.v1.yaml MUST exist with schema: header, a request:
# block listing the 6 mandatory fields (callback_id, server_run_id, capability_ref,
# request_payload, trace_id, idempotency_key), a response: block, and an
# outcome_values: block declaring exactly {ok, error, timeout}.
# Drift would let an S2C transport accept envelopes that violate the Phase 3a
# cross-rule audit's propagation contract (response doctrine §5.2).
# ---------------------------------------------------------------------------
_r58_fail=0
_r58_path="docs/contracts/s2c-callback.v1.yaml"
if [[ ! -f "$_r58_path" ]]; then
  fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing -- Rule 46 / P-M S2C callback contract unenforced"
  _r58_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+s2c-callback/v1[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing 'schema: s2c-callback/v1' header"
    _r58_fail=1
  fi
  if ! grep -qE '^request:[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing request: block"
    _r58_fail=1
  fi
  if ! grep -qE '^response:[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing response: block"
    _r58_fail=1
  fi
  # 6 mandatory request fields per audit §5.2
  for _r58_field in callback_id server_run_id capability_ref request_payload trace_id idempotency_key; do
    if ! grep -qE "^[[:space:]]+- ${_r58_field}([[:space:]]|#|\$)" "$_r58_path"; then
      fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing mandatory request field: ${_r58_field}"
      _r58_fail=1
    fi
  done
  # Outcome enum closed at exactly ok | error | timeout
  for _r58_oc in ok error timeout; do
    if ! grep -qE "^[[:space:]]+- ${_r58_oc}([[:space:]]|#|\$)" "$_r58_path"; then
      fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path outcome_values missing entry: ${_r58_oc}"
      _r58_fail=1
    fi
  done
fi
if [[ $_r58_fail -eq 0 ]]; then pass_rule "s2c_callback_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
