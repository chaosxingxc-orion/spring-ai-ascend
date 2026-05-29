#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 100 — kernel_implementation_disjunction_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 100 — kernel_implementation_disjunction_truth (enforcer E141)
#
# Closes rc10 post-corrective review P1-3 (J-γ family): Rule 96 kernel said
# "the matching CLAUDE.md kernel block MUST contain" while the impl accepted
# EITHER the kernel OR the rule card. The "AND vs OR" drift was a Code-as-
# Contract violation in the rule whose job is preventing kernel/deferred drift.
#
# Rule 100 narrows the check to an explicit allow-list of rules that declare
# "either / OR" semantics in their kernel: for each allow-list rule, both the
# kernel text and the rule card text MUST contain explicit disjunction wording
# (EITHER / OR / either surface / either ... or ...). The allow-list lives at
# gate/rule-100-disjunction-allowlist.txt (one rule id per line).
#
# Why allow-list scope: a fully-general "kernel AND vs impl ||" parser is
# fragile (bash predicate grammar varies; some rules use multi-stage checks).
# The allow-list captures the rules where the disjunction is structurally
# load-bearing.
# ---------------------------------------------------------------------------
_r100_fail=0
_r100_allowlist="gate/rule-100-disjunction-allowlist.txt"
_r100_claude="CLAUDE.md"
if [[ ! -f "$_r100_allowlist" ]]; then
  fail_rule "kernel_implementation_disjunction_truth" "$_r100_allowlist missing — Rule 100 / E141"
  _r100_fail=1
else
  _r100_violations=""
  while IFS= read -r _r100_rule; do
    [[ -z "$_r100_rule" || "$_r100_rule" =~ ^[[:space:]]*# ]] && continue
    # Card path: integer id → zero-pad to 02d; namespaced id (D-1, G-3.d, ...) → literal.
    if [[ "$_r100_rule" =~ ^[0-9]+$ ]]; then
      _r100_card="docs/governance/rules/rule-$(printf '%02d' "$_r100_rule").md"
      [[ ! -f "$_r100_card" ]] && _r100_card="docs/governance/rules/rule-${_r100_rule}.md"
    else
      _r100_card="docs/governance/rules/rule-${_r100_rule}.md"
    fi
    # Extract CLAUDE.md kernel block (heading matches either integer or namespaced id)
    _r100_block=$(awk -v rn="$_r100_rule" '
      $0 ~ "^#### Rule "rn" " || $0 ~ "^#### Rule "rn"$" { in_block = 1; print; next }
      in_block && /^---$/ { exit }
      in_block { print }
    ' "$_r100_claude")
    # Both surfaces must declare disjunction
    _r100_kernel_has=0
    _r100_card_has=0
    if echo "$_r100_block" | grep -qE '\bEITHER\b|\bOR\b|either surface|either ... or|either kernel|either the' 2>/dev/null; then
      _r100_kernel_has=1
    fi
    if [[ -f "$_r100_card" ]] && grep -qE '\bEITHER\b|\bOR\b|either surface|either ... or|either kernel|either the' "$_r100_card" 2>/dev/null; then
      _r100_card_has=1
    fi
    if [[ $_r100_kernel_has -eq 0 ]] || [[ $_r100_card_has -eq 0 ]]; then
      _r100_violations="${_r100_violations}Rule ${_r100_rule} (kernel=$_r100_kernel_has, card=$_r100_card_has) "
    fi
  done < "$_r100_allowlist"
  if [[ -n "$_r100_violations" ]]; then
    fail_rule "kernel_implementation_disjunction_truth" "allow-listed disjunction rules missing 'EITHER/OR' wording in kernel and/or card: ${_r100_violations}-- Rule 100 / E141 (rc10 post-corrective P1-3 closure; allow-list at $_r100_allowlist must declare which rules are 'either-surface' so the kernel + card wording can be checked for the EITHER/OR connective)"
    _r100_fail=1
  fi
fi
if [[ $_r100_fail -eq 0 ]]; then pass_rule "kernel_implementation_disjunction_truth"; fi

# ---------------------------------------------------------------------------
