#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28g — no_prose_only_constraint_marker. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28g — no_prose_only_constraint_marker (enforcer E30)
# Rule 28 forbids deferring an enforcer. Markers like "TODO: enforce",
# "FIXME: enforcer", "XXX: test", "deferred: gate" in CLAUDE.md /
# ARCHITECTURE.md / module ARCHITECTURE.md / docs/governance/*.yaml are bans.
# ---------------------------------------------------------------------------
_r28g_fail=0
_marker_pattern='(TODO|FIXME|XXX|deferred)[[:space:]]*:[[:space:]]*(enforce|enforcer|test|gate)\b'
# Canonical architecture-text files + every L1+ ADR (00[5-9]X glob). ADR-0059
# is exempt because it documents the marker patterns themselves; any future
# L1+ ADR that legitimately needs to document the markers must explicitly
# extend the _28g_exempt list (rather than silently drop out of scope).
# Phase K (audit fix F7): switched from a hardcoded list to a glob with an
# explicit exempt set so new ADRs are auto-covered.
_28g_files=(CLAUDE.md ARCHITECTURE.md)
while IFS= read -r _arch; do
  [[ -n "$_arch" ]] && _28g_files+=("$_arch")
done < <(ls agent-service/ARCHITECTURE.md agent-service/ARCHITECTURE.md 2>/dev/null || true)
_28g_exempt=("docs/adr/0059-code-as-contract-architectural-enforcement.md")
while IFS= read -r _adr; do
  [[ -z "$_adr" ]] && continue
  _skip=0
  for _ex in "${_28g_exempt[@]}"; do
    [[ "$_adr" == "$_ex" ]] && _skip=1 && break
  done
  [[ $_skip -eq 0 ]] && _28g_files+=("$_adr")
done < <(ls docs/adr/00[5-9][0-9]-*.md 2>/dev/null | sort || true)
_28g_existing=()
for _f in "${_28g_files[@]}"; do
  [[ -f "$_f" ]] && _28g_existing+=("$_f")
done
_28g_hits=""
if (( ${#_28g_existing[@]} > 0 )); then
  _28g_hits=$(grep -nE "$_marker_pattern" "${_28g_existing[@]}" 2>/dev/null || true)
fi
if [[ -n "$_28g_hits" ]]; then
  fail_rule "no_prose_only_constraint_marker" "Rule-28-bypass marker found:\n$_28g_hits\nPer Rule 28g / enforcer E30."
  _r28g_fail=1
fi
if [[ $_r28g_fail -eq 0 ]]; then pass_rule "no_prose_only_constraint_marker"; fi

# ---------------------------------------------------------------------------
