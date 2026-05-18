#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 80 — s2c_callback_signal_historical_only_in_authority. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 80 — s2c_callback_signal_historical_only_in_authority (enforcer E113)
#
# In authoritative entrypoints (CLAUDE.md, README.md, root ARCHITECTURE.md,
# agent-*/ARCHITECTURE.md, docs/contracts/*.v1.yaml, docs/adr/*.yaml,
# docs/adr/*.md), the deleted Java type name S2cCallbackSignal MUST appear
# only in paragraphs marked historical / deleted / refactored from /
# amendments / rc3-unification (within +/-5 lines). v2.0.0-rc3 unified S2C
# suspension into the checked SuspendSignal.forClientCallback(...) variant
# (ADR-0074 2026-05-18 amendment); live current-state claims naming
# S2cCallbackSignal are forbidden in authoritative docs.
# ---------------------------------------------------------------------------
_r80_fail=0
_r80_marker_re='historical|deleted|refactored|rc3-unification|amendment|forClientCallback|prior parallel|2026-05-16 design|unifies|unified|rc3 unif'
for _r80_file in CLAUDE.md README.md ARCHITECTURE.md docs/contracts/*.v1.yaml docs/adr/*.yaml docs/adr/*.md agent-*/ARCHITECTURE.md; do
  [[ -f "$_r80_file" ]] || continue
  while IFS= read -r _r80_match; do
    [[ -z "$_r80_match" ]] && continue
    _r80_lineno="${_r80_match%%:*}"
    [[ -z "$_r80_lineno" || ! "$_r80_lineno" =~ ^[0-9]+$ ]] && continue
    _r80_lo=$((_r80_lineno > 5 ? _r80_lineno - 5 : 1))
    _r80_hi=$((_r80_lineno + 5))
    if ! sed -n "${_r80_lo},${_r80_hi}p" "$_r80_file" 2>/dev/null | grep -qiE "$_r80_marker_re"; then
      fail_rule "s2c_callback_signal_historical_only_in_authority" "$_r80_file:$_r80_lineno mentions S2cCallbackSignal without a historical/deleted/refactored/amendment marker within +/-5 lines -- Rule 80 / E113"
      _r80_fail=1
    fi
  done < <(grep -nF 'S2cCallbackSignal' "$_r80_file" 2>/dev/null)
done
if [[ $_r80_fail -eq 0 ]]; then pass_rule "s2c_callback_signal_historical_only_in_authority"; fi

