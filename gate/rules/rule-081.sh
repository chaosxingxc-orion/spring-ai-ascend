#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 81 — skeleton_module_has_no_production_java. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 81 — skeleton_module_has_no_production_java (enforcer E114)
#
# For every reactor module whose root ARCHITECTURE.md frontmatter status:
# contains the token "skeleton", the module's src/main/java/**/*.java tree
# MUST contain only package-info.java OR placeholder SPI stubs whose first
# 30 lines name a "placeholder" keyword with an ADR-NNNN waiver. Modules
# with extracted production code (e.g., agent-execution-engine post-ADR-0079,
# agent-middleware post-ADR-0073) MUST NOT carry a "skeleton" status.
# ---------------------------------------------------------------------------
_r81_fail=0
for _r81_arch in architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md; do
  [[ -f "$_r81_arch" ]] || continue
  _r81_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r81_arch" 2>/dev/null)
  if [[ "$_r81_status" == *skeleton* ]]; then
    _r81_module="${_r81_arch%/ARCHITECTURE.md}"
    _r81_src="$_r81_module/src/main/java"
    [[ -d "$_r81_src" ]] || continue
    while IFS= read -r _r81_java; do
      [[ -z "$_r81_java" ]] && continue
      _r81_basename="$(basename "$_r81_java")"
      if [[ "$_r81_basename" == "package-info.java" ]]; then continue; fi
      if head -n 30 "$_r81_java" 2>/dev/null | grep -qE 'placeholder.*ADR-[0-9]{4}|ADR-[0-9]{4}.*placeholder'; then continue; fi
      fail_rule "skeleton_module_has_no_production_java" "$_r81_java in skeleton module $_r81_module is neither package-info.java nor an ADR-waived placeholder -- Rule 81 / E114 (status claims skeleton but production code is present)"
      _r81_fail=1
    done < <(find "$_r81_src" -name '*.java' -type f 2>/dev/null)
  fi
done
if [[ $_r81_fail -eq 0 ]]; then pass_rule "skeleton_module_has_no_production_java"; fi

