#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 109 — namespaced_rule_reference_completeness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 109 — namespaced_rule_reference_completeness (enforcer E154)
#
# Family C prevention — closes rc16 P2-1 (numeric Rule references in 13
# principle frontmatters + 4 module ARCHITECTURE.md + 3 contract docs).
# rc12 Rule 101 was scoped narrowly per ADR-0086 gate_layer_boundary;
# this rule widens to ALL semantic-authority surfaces. Per ADR-0093.
#
# scope_surfaces: docs/governance/principles/P-*.md, docs/governance/rules/*.md, architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md, docs/contracts/*.yaml, docs/contracts/*.md
#
# Numeric Rule references MUST carry a legacy marker (formerly|legacy|
# historical|Gate Rule|gate Rule|was Rule|ex-Rule) within the SAME line.
# Gate-layer numeric refs (`Gate Rule N`) are exempt by syntax.
# ---------------------------------------------------------------------------
_r109_fail=0
_r109_surfaces=$(find docs/governance/principles docs/governance/rules \
                   architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md docs/contracts \
                   -type f \( -name '*.md' -o -name '*.yaml' \) 2>/dev/null \
                 | grep -v 'docs/archive/' | grep -v 'docs/logs/' || true)
while IFS= read -r _r109_file; do
  [[ -z "$_r109_file" || ! -f "$_r109_file" ]] && continue
  # Find lines with `Rule <digits>` in the engineering-rule range (1-48) without same-line legacy marker.
  # Gate-layer rules (≥49) per ADR-0086 gate_layer_boundary are intentionally numeric and not subject to this check.
  _r109_hits=$(grep -nE '\bRule ([1-9]|[1-3][0-9]|4[0-8])(\.[a-z])?\b' "$_r109_file" 2>/dev/null \
               | grep -viE '(formerly|legacy|historical|gate rule|was rule|ex-rule|pre-rc[0-9]+|superseded|deprecated)' || true)
  while IFS= read -r _r109_line; do
    [[ -z "$_r109_line" ]] && continue
    # Skip lines that are URL/section headers/code blocks
    echo "$_r109_line" | grep -qE '^[0-9]+:[[:space:]]*```' && continue
    fail_rule "namespaced_rule_reference_completeness" "$_r109_file:$_r109_line -- Rule 109 / E154 (Family C per ADR-0093)"
    _r109_fail=1
  done <<< "$_r109_hits"
done <<< "$_r109_surfaces"
if [[ $_r109_fail -eq 0 ]]; then pass_rule "namespaced_rule_reference_completeness"; fi

# ---------------------------------------------------------------------------
