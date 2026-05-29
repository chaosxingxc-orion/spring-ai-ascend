#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 121 — whitebox_quality_reports. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 121 — whitebox_quality_reports (enforcer E169)
#
# Operationalises Rule G-12. Maven owns execution of SpotBugs, PMD, and
# Checkstyle through the quality profile; this gate owns repository semantics:
# report presence, high-confidence SpotBugs blocking, low-dispute Checkstyle
# blocking, and PMD review-trigger summarisation.
#
# scope_surfaces: pom.xml, config/spotbugs/exclude.xml, config/pmd/pmd-ruleset.xml, config/checkstyle/checkstyle.xml, gate/lib/check_whitebox_quality.sh, .github/workflows/ci.yml
# ---------------------------------------------------------------------------
_r121_fail=0
if ! command -v check_whitebox_quality_reports >/dev/null 2>&1; then
  fail_rule "whitebox_quality_reports" "helper-missing: gate/lib/check_whitebox_quality.sh not sourced -- Rule G-12 / E169"
  _r121_fail=1
else
  _r121_out=$(check_whitebox_quality_reports 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ -z "$_s" ]] && continue
    if [[ "$_s" == "FAIL" ]]; then
      fail_rule "whitebox_quality_reports" "$_f: $_d -- Rule G-12 / E169"
      _r121_fail=1
    elif [[ "$_s" == "INFO" ]]; then
      printf 'INFO: whitebox_quality_reports -- %s: %s\n' "$_f" "$_d"
    fi
  done <<< "$_r121_out"
fi
[[ $_r121_fail -eq 0 ]] && pass_rule "whitebox_quality_reports"

# ---------------------------------------------------------------------------
