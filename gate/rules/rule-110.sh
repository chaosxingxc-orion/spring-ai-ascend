#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 110 — prevention_rule_scope_completeness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 110 — prevention_rule_scope_completeness (enforcer E155) [META]
#
# Operationalises the rc10/rc11/rc12 meta-lesson "Reviewer scope can be
# narrower than defect scope": every NEW prevention rule (rc16+ with
# scope_surfaces: declared in its card frontmatter) MUST have ≥2 self-test
# fixture functions in gate/test_architecture_sync_gate.sh. This prevents
# future waves from shipping scope-narrow rules that only cover the
# reviewer-cited surface.
# Per ADR-0093 (rc16 meta scope completeness wave).
#
# Scope dimensions (self-applied):
#   - docs/governance/rules/rule-*.md (cards with scope_surfaces:)
#   - gate/test_architecture_sync_gate.sh (test_rule_<id>_* function count)
#
# Pre-rc16 rules without scope_surfaces: are grandfathered (no retrofit).
# ---------------------------------------------------------------------------
_r110_fail=0
_r110_test_file="gate/test_architecture_sync_gate.sh"
if [[ -f "$_r110_test_file" ]]; then
  for _r110_card in docs/governance/rules/rule-*.md; do
    [[ -f "$_r110_card" ]] || continue
    # Check for scope_surfaces: in frontmatter
    if ! head -30 "$_r110_card" 2>/dev/null | grep -qE '^scope_surfaces:'; then
      continue
    fi
    # Extract rule_id from frontmatter
    _r110_rid=$(head -30 "$_r110_card" 2>/dev/null | grep -E '^rule_id:' | head -1 | awk '{print $2}' | tr -d '"')
    [[ -z "$_r110_rid" ]] && continue
    # Normalize rule_id for fixture function name (numeric or namespaced)
    # Count fixture functions matching test_rule_<id>_*
    _r110_fixture_count=$(grep -cE "^test_rule_${_r110_rid}_" "$_r110_test_file" 2>/dev/null || echo 0)
    if [[ "$_r110_fixture_count" -lt 2 ]]; then
      fail_rule "prevention_rule_scope_completeness" "$_r110_card declares scope_surfaces: but has only $_r110_fixture_count test_rule_${_r110_rid}_* fixtures (need ≥2) -- Rule 110 / E155 (META per ADR-0093)"
      _r110_fail=1
    fi
  done
fi
if [[ $_r110_fail -eq 0 ]]; then pass_rule "prevention_rule_scope_completeness"; fi

# === END OF RULES ===
# ---------------------------------------------------------------------------
if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
