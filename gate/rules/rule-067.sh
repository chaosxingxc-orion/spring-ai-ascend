#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 67 — claude_md_kernel_size_bounded. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 67 — claude_md_kernel_size_bounded (enforcer E97)
#
# For each "#### Rule NN" heading in CLAUDE.md, count the lines between the
# heading and the next "---" separator (inclusive of the heading). Look up
# kernel_cap from docs/governance/rules/rule-NN.md front-matter; fail if
# exceeded. If the card does not exist yet, this rule is SKIPPED for that
# rule (the missing card is caught by Rule 69 instead).
#
# Cap discipline (per CLAUDE.md token-optimization wave):
#   daily principles (Rules 1-6, 9, 10): kernel_cap: 12
#   architectural + ironclad (Rules 20-48): kernel_cap: 6
# ---------------------------------------------------------------------------
_r67_fail=0
_r67_claude='CLAUDE.md'
_r67_cards_dir='docs/governance/rules'
if [[ ! -f "$_r67_claude" ]]; then
  fail_rule "claude_md_kernel_size_bounded" "$_r67_claude missing"
  _r67_fail=1
elif [[ ! -d "$_r67_cards_dir" ]]; then
  # No cards yet -- rule is vacuously true during initial PR1 landing.
  pass_rule "claude_md_kernel_size_bounded"
else
  _r67_violations=""
  # Extract every Rule NN heading line number from CLAUDE.md.
  _r67_rule_lines=$(grep -nE '^#### Rule [0-9]+' "$_r67_claude" | sort -t: -k1,1n)
  while IFS= read -r _r67_entry; do
    [[ -z "$_r67_entry" ]] && continue
    _r67_ln="${_r67_entry%%:*}"
    _r67_rest="${_r67_entry#*:}"
    _r67_num=$(printf '%s\n' "$_r67_rest" | sed -nE 's/^#### Rule ([0-9]+).*/\1/p')
    [[ -z "$_r67_num" ]] && continue
    _r67_card_padded=$(printf 'rule-%02d.md' "$_r67_num")
    _r67_card="${_r67_cards_dir}/${_r67_card_padded}"
    if [[ ! -f "$_r67_card" ]]; then
      # No card -- skip (Rule 69 will catch it).
      continue
    fi
    _r67_cap=$(awk '/^kernel_cap:[[:space:]]*[0-9]+/{print $2; exit}' "$_r67_card")
    [[ -z "$_r67_cap" ]] && continue
    # Count lines from heading until next '---' separator (exclusive of separator).
    _r67_count=$(awk -v start="$_r67_ln" '
      NR < start { next }
      NR == start { count = 1; next }
      /^---$/ { exit }
      { count++ }
      END { print count + 0 }
    ' "$_r67_claude")
    if [[ "$_r67_count" -gt "$_r67_cap" ]]; then
      _r67_violations+="Rule $_r67_num: $_r67_count lines > cap $_r67_cap; "
      _r67_fail=1
    fi
  done <<< "$_r67_rule_lines"
  if [[ $_r67_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_size_bounded"
  else
    fail_rule "claude_md_kernel_size_bounded" "$_r67_violations"
  fi
fi

# ---------------------------------------------------------------------------
