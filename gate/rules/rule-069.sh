#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 69 — every_active_rule_has_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 69 — every_active_rule_has_card (enforcer E99)
#
# Every "#### Rule <id>" heading in CLAUDE.md MUST have a sibling
# docs/governance/rules/rule-<id>.md card. The reverse direction (every card
# must appear in CLAUDE.md or in an alt-home file) is NO LONGER enforced --
# cards exist independently as the sole authority for rule definitions per
# user directive 2026-05-28. Orphan-card check eliminated; the CLAUDE-deferred.md
# alt-home semantics retired with that file's elimination.
#
# Initial PR1 mode (loose): if docs/governance/rules/ does not exist yet,
# the rule is vacuously true.
# ---------------------------------------------------------------------------
_r69_fail=0
_r69_claude='CLAUDE.md'
_r69_cards_dir='docs/governance/rules'
if [[ ! -d "$_r69_cards_dir" ]]; then
  pass_rule "every_active_rule_has_card"
else
  _r69_active_f=$(mktemp 2>/dev/null || echo "/tmp/r69_active.$$")
  _r69_cards_f=$(mktemp 2>/dev/null || echo "/tmp/r69_cards.$$")
  grep -oE '^#### Rule [A-Za-z0-9.-]+' "$_r69_claude" 2>/dev/null \
    | sed -E 's/^#### Rule //; s/^0*([0-9])/\1/' | sort -u > "$_r69_active_f"
  find "$_r69_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/rule-(.+)\.md$|\1|; s/^0*([0-9])/\1/' | sort -u > "$_r69_cards_f"
  _r69_missing=$(comm -23 "$_r69_active_f" "$_r69_cards_f" | tr '\n' ' ' | sed 's/[[:space:]]*$//')
  if [[ -n "$_r69_missing" ]]; then
    fail_rule "every_active_rule_has_card" "active rules with no card: $_r69_missing"
    _r69_fail=1
  fi
  rm -f "$_r69_active_f" "$_r69_cards_f"
  if [[ $_r69_fail -eq 0 ]]; then
    pass_rule "every_active_rule_has_card"
  fi
fi

# ---------------------------------------------------------------------------
