#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 68 — claude_md_kernel_matches_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 68 — claude_md_kernel_matches_card (enforcer E98)
#
# For every docs/governance/rules/rule-NN.md card, extract the kernel: scalar
# from the YAML front-matter, normalise whitespace, and assert the same text
# appears verbatim in the body of "#### Rule NN" in CLAUDE.md. Fails on drift.
# If no cards exist (initial PR1 landing), the rule is vacuously true.
# ---------------------------------------------------------------------------
_r68_fail=0
_r68_claude='CLAUDE.md'
_r68_cards_dir='docs/governance/rules'
if [[ ! -f "$_r68_claude" ]]; then
  fail_rule "claude_md_kernel_matches_card" "$_r68_claude missing"
  _r68_fail=1
elif [[ ! -d "$_r68_cards_dir" ]]; then
  pass_rule "claude_md_kernel_matches_card"
else
  _r68_drift=""
  while IFS= read -r _r68_card; do
    [[ -z "$_r68_card" ]] && continue
    _r68_base=$(basename "$_r68_card" .md)
    # Card id may be old integer form (rule-NN) or new namespaced form
    # (rule-D-1 / rule-R-C.a / rule-G-3.f / rule-M-2.b). Extract the trailing
    # identifier — everything after `rule-`.
    _r68_id=$(printf '%s\n' "$_r68_base" | sed -nE 's/^rule-(.+)$/\1/p')
    [[ -z "$_r68_id" ]] && continue
    # For integer ids, strip leading zeros for heading-match symmetry.
    if [[ "$_r68_id" =~ ^[0-9]+$ ]]; then
      _r68_id_match=$(printf '%s\n' "$_r68_id" | sed -nE 's/^0*([0-9]+)$/\1/p')
    else
      _r68_id_match="$_r68_id"
    fi
    # Extract the kernel: scalar from card front-matter (supports both '|' literal
    # block style and inline scalar). Stop at the next top-level key or '---'.
    _r68_kernel=$(awk '
      /^kernel:[[:space:]]*\|/ { flag=1; next }
      /^kernel:[[:space:]]/ { line=$0; sub(/^kernel:[[:space:]]*/, "", line); print line; exit }
      flag && /^[a-zA-Z_][a-zA-Z_0-9]*:/ { flag=0; exit }
      flag && /^---$/ { flag=0; exit }
      flag { sub(/^  /, ""); print }
    ' "$_r68_card" | tr -s ' \t' ' ' | tr -d '\r' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
    [[ -z "$_r68_kernel" ]] && continue
    # Extract the body of "#### Rule <id>" from CLAUDE.md: lines until the first
    # blank-line + "Enforced" or until "---" or until the next heading.
    _r68_body=$(awk -v n="$_r68_id_match" '
      $0 ~ "^#### Rule " n "[[:space:]]" || $0 ~ "^#### Rule " n "$" { flag=1; next }
      flag && /^---$/ { exit }
      flag && /^#### / { exit }
      flag && /^Enforced by/ { exit }
      flag && NF { print }
    ' "$_r68_claude" | tr -s ' \t' ' ' | tr -d '\r' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '\n' ' ' | tr -s ' ' | sed -E 's/^ //; s/ $//')
    if [[ -z "$_r68_body" ]]; then
      # Deferred-only sub-clause cards (e.g. R-A.c, R-K.c) have no CLAUDE.md body —
      # they live in docs/CLAUDE-deferred.md. Check that the deferred doc
      # references the rule before failing.
      _r68_deferred_doc='docs/CLAUDE-deferred.md'
      if [[ -f "$_r68_deferred_doc" ]] && grep -qE "(^|[^a-zA-Z0-9])Rule[[:space:]]+${_r68_id_match}([^a-zA-Z0-9]|$)" "$_r68_deferred_doc"; then
        continue  # deferred-only card; not a drift
      fi
      _r68_drift+="Rule $_r68_id_match: card exists but no body in CLAUDE.md; "
      _r68_fail=1
    elif [[ "$_r68_kernel" != "$_r68_body" ]]; then
      _r68_drift+="Rule $_r68_id_match drift; "
      _r68_fail=1
    fi
  done < <(find "$_r68_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null | sort)
  if [[ $_r68_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_matches_card"
  else
    fail_rule "claude_md_kernel_matches_card" "$_r68_drift"
  fi
fi

# ---------------------------------------------------------------------------
