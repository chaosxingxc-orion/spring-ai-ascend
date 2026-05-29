#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 55 — engine_envelope_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 55 — engine_envelope_yaml_present_and_wellformed (enforcer E76, Rule 43 / P-M, ADR-0072)
#
# docs/contracts/engine-envelope.v1.yaml is the single-source-of-truth for
# the EngineEnvelope shape. Required: schema: header, known_engines: block,
# at least one entry carrying an id:.
# ---------------------------------------------------------------------------
_r55_fail=0
_r55_path="docs/contracts/engine-envelope.v1.yaml"
if [[ ! -f "$_r55_path" ]]; then
  fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing -- Rule 43 / P-M envelope schema unenforced"
  _r55_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+engine-envelope/v1[[:space:]]*$' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing 'schema: engine-envelope/v1' header"
    _r55_fail=1
  fi
  if ! grep -qE '^known_engines:[[:space:]]*$' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing known_engines: block"
    _r55_fail=1
  fi
  if ! grep -qE '^[[:space:]]+- id:[[:space:]]+\S+' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path known_engines: contains no '- id:' entry"
    _r55_fail=1
  fi
fi
if [[ $_r55_fail -eq 0 ]]; then pass_rule "engine_envelope_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
