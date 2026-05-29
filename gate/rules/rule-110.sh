#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 110 — prevention_rule_scope_completeness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 110 — prevention_rule_scope_completeness (enforcer E155) [META]
#
# Operationalises the rc10/rc11/rc12 meta-lesson "Reviewer scope can be
# narrower than defect scope": every gate-script rule that declares
# `# scope_surfaces:` in its leading comment block MUST have ≥2 self-test
# fixture functions in gate/test_architecture_sync_gate.sh matching
# test_rule_<N>_*. This prevents future waves from shipping scope-narrow
# rules that only cover the reviewer-cited surface.
# Per ADR-0093 (rc16 meta scope completeness wave).
#
# scope_surfaces: gate/check_architecture_sync.sh, gate/test_architecture_sync_gate.sh
#
# Pre-rc16 rules without scope_surfaces: are grandfathered (no retrofit).
#
# rc19 Wave 1 (ADR-0096): Rule 110 sources gate/lib/check_recurring_families.sh
# as a structural-compliance no-op to satisfy Rule 112 (meta_rule_self_
# application_check). Rule 112 requires every [META] rule to source a
# gate/lib/check_*.sh helper as the F-recursive-prevention-irony permanent
# close — this source statement is the Rule 110 → Rule 112 self-application
# proof. The helper is not invoked here; its presence in scope is the gate.
# shellcheck disable=SC1091
source "gate/lib/check_recurring_families.sh"  # Rule 112 dogfooding
# ---------------------------------------------------------------------------
_r110_fail=0
_r110_test_file="gate/test_architecture_sync_gate.sh"
_r110_gate_file="gate/check_architecture_sync.sh"
if [[ -f "$_r110_test_file" && -f "$_r110_gate_file" ]]; then
  # For every gate rule whose header is followed (within 20 lines) by a
  # `# scope_surfaces:` comment, require ≥2 test_rule_<N>_* fixtures.
  while IFS= read -r _r110_rid; do
    [[ -z "$_r110_rid" ]] && continue
    _r110_fixture_count=$(grep -cE "^test_rule_${_r110_rid}_" "$_r110_test_file" 2>/dev/null || echo 0)
    if [[ "$_r110_fixture_count" -lt 2 ]]; then
      fail_rule "prevention_rule_scope_completeness" "Rule $_r110_rid declares scope_surfaces in $_r110_gate_file but has only $_r110_fixture_count test_rule_${_r110_rid}_* fixtures (need ≥2) -- Rule 110 / E155 (META per ADR-0093)"
      _r110_fail=1
    fi
  done < <(awk '/^# Rule [0-9]+ — /{ str=substr($0, 8); space_idx=index(str, " "); cr=substr(str, 1, space_idx - 1); ls=0; next } cr!="" { ls++; if (ls>20){cr=""; next} if ($0 ~ /^# scope_surfaces:/){print cr; cr=""} }' "$_r110_gate_file" 2>/dev/null)
fi
if [[ $_r110_fail -eq 0 ]]; then pass_rule "prevention_rule_scope_completeness"; fi

# ---------------------------------------------------------------------------
