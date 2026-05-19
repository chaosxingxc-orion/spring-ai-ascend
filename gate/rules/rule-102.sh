#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 102 — release_recency_resolver_correctness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 102 — release_recency_resolver_correctness (enforcer E144)
#
# Closes rc11 review P1-2 (K-β family): lex-sort `find docs/logs/releases |
# sort | tail -1` placed `rc9-corrective.en.md` after `rc11-corrective.en.md`
# (character "9" > "1"), so Rules 33/97/G-2 evaluated stale rc9 prose as
# canonical. The fix is gate/lib/latest_release.sh::latest_release_path
# (rc-number numeric resolver). Rule 102 is a static guard against the
# anti-pattern recurring elsewhere in the gate.
# ---------------------------------------------------------------------------
_r102_fail=0
_r102_canonical="gate/check_architecture_sync.sh"
_r102_helper="gate/lib/latest_release.sh"
if [[ ! -f "$_r102_helper" ]]; then
  fail_rule "release_recency_resolver_correctness" "$_r102_helper missing -- Rule 102 / E144 (K-β resolver helper must exist)"
  _r102_fail=1
fi
# Scan production gate scripts for the lex-sort anti-pattern.
_r102_bad_sites=""
while IFS= read -r _r102_f; do
  [[ -f "$_r102_f" ]] || continue
  # Skip the helper itself + test fixtures + this very gate-script comment block.
  case "$_r102_f" in
    "$_r102_helper") continue ;;
    gate/test_architecture_sync_gate.sh) continue ;;
  esac
  _r102_hits=$(grep -nE 'find[[:space:]]+docs/logs/releases.*\|[[:space:]]*sort[[:space:]]*\|[[:space:]]*tail' "$_r102_f" 2>/dev/null || true)
  if [[ -n "$_r102_hits" ]]; then
    _r102_bad_sites="${_r102_bad_sites}${_r102_f}: ${_r102_hits}|"
  fi
done < <(find gate -maxdepth 2 -type f -name '*.sh' 2>/dev/null)
if [[ -n "$_r102_bad_sites" ]]; then
  fail_rule "release_recency_resolver_correctness" "production gate script(s) use lex-sort tail-1 anti-pattern instead of gate/lib/latest_release.sh::latest_release_path: ${_r102_bad_sites}-- Rule 102 / E144 (K-β closure; rc11 review P1-2)"
  _r102_fail=1
fi
if [[ $_r102_fail -eq 0 ]]; then pass_rule "release_recency_resolver_correctness"; fi

# ---------------------------------------------------------------------------
