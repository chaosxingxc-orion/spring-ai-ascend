#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 84 — active_module_architecture_path_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 84 — active_module_architecture_path_truth (enforcer E117)
#
# Every agent-*/ARCHITECTURE.md whose front-matter status: token does NOT
# contain "skeleton" or "deferred" MUST have every inline path claim of the
# shape "<module>/src/main/java/..." resolve to a real file on disk OR carry
# a historical/moved/extracted-per-ADR/superseded/deferred/formerly marker
# within +/-3 lines. Operationalises the rc5 review P0-1 closure: module-
# level ARCHITECTURE path claims cannot lag behind real code locations
# (Rule 81 already covers the symmetric skeleton case; Rule 84 covers the
# active-module case Rule 81 cannot reach).
# ---------------------------------------------------------------------------
_r84_fail=0
_r84_marker_re='historical|moved|extracted per ADR-[0-9]{4}|extracted at|was rooted|formerly|deferred|superseded|pre-ADR-[0-9]{4}|relocated|relocated to|migrated|per ADR-[0-9]{4} \(2026|post-ADR-[0-9]{4}'
_r84_path_re='agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+'
# Perf fix (2026-05-23): replaced per-line `echo | grep -oE` + per-claim
# `sed | grep` with mapfile + bash-native regex. On WSL/mnt/d the original
# took ~52s per gate run; the rewrite finishes in ~1s.
for _r84_arch in agent-*/ARCHITECTURE.md; do
  [[ -f "$_r84_arch" ]] || continue
  _r84_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r84_arch" 2>/dev/null)
  [[ "$_r84_status" == *skeleton* ]] && continue
  [[ "$_r84_status" == *deferred* ]] && continue
  mapfile -t _r84_arr < "$_r84_arch"
  _r84_n=${#_r84_arr[@]}
  for ((_r84_i=0; _r84_i<_r84_n; _r84_i++)); do
    _r84_line="${_r84_arr[$_r84_i]}"
    _r84_lineno=$((_r84_i + 1))
    _r84_rest="$_r84_line"
    while [[ "$_r84_rest" =~ $_r84_path_re ]]; do
      _r84_path="${BASH_REMATCH[0]}"
      _r84_rest="${_r84_rest#*"$_r84_path"}"
      _r84_path_clean="${_r84_path%.}"  # strip trailing dots from prose
      if [[ -e "$_r84_path_clean" ]] || [[ -e "${_r84_path_clean}.java" ]]; then continue; fi
      _r84_lo=$((_r84_i > 3 ? _r84_i - 3 : 0))
      _r84_hi=$((_r84_i + 3 < _r84_n - 1 ? _r84_i + 3 : _r84_n - 1))
      _r84_marker_present=0
      for ((_r84_j=_r84_lo; _r84_j<=_r84_hi; _r84_j++)); do
        if [[ "${_r84_arr[$_r84_j]}" =~ $_r84_marker_re ]]; then
          _r84_marker_present=1; break
        fi
      done
      [[ $_r84_marker_present -eq 1 ]] && continue
      fail_rule "active_module_architecture_path_truth" "$_r84_arch:$_r84_lineno claims path '$_r84_path_clean' that does not exist on disk and the surrounding +/-3 lines carry no historical/moved/extracted-per-ADR marker -- Rule 84 / E117"
      _r84_fail=1
    done
  done
done
if [[ $_r84_fail -eq 0 ]]; then pass_rule "active_module_architecture_path_truth"; fi

