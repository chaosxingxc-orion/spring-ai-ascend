#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 46 — cursor_flow_documented. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 46 — cursor_flow_documented (enforcer E65, Rule 36 / P-F)
#
# docs/contracts/openapi-v1.yaml MUST declare a TaskCursor schema in
# components.schemas AND a top-level x-cursor-flow: annotation. Either alone
# is insufficient — the annotation declares INTENT, the schema declares the
# WIRE shape; both are needed for an LLM or codegen consumer to act on it.
# ---------------------------------------------------------------------------
_r46_fail=0
_r46_path="docs/contracts/openapi-v1.yaml"
if [[ ! -f "$_r46_path" ]]; then
  fail_rule "cursor_flow_documented" "$_r46_path missing"
  _r46_fail=1
else
  if ! grep -qE '^[[:space:]]+TaskCursor:[[:space:]]*$' "$_r46_path"; then
    fail_rule "cursor_flow_documented" "$_r46_path does not declare a TaskCursor schema in components.schemas — Cursor Flow wire shape missing"
    _r46_fail=1
  fi
  if ! grep -qE '^x-cursor-flow:[[:space:]]*$' "$_r46_path"; then
    fail_rule "cursor_flow_documented" "$_r46_path missing top-level x-cursor-flow: annotation — Cursor Flow intent not declared"
    _r46_fail=1
  fi
fi
if [[ $_r46_fail -eq 0 ]]; then pass_rule "cursor_flow_documented"; fi

# ---------------------------------------------------------------------------
