#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 69 — every_active_rule_has_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 69 — every_active_rule_has_card (enforcer E99)
#
# Every "#### Rule NN" heading in CLAUDE.md MUST have a sibling
# docs/governance/rules/rule-NN.md (zero-padded). Every card MUST either
# (a) appear as a heading in CLAUDE.md, or
# (b) appear as a "Rule NN" reference in docs/CLAUDE-deferred.md.
# Orphan cards that satisfy neither are a fail.
#
# Initial PR1 mode (loose): if docs/governance/rules/ does not exist yet,
# the rule is vacuously true so the budget-gate and other rules can land first.
# ---------------------------------------------------------------------------
_r69_fail=0
_r69_claude='CLAUDE.md'
_r69_deferred='docs/CLAUDE-deferred.md'
_r69_cards_dir='docs/governance/rules'
if [[ ! -d "$_r69_cards_dir" ]]; then
  pass_rule "every_active_rule_has_card"
else
  # Extract active rule numbers from CLAUDE.md.
  _r69_active=$(grep -oE '^#### Rule [0-9]+' "$_r69_claude" 2>/dev/null | grep -oE '[0-9]+' | sort -un)
  # Extract card numbers from filenames.
  _r69_cards=$(find "$_r69_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
                 | sed -E 's|.*/rule-0*([0-9]+)[a-z]?\.md|\1|' | sort -un)
  # Missing cards: active rule with no card.
  _r69_missing=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if ! echo "$_r69_cards" | grep -qxF "$_n"; then
      _r69_missing+="$_n "
    fi
  done <<< "$_r69_active"
  if [[ -n "$_r69_missing" ]]; then
    fail_rule "every_active_rule_has_card" "active rules with no card: $_r69_missing"
    _r69_fail=1
  fi
  # Orphan cards: card exists but rule is neither active nor deferred.
  _r69_orphans=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if echo "$_r69_active" | grep -qxF "$_n"; then
      continue
    fi
    # Check deferred file mentions "Rule NN" (allow optional sub-clause suffix like 29.c).
    if [[ -f "$_r69_deferred" ]] && grep -qE "Rule[[:space:]]+${_n}([.][a-z])?\b" "$_r69_deferred"; then
      continue
    fi
    _r69_orphans+="$_n "
  done <<< "$_r69_cards"
  if [[ -n "$_r69_orphans" ]]; then
    fail_rule "every_active_rule_has_card" "orphan cards (no active or deferred reference): $_r69_orphans"
    _r69_fail=1
  fi
  if [[ $_r69_fail -eq 0 ]]; then
    pass_rule "every_active_rule_has_card"
  fi
fi

# ---------------------------------------------------------------------------
