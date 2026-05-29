#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 130 — feature_lifecycle_validity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 130 — feature_lifecycle_validity (enforcer E178, kernel Rule G-14)
#
# Authority: ADR-0151 (L1 Feature Registry canonical schema, W1) +
#            ADR-0153 (L1 Feature Registry closure, W5).
#
# Sub-clause .a — every SAA Feature element in features.dsl declares
#   saa.status ∈ the 9-state lifecycle set. Implemented at W5
#   (advisory→blocking flip). Sub-clauses .b/.c/.d (git-history
#   transition validity, shipped-requires-verification,
#   deprecated-requires-sunset) remain advisory through W5 and ship
#   blocking in a follow-up sub-wave.
# ---------------------------------------------------------------------------
_r130_fail=0
_r130_dsl="architecture/features/features.dsl"
_r130_valid_states="proposed accepted design_only ready_for_impl implemented_unverified test_verified shipped deprecated removed"
if [[ ! -f "$_r130_dsl" ]]; then
  fail_rule "feature_lifecycle_validity" "$_r130_dsl missing -- Rule G-14.a / E178"
  _r130_fail=1
else
  # Walk every "saa.status" "X" property and check X ∈ allowed set.
  while IFS= read -r _r130_status; do
    _r130_status=$(echo "$_r130_status" | tr -d '\r')
    if [[ -z "$_r130_status" ]]; then continue; fi
    _r130_match=0
    for _s in $_r130_valid_states; do
      if [[ "$_r130_status" == "$_s" ]]; then _r130_match=1; break; fi
    done
    if [[ $_r130_match -eq 0 ]]; then
      fail_rule "feature_lifecycle_validity" "$_r130_dsl declares saa.status \"$_r130_status\" which is not in the 9-state lifecycle (proposed/accepted/design_only/ready_for_impl/implemented_unverified/test_verified/shipped/deprecated/removed) -- Rule G-14.a / E178"
      _r130_fail=1
    fi
  done < <(grep -oE '"saa\.status"[[:space:]]+"[^"]+"' "$_r130_dsl" | sed -E 's/.*"saa\.status"[[:space:]]+"([^"]+)".*/\1/')
fi
if [[ $_r130_fail -eq 0 ]]; then
  pass_rule "feature_lifecycle_validity"
fi

# ---------------------------------------------------------------------------
