#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 96 — kernel_deferred_clause_coherence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 96 — kernel_deferred_clause_coherence (enforcer E133)
#
# Closes rc8 post-corrective review P1-1: active rule kernels (Rule 42, Rule 46)
# stated runtime obligations that `docs/CLAUDE-deferred.md` correctly defers
# to W2. Rule 96 asserts that for every `Rule N.<letter>` block in
# CLAUDE-deferred.md, the matching `#### Rule N` kernel block in CLAUDE.md
# acknowledges the sub-clause by literal-string reference (e.g. `Rule 42.b`).
# ---------------------------------------------------------------------------
_r96_fail=0
_r96_claude="CLAUDE.md"
_r96_deferred="docs/CLAUDE-deferred.md"
if [[ ! -f "$_r96_claude" ]] || [[ ! -f "$_r96_deferred" ]]; then
  fail_rule "kernel_deferred_clause_coherence" "$_r96_claude or $_r96_deferred missing — Rule 96 / E133"
  _r96_fail=1
else
  _r96_missing=""
  # Find every "## Rule N.X" or "## Rule N.b/c/..." heading in CLAUDE-deferred.md
  while IFS= read -r _r96_subclause; do
    [[ -z "$_r96_subclause" ]] && continue
    _r96_num=$(echo "$_r96_subclause" | grep -oE '^[0-9]+')
    _r96_letter=$(echo "$_r96_subclause" | grep -oE '\.[a-z]$' | sed 's/^\.//')
    [[ -z "$_r96_num" ]] || [[ -z "$_r96_letter" ]] && continue
    _r96_ref="Rule ${_r96_num}.${_r96_letter}"
    # Find the `#### Rule N` block in CLAUDE.md (between heading and next `---`).
    _r96_block=$(awk -v rn="$_r96_num" '
      $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
      in_block && /^---$/ { exit }
      in_block { print }
    ' "$_r96_claude")
    if [[ -z "$_r96_block" ]]; then continue; fi  # Rule N might be deferred itself
    # Coherence is satisfied if EITHER the CLAUDE.md kernel OR the matching rule card
    # references the sub-clause by literal name. Rule cards have no kernel_cap, so a
    # rule with a long deferred discussion can cite there without bloating CLAUDE.md.
    _r96_card="docs/governance/rules/rule-${_r96_num}.md"
    _r96_kernel_has=0
    _r96_card_has=0
    echo "$_r96_block" | grep -qF "$_r96_ref" && _r96_kernel_has=1
    [[ -f "$_r96_card" ]] && grep -qF "$_r96_ref" "$_r96_card" && _r96_card_has=1
    if [[ $_r96_kernel_has -eq 0 ]] && [[ $_r96_card_has -eq 0 ]]; then
      _r96_missing="${_r96_missing}Rule${_r96_num}.${_r96_letter} "
    fi
  done < <(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r96_deferred" | sed -E 's/^## Rule //')
  if [[ -n "$_r96_missing" ]]; then
    fail_rule "kernel_deferred_clause_coherence" "Active rule kernel + rule card pair does not acknowledge deferred sub-clause(s): ${_r96_missing}-- Rule 96 / E133 (add explicit 'Rule N.X' literal-string reference in either CLAUDE.md kernel block OR docs/governance/rules/rule-NN.md card; rc8 post-corrective P1-1 closure)"
    _r96_fail=1
  fi
fi
if [[ $_r96_fail -eq 0 ]]; then pass_rule "kernel_deferred_clause_coherence"; fi

# === END OF RULES ===
# ---------------------------------------------------------------------------
if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
