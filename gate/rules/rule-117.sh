#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 117 — phase_contract_rule_allocation_coherence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 117 — phase_contract_rule_allocation_coherence (enforcer E165)
#
# Operationalises Rule G-11. Phase contract <-> rule card coherence on the
# post-ADR-0098 contract layer:
#   (a) every Active Rules row in docs/governance/contracts/*.md MUST cite
#       a rule whose card exists under docs/governance/rules/rule-*.md OR
#       a principle whose card exists under docs/governance/principles/P-*.md;
#   (b) every active rule card MUST be cited in at least one phase contract
#       as P or X;
#   (c) dual-P (same rule cited as P in multiple contracts) is forbidden
#       except for the enumerated G-9 exception (commit + review).
#
# Vacuously passes if docs/governance/contracts/ is absent.
# ---------------------------------------------------------------------------
_r117_fail=0
_r117_contracts_dir='docs/governance/contracts'
_r117_rules_dir='docs/governance/rules'
_r117_principles_dir='docs/governance/principles'
if [[ ! -d "$_r117_contracts_dir" ]]; then
  pass_rule "phase_contract_rule_allocation_coherence"
else
  _r117_drift=""
  # Set of rule + principle card ids on disk
  _r117_cards=$(find "$_r117_rules_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/rule-||; s|\.md$||' | sort -u)
  _r117_principles=$(find "$_r117_principles_dir" -maxdepth 1 -name 'P-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/||; s|\.md$||' | sort -u)
  # Extract citations: each Active Rules row of form "| <id> | <title> | **P** | ..." or **X**
  _r117_cited_p=""
  _r117_cited_x=""
  for _r117_contract in "$_r117_contracts_dir"/*.md; do
    [[ -f "$_r117_contract" ]] || continue
    while IFS= read -r _r117_row; do
      _r117_id=$(printf '%s\n' "$_r117_row" | sed -nE 's/^\| ([A-Za-z][A-Za-z0-9.-]*) \|.*/\1/p')
      [[ -z "$_r117_id" ]] && continue
      [[ "$_r117_id" == "Rule" ]] && continue
      _r117_marker=$(printf '%s\n' "$_r117_row" | grep -oE '\*\*[PX]\*\*' | head -1 | tr -d '*')
      if [[ "$_r117_marker" == "P" ]]; then
        _r117_cited_p+="$_r117_id"$'\n'
      elif [[ "$_r117_marker" == "X" ]]; then
        _r117_cited_x+="$_r117_id"$'\n'
      fi
    done < <(grep -E '^\| [A-Za-z][A-Za-z0-9.-]* \|' "$_r117_contract" 2>/dev/null)
  done
  _r117_all_cited=$(printf '%s%s' "$_r117_cited_p" "$_r117_cited_x" | grep -v '^$' | sort -u)
  # Check (a): every cited id resolves to a card or principle
  while IFS= read -r _r117_cited; do
    [[ -z "$_r117_cited" ]] && continue
    if ! printf '%s\n' "$_r117_cards" | grep -Fxq "$_r117_cited" \
       && ! printf '%s\n' "$_r117_principles" | grep -Fxq "$_r117_cited"; then
      _r117_drift+="ghost-rule:$_r117_cited (cited in contract; no card on disk); "
      _r117_fail=1
    fi
  done <<< "$_r117_all_cited"
  # Check (b): every rule card is cited at least once
  while IFS= read -r _r117_card; do
    [[ -z "$_r117_card" ]] && continue
    if ! printf '%s\n' "$_r117_all_cited" | grep -Fxq "$_r117_card"; then
      _r117_drift+="orphan-rule:$_r117_card (card exists; not cited in any contract); "
      _r117_fail=1
    fi
  done <<< "$_r117_cards"
  # Check (c): dual-P only allowed for G-9
  _r117_dup_p=$(printf '%s' "$_r117_cited_p" | grep -v '^$' | sort | uniq -d)
  if [[ -n "$_r117_dup_p" ]]; then
    while IFS= read -r _r117_dup; do
      [[ -z "$_r117_dup" ]] && continue
      if [[ "$_r117_dup" != "G-9" ]]; then
        _r117_drift+="dual-P-violation:$_r117_dup (only G-9 dual-P sanctioned; see docs/governance/rules/rule-G-11.md); "
        _r117_fail=1
      fi
    done <<< "$_r117_dup_p"
  fi
  if [[ $_r117_fail -eq 0 ]]; then
    pass_rule "phase_contract_rule_allocation_coherence"
  else
    fail_rule "phase_contract_rule_allocation_coherence" "${_r117_drift}-- Rule G-11 / E165"
  fi
fi

# === END OF RULES ===
# ---------------------------------------------------------------------------
if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
