#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 142 — tier1_non_english_lint. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 142 — tier1_non_english_lint (enforcer E190, kernel Rule G-25)
#
# Authority: CLAUDE.md kernel "Translate all instructions into English".
# Closes external review P1-3: every file with a non-zero budget in
# gate/always-loaded-budget.txt (the always-loaded Tier-1 set) MUST be free of
# CJK code points [U+4E00..U+9FFF] and common UTF-8/GBK mojibake markers
# (U+FFFD plus the literal sequences for double-decoded text). Fails closed.
# The checker reports line:col + byte offset ONLY and MUST NOT echo the
# offending non-English text, so the gate log never embeds non-English source.
#
# scope_surfaces: gate/always-loaded-budget.txt, gate/lib/check_tier1_non_english.py
# ---------------------------------------------------------------------------
_r142_fail=0
_r142_helper="gate/lib/check_tier1_non_english.py"
if [[ ! -f "$_r142_helper" ]]; then
  fail_rule "tier1_non_english_lint" "$_r142_helper missing -- Rule G-25 / E190"
  _r142_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r142_out=$("$GATE_PYTHON_BIN" "$_r142_helper" 2>&1)
  _r142_rc=$?
  if [[ $_r142_rc -ne 0 ]]; then
    _r142_count=$(printf '%s\n' "$_r142_out" | grep -cE '^(NON-ENGLISH|MISSING-SURFACE|MISSING-BUDGET|NO-SCOPE|UNREADABLE):')
    _r142_first=$(printf '%s' "$_r142_out" | grep -E '^(NON-ENGLISH|MISSING-SURFACE|MISSING-BUDGET|NO-SCOPE|UNREADABLE):' | head -1)
    fail_rule "tier1_non_english_lint" "always-loaded Tier-1 surface(s) carry non-English / mojibake ($_r142_count finding(s), first: ${_r142_first:-rc=$_r142_rc}) -- Rule G-25 / E190 (line:col + byte offset only; offending text intentionally not echoed)"
    _r142_fail=1
  fi
fi
[[ $_r142_fail -eq 0 ]] && pass_rule "tier1_non_english_lint"

# ---------------------------------------------------------------------------
