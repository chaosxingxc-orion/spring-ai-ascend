#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 143 — local_plan_path_ban. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 143 — local_plan_path_ban (enforcer E191, kernel Rule G-26)
#
# Authority: 2026-05-29 EnginePort/Frame review P1-4. Closes the defect class of
# active authority referencing a local agent-host plan path (`D:\.claude\plans`
# or `D:/.claude/plans`, BOTH separators). SCOPE: product/, docs/adr/,
# docs/governance/ (excluding rule-history.md), architecture/, CLAUDE.md,
# AGENTS.md. The only permitted surfaces are listed in
# gate/local-plan-path-exemptions.txt (Linux-first workflow docs + history +
# gate helpers/fixtures that construct synthetic inputs).
#
# scope_surfaces: product/*, docs/adr/*, docs/governance/*, architecture/*, CLAUDE.md, AGENTS.md, gate/local-plan-path-exemptions.txt
# ---------------------------------------------------------------------------
_r143_fail=0
_r143_exempt="gate/local-plan-path-exemptions.txt"
# Match BOTH path separators: D:\.claude\plans and D:/.claude/plans.
_r143_pat='D:[\\/]\.claude[\\/]plans'
_r143_hits=$(grep -rEln "$_r143_pat" \
               product docs/adr docs/governance architecture CLAUDE.md AGENTS.md 2>/dev/null \
             | sort -u)
_r143_bad=""
while IFS= read -r _r143_f; do
  [[ -z "$_r143_f" ]] && continue
  _r143_rel="${_r143_f#./}"
  _r143_exempted=0
  if [[ -f "$_r143_exempt" ]]; then
    while IFS= read -r _r143_e; do
      _r143_e="${_r143_e%%$'\r'}"
      [[ -z "$_r143_e" || "$_r143_e" == \#* ]] && continue
      # Prefix match supports both exact files and directory prefixes (trailing /).
      if [[ "$_r143_rel" == "$_r143_e" || "$_r143_rel" == "$_r143_e"* ]]; then
        _r143_exempted=1
        break
      fi
    done < "$_r143_exempt"
  fi
  if [[ $_r143_exempted -eq 0 ]]; then
    _r143_bad="${_r143_bad}${_r143_rel} "
  fi
done <<< "$_r143_hits"
if [[ -n "$_r143_bad" ]]; then
  fail_rule "local_plan_path_ban" "active authority references local plan path D:\\.claude\\plans (both separators) in non-exempt surface(s): ${_r143_bad}-- Rule G-26 / E191 (exempt list: $_r143_exempt)"
  _r143_fail=1
fi
[[ $_r143_fail -eq 0 ]] && pass_rule "local_plan_path_ban"

