#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 71 — deferred_doc_not_in_always_loaded. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 71 — deferred_doc_not_in_always_loaded (enforcer E101)
#
# Once docs/CLAUDE-deferred.md is demoted from the always-loaded set, the
# demote must stay durable. Fails if:
#   (a) CLAUDE.md contains a literal '@docs/CLAUDE-deferred.md' include directive
#       (the Claude Code auto-load syntax), OR
#   (b) docs/governance/SESSION-START-CONTEXT.md table row for CLAUDE-deferred.md
#       contains an ALWAYS-LOAD / ALWAYS marker.
# Plain prose pointers ("see docs/CLAUDE-deferred.md") are fine.
# ---------------------------------------------------------------------------
_r71_fail=0
_r71_claude='CLAUDE.md'
_r71_sscontext='docs/governance/SESSION-START-CONTEXT.md'
if [[ -f "$_r71_claude" ]] && grep -qE '^[[:space:]]*@docs/CLAUDE-deferred\.md' "$_r71_claude" 2>/dev/null; then
  fail_rule "deferred_doc_not_in_always_loaded" "$_r71_claude contains @docs/CLAUDE-deferred.md auto-load -- must be on-demand"
  _r71_fail=1
fi
if [[ -f "$_r71_sscontext" ]]; then
  # Look at lines mentioning CLAUDE-deferred.md and reject ones marked ALWAYS / ALWAYS-LOAD.
  _r71_bad=$(grep -E 'CLAUDE-deferred\.md' "$_r71_sscontext" 2>/dev/null | grep -E '(\bALWAYS\b|ALWAYS-LOAD)' || true)
  if [[ -n "$_r71_bad" ]]; then
    fail_rule "deferred_doc_not_in_always_loaded" "$_r71_sscontext marks CLAUDE-deferred.md as ALWAYS-LOAD"
    _r71_fail=1
  fi
fi
if [[ $_r71_fail -eq 0 ]]; then pass_rule "deferred_doc_not_in_always_loaded"; fi

# ===========================================================================
# Gate-script efficiency wave PR-E1 (2026-05-17)
# Authority: docs/governance/rules/rule-73.md
# ===========================================================================

# ---------------------------------------------------------------------------
