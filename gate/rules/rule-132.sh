#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 132 — feature_catalog_render_idempotency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 132 — feature_catalog_render_idempotency (enforcer E180, kernel Rule G-13 sibling)
#
# Authority: ADR-0154 (Fact-Layer Authority) + Round-4 second-correction
# request R2 (2026-05-28). Wires gate/lib/render_features_catalog.py
# --check into the canonical gate so feature-catalog drift fails closed.
# Round-1 declared "rendered L1 feature catalogs" as an in-scope drift
# surface; Rounds 1-3 verified the detector existed but never invoked
# it from the canonical sync gate (sibling of Rule G-13.b for templated
# Markdown). This rule closes that gate-coverage gap.
# ---------------------------------------------------------------------------
_r132_fail=0
_r132_render_script="gate/lib/render_features_catalog.py"
if [[ ! -f "$_r132_render_script" ]]; then
  fail_rule "feature_catalog_render_idempotency" "$_r132_render_script missing -- Rule G-13 sibling / E180"
  _r132_fail=1
else
  _r132_out=$(python3 "$_r132_render_script" --check 2>&1)
  _r132_rc=$?
  if [[ $_r132_rc -ne 0 ]]; then
    _r132_first=$(printf '%s' "$_r132_out" | grep "^DRIFT:" | head -1)
    fail_rule "feature_catalog_render_idempotency" "feature catalog drift: ${_r132_first:-rc=$_r132_rc} -- Rule G-13 sibling / E180"
    _r132_fail=1
  fi
fi
[[ $_r132_fail -eq 0 ]] && pass_rule "feature_catalog_render_idempotency"

# ---------------------------------------------------------------------------
