#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 108 — governance_text_java_anchor_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 108 — governance_text_java_anchor_truth (enforcer E153)
#
# Family B prevention — closes rc16 P1-2 (stale
# SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne in
# rule-R-K.md + P-K.md after rc15 rename). Per ADR-0093.
#
# scope_surfaces: docs/governance/rules/*.md, docs/governance/principles/P-*.md
#
# The rule scans for ClassName.methodName tokens that look like Java
# evidence anchors. For each, it requires either (a) the method to exist
# in agent-*/src/{main,test}/java/, OR (b) a nearby historical marker.
# Marker tokens (within ±2 lines): formerly|renamed from|pre-rc[0-9]+|
# superseded|removed|historical. Filters out non-Java patterns (URLs,
# config keys with dot-notation, etc.) via shape constraints.
# ---------------------------------------------------------------------------
_r108_fail=0
# Class.method tokens with PascalCase class + camelCase method, length-bounded.
_r108_pattern='\b([A-Z][A-Za-z0-9_]{2,40})\.([a-z][A-Za-z0-9_]{2,40})\b'
for _r108_dir in docs/governance/rules docs/governance/principles; do
  [[ -d "$_r108_dir" ]] || continue
  for _r108_file in "$_r108_dir"/*.md; do
    [[ -f "$_r108_file" ]] || continue
    # Find lines with potential anchors
    grep -nE "$_r108_pattern" "$_r108_file" 2>/dev/null | while IFS= read -r _r108_line; do
      _r108_lineno=$(echo "$_r108_line" | cut -d: -f1)
      _r108_content=$(echo "$_r108_line" | cut -d: -f2-)
      # Filter out non-Java tokens: URLs, hyphenated names, etc.
      # Pull each class.method token
      echo "$_r108_content" | grep -oE "$_r108_pattern" | sort -u | while IFS= read -r _r108_token; do
        [[ -z "$_r108_token" ]] && continue
        _r108_cls=$(echo "$_r108_token" | cut -d. -f1)
        _r108_mth=$(echo "$_r108_token" | cut -d. -f2)
        # Skip obvious false positives: classes ending in xyz that aren't real Java types
        # Allow only PascalCase class with length 4+
        [[ ${#_r108_cls} -lt 4 ]] && continue
        # Skip common method names + file extensions that are not real Java methods
        case "$_r108_mth" in
          equals|hashCode|toString|getName|getValue|getId) continue;;
          java|yaml|md|properties|txt|json|xml|sh|sql|bash|py|tpl|spec|sample|class|impl|yml) continue;;
        esac
        # Require method length ≥ 5 to filter out short tokens that look like extensions
        [[ ${#_r108_mth} -lt 5 ]] && continue
        # Check for historical marker within ±2 lines
        _r108_start=$((_r108_lineno - 2))
        [[ $_r108_start -lt 1 ]] && _r108_start=1
        _r108_end=$((_r108_lineno + 2))
        _r108_context=$(sed -n "${_r108_start},${_r108_end}p" "$_r108_file" 2>/dev/null || true)
        if echo "$_r108_context" | grep -qE '(formerly|renamed from|pre-rc[0-9]+|superseded|removed|historical|deleted|deprecated|[Dd]eferred to W|deferred_body|Rule \(draft\)|draft\):)'; then
          continue
        fi
        # Look for the class file
        _r108_class_file=$(find agent-*/src -name "${_r108_cls}.java" -type f 2>/dev/null | head -1)
        if [[ -z "$_r108_class_file" ]]; then
          # Class doesn't exist anywhere — could be deleted or never existed
          # Only flag if the class looks load-bearing (has uppercase + a recognizable suffix)
          case "$_r108_cls" in
            *IT|*Test|*Repository|*Service|*Controller|*Component|*Configuration|*Properties|*Filter|*Listener|*Hook|*Executor|*Registry|*Envelope|*Signal|*Context|*Response|*Resolver)
              fail_rule "governance_text_java_anchor_truth" "$_r108_file:$_r108_lineno references $_r108_token but class $_r108_cls not found in agent-*/src/ -- Rule 108 / E153 (Family B per ADR-0093)"
              _r108_fail=1
              ;;
          esac
          continue
        fi
        # Class exists — check for the identifier (method call, record component, field, or accessor).
        # Allow `name(`, `name,`, `name)` patterns to cover methods + record components + auto-generated accessors.
        if ! grep -qE "\\b${_r108_mth}\\s*[(,)]" "$_r108_class_file" 2>/dev/null; then
          fail_rule "governance_text_java_anchor_truth" "$_r108_file:$_r108_lineno references $_r108_token but identifier $_r108_mth not found in $_r108_class_file -- Rule 108 / E153 (Family B per ADR-0093)"
          _r108_fail=1
        fi
      done
    done
  done
done
_r108_stale_java_refs=$(find agent-* -path '*/src/main/java/*' -type f -name '*.java' 2>/dev/null \
  | xargs grep -nE 'com\.huawei\.ascend\.bus\.s2c\.ReflectionEnvelopeRouter|com\.huawei\.ascend\.evolve\.online\.SlowTrackJudge|Lives in \{@code runtime\.s2c\.spi\}' 2>/dev/null || true)
if [[ -n "$_r108_stale_java_refs" ]]; then
  while IFS= read -r _r108_stale_line; do
    [[ -z "$_r108_stale_line" ]] && continue
    fail_rule "governance_text_java_anchor_truth" "$_r108_stale_line references a pre-.spi Java package anchor after the ADR-0088/rc27 relocation; update it to the current package or add an explicitly historical package-lineage note outside the stale anchor wording."
    _r108_fail=1
  done <<< "$_r108_stale_java_refs"
fi
if [[ $_r108_fail -eq 0 ]]; then pass_rule "governance_text_java_anchor_truth"; fi

# ---------------------------------------------------------------------------
