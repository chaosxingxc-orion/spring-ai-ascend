#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 117 — phase_contract_rule_allocation_coherence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

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
  # Materialise the cited / card / principle sets to temp files BEFORE the
  # lookup loops. `printf '%s\n' "$big_var" | grep -Fxq` triggers SIGPIPE
  # on the printf when grep -q exits early on first match — combined with
  # `set -o pipefail` at the top of this script, the captured result becomes
  # non-deterministic across fast (CI) vs slow (local) runners. CI rc21 hit
  # this on R-C.1 reporting false orphan. Temp files make the lookup
  # pipefail-immune.
  _r117_tmp=$(mktemp -d 2>/dev/null || mktemp -d -t r117) || _r117_tmp="/tmp/r117_$$"
  mkdir -p "$_r117_tmp"
  printf '%s%s' "$_r117_cited_p" "$_r117_cited_x" | grep -v '^$' | sort -u > "$_r117_tmp/all_cited" || true
  printf '%s\n' "$_r117_cards" | grep -v '^$' > "$_r117_tmp/cards" || true
  printf '%s\n' "$_r117_principles" | grep -v '^$' > "$_r117_tmp/principles" || true
  # Check (a): every cited id resolves to a card or principle
  while IFS= read -r _r117_cited; do
    [[ -z "$_r117_cited" ]] && continue
    if ! grep -Fxq "$_r117_cited" "$_r117_tmp/cards" \
       && ! grep -Fxq "$_r117_cited" "$_r117_tmp/principles"; then
      _r117_drift+="ghost-rule:$_r117_cited (cited in contract; no card on disk); "
      _r117_fail=1
    fi
  done < "$_r117_tmp/all_cited"
  # Check (b): every rule card is cited at least once
  while IFS= read -r _r117_card; do
    [[ -z "$_r117_card" ]] && continue
    if ! grep -Fxq "$_r117_card" "$_r117_tmp/all_cited"; then
      _r117_drift+="orphan-rule:$_r117_card (card exists; not cited in any contract); "
      _r117_fail=1
    fi
  done < "$_r117_tmp/cards"
  # Check (c): dual-P only allowed for G-9
  printf '%s' "$_r117_cited_p" | grep -v '^$' | sort | uniq -d > "$_r117_tmp/dup_p" || true
  _r117_dup_p=$(cat "$_r117_tmp/dup_p" 2>/dev/null || true)
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
  rm -rf "$_r117_tmp" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Rule G-1.1 — L1 Architecture Depth & Grounding (3 sub-clauses, ADR-0099)
# rc27 fix (rc22-2): real helpers replace prior placeholder pass_rule stubs.
# ---------------------------------------------------------------------------
