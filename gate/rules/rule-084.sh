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
for _r84_arch in agent-*/ARCHITECTURE.md; do
  [[ -f "$_r84_arch" ]] || continue
  _r84_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r84_arch" 2>/dev/null)
  [[ "$_r84_status" == *skeleton* ]] && continue
  [[ "$_r84_status" == *deferred* ]] && continue
  # Walk each line looking for path claims; check existence or marker proximity.
  _r84_lineno=0
  while IFS= read -r _r84_line || [[ -n "$_r84_line" ]]; do
    _r84_lineno=$((_r84_lineno + 1))
    _r84_claims=$(echo "$_r84_line" | grep -oE 'agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+' 2>/dev/null | sort -u)
    [[ -z "$_r84_claims" ]] && continue
    while IFS= read -r _r84_path; do
      [[ -z "$_r84_path" ]] && continue
      _r84_path_clean="${_r84_path%.}"  # strip trailing dots from prose
      if [[ -e "$_r84_path_clean" ]] || [[ -e "${_r84_path_clean}.java" ]]; then continue; fi
      _r84_lo=$((_r84_lineno > 3 ? _r84_lineno - 3 : 1))
      _r84_hi=$((_r84_lineno + 3))
      if sed -n "${_r84_lo},${_r84_hi}p" "$_r84_arch" 2>/dev/null | grep -qiE "$_r84_marker_re"; then continue; fi
      fail_rule "active_module_architecture_path_truth" "$_r84_arch:$_r84_lineno claims path '$_r84_path_clean' that does not exist on disk and the surrounding +/-3 lines carry no historical/moved/extracted-per-ADR marker -- Rule 84 / E117"
      _r84_fail=1
    done <<< "$_r84_claims"
  done < "$_r84_arch"
done
if [[ $_r84_fail -eq 0 ]]; then pass_rule "active_module_architecture_path_truth"; fi

