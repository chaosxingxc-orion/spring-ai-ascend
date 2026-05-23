#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 101 — rule_namespace_authority_completeness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 101 — rule_namespace_authority_completeness (enforcer E143)
#
# Closes rc11 review P1-1 (K-α family): ratchet authority surfaces had
# diverged across CLAUDE.md (30 namespaced kernels) vs rule cards (15/16
# hybrid frontmatter) vs enforcers.yaml (60+ stale `Rule 28[a-i]` refs) vs
# architecture-status.yaml (`active_engineering_rules: 67` vs CLAUDE 30).
# Rule 101 gates the semantic-authority parity per ADR-0086 `gate_layer_boundary:`:
#   (a) every `#### Rule <ns>` heading in CLAUDE.md has a matching
#       `docs/governance/rules/rule-<ns>.md` with `rule_id: <ns>` frontmatter.
#   (b) `baseline_metrics.active_engineering_rules` equals the live count of
#       `^#### Rule ` headers in CLAUDE.md.
#   (c) every active enforcer `constraint_ref:` either uses namespaced
#       form (`Rule [DRGM]-`) OR carries a legacy/historical marker.
# Gate-layer identifiers (gate section headers, gate/rules/*.sh filenames)
# stay numeric BY DESIGN per ADR-0086; Rule 101 only gates authority surfaces.
# ---------------------------------------------------------------------------
_r101_fail=0
_r101_claude="CLAUDE.md"
_r101_status_yaml="docs/governance/architecture-status.yaml"
_r101_cards_dir="docs/governance/rules"
_r101_enforcers="docs/governance/enforcers.yaml"
if [[ ! -f "$_r101_claude" ]] || [[ ! -d "$_r101_cards_dir" ]] || [[ ! -f "$_r101_status_yaml" ]]; then
  fail_rule "rule_namespace_authority_completeness" "missing CLAUDE.md or rule-card dir or architecture-status.yaml -- Rule 101 / E143"
  _r101_fail=1
else
  # (a) Every CLAUDE kernel header has a card.
  _r101_missing_cards=""
  while IFS= read -r _r101_h; do
    _r101_ns="$(echo "$_r101_h" | sed -E 's/^#### Rule ([A-Z]-[A-Za-z0-9.]+).*/\1/')"
    _r101_card="${_r101_cards_dir}/rule-${_r101_ns}.md"
    if [[ ! -f "$_r101_card" ]]; then
      _r101_missing_cards="${_r101_missing_cards} ${_r101_ns}"
    elif ! grep -qE "^rule_id: ${_r101_ns}[[:space:]]*\r?$" "$_r101_card" 2>/dev/null; then
      _r101_missing_cards="${_r101_missing_cards} ${_r101_ns}(frontmatter)"
    fi
  done < <(grep -E '^#### Rule [A-Z]-' "$_r101_claude" 2>/dev/null)
  if [[ -n "$_r101_missing_cards" ]]; then
    fail_rule "rule_namespace_authority_completeness" "CLAUDE.md kernel heading(s) without matching rule card OR card frontmatter rule_id mismatch:${_r101_missing_cards} -- Rule 101 / E143 (a) -- ADR-0086 authority-surface parity"
    _r101_fail=1
  fi

  # (b) baseline_metrics.active_engineering_rules equals live CLAUDE.md count.
  _r101_kernel_count=$(grep -cE '^#### Rule [A-Z]-' "$_r101_claude" 2>/dev/null || echo 0)
  _r101_declared=$(awk '/^[[:space:]]+active_engineering_rules:/{print $2; exit}' "$_r101_status_yaml")
  if [[ -z "$_r101_declared" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml missing active_engineering_rules: under baseline_metrics -- Rule 101 / E143 (b)"
    _r101_fail=1
  elif [[ "$_r101_declared" != "$_r101_kernel_count" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml baseline_metrics.active_engineering_rules=$_r101_declared but CLAUDE.md has $_r101_kernel_count '#### Rule ' headers -- Rule 101 / E143 (b)"
    _r101_fail=1
  fi

  # (c) enforcers.yaml constraint_ref lines must be namespaced or carry legacy marker.
  if [[ -f "$_r101_enforcers" ]]; then
    # Engineering-rule range (1-48) per ADR-0086 gate_layer_boundary requires legacy/namespaced markers.
    # Gate-layer rules (numeric ≥49) are intentional numeric per ADR-0086 and are exempt.
    _r101_bad_refs=$(grep -nE 'constraint_ref:[[:space:]]*"[^"]*\bRule ([1-9]|[1-3][0-9]|4[0-8])[a-z]?\b' "$_r101_enforcers" 2>/dev/null \
                     | grep -vE 'legacy Rule [0-9]+.?[a-z]?|Rule [DRGM]-|historical' || true)
    if [[ -n "$_r101_bad_refs" ]]; then
      _r101_first=$(echo "$_r101_bad_refs" | head -3 | tr '\n' '|')
      fail_rule "rule_namespace_authority_completeness" "enforcers.yaml constraint_ref row(s) carry bare numeric 'Rule N' without 'legacy' marker or namespaced form: ${_r101_first}-- Rule 101 / E143 (c)"
      _r101_fail=1
    fi
  fi
fi
if [[ $_r101_fail -eq 0 ]]; then pass_rule "rule_namespace_authority_completeness"; fi

# ---------------------------------------------------------------------------
