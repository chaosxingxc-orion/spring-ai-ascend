#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 111 — architecture_refresh_defect_family_re_eval_required. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 111 — architecture_refresh_defect_family_re_eval_required (enforcers E156 E157 E158) [META]
#
# Operationalises Rule G-9 (Recurring-Defect Family Truth). Per ADR-0095
# rc18 Wave 1, the 3 sub-checks delegate to shared helpers in
# gate/lib/check_recurring_families.sh — closes F-kernel-vs-implementation-
# drift on Rule 111 itself (Wave 1 finding: fixtures and gate both invoke
# the same code, no inline re-implementation).
#
# Hardening fixes (per ADR-0095):
#   1a — yaml's own git commit date drives freshness (not hand-edited last_updated)
#   1b — families: [] is rejected (hard non-empty assertion)
#   1c — cleanup_status enum value validated against {closed | structurally_addressed |
#        partial | incomplete | monitoring}
#   1d — per-family block-bucket: each family has every required field exactly once
#        (closes duplicate-field compensation blind spot)
#   1e — last_updated must be ISO YYYY-MM-DD format
#   1f — md parity anchored to ^### F- H3 headings (mirrors yaml ^  - id: anchoring,
#        closes prose false-positives)
#   1g — refresh-signal path filter INCLUDES docs/governance/rules/
#   1h — shallow-clone fail-closed (was silent pass)
#
# Sub-checks:
#   .a (E156) — yaml well-formedness (file + top-level keys + ISO date +
#               non-empty + per-family field count + enum validation)
#   .b (E157) — freshness via yaml file's own git commit date vs latest
#               refresh-signal commit date
#   .c (E158) — yaml/md family-id parity, both sides H3/structural-anchored
#
# Per ADR-0094 (rc17 introduction) + ADR-0095 (rc18 Wave 1 hardening).
#
# scope_surfaces: docs/governance/recurring-defect-families.yaml, docs/governance/recurring-defect-families.md, docs/adr/, docs/logs/releases/, CLAUDE.md, docs/governance/architecture-status.yaml, docs/governance/rules/, gate/lib/check_recurring_families.sh
# ---------------------------------------------------------------------------
_r111_yaml="docs/governance/recurring-defect-families.yaml"
_r111_md="docs/governance/recurring-defect-families.md"
_r111_helper="gate/lib/check_recurring_families.sh"
_r111_fail=0

if [[ ! -f "$_r111_helper" ]]; then
  fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_helper missing -- Rule G-9 / ADR-0095 Wave 1 helper file required"
else
  # Source helpers once; capture each sub-check's stdout for fail_rule emission.
  # shellcheck disable=SC1090
  source "$_r111_helper"  # source gate/lib/check_recurring_families.sh — Rule 112 [META] self-application marker

  # Sub-check .a — yaml well-formedness (covers fixes 1b, 1c, 1d, 1e)
  _r111_a_output=$(_check_recurring_families_yaml_wellformed "$_r111_yaml")
  if [[ -n "$_r111_a_output" ]]; then
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
    done <<< "$_r111_a_output"
  fi

  # Sub-check .b — freshness (covers fixes 1a, 1g, 1h)
  _r111_b_output=$(_check_recurring_families_freshness "$_r111_yaml" ".")
  if [[ -n "$_r111_b_output" ]]; then
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
    done <<< "$_r111_b_output"
  fi

  # Sub-check .c — md/yaml parity (covers fix 1f)
  _r111_c_output=$(_check_recurring_families_md_yaml_parity "$_r111_yaml" "$_r111_md")
  if [[ -n "$_r111_c_output" ]]; then
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
    done <<< "$_r111_c_output"
  fi
fi

if [[ $_r111_fail -eq 0 ]]; then pass_rule "architecture_refresh_defect_family_re_eval_required"; fi

# ---------------------------------------------------------------------------
