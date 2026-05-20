#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 111 — architecture_refresh_defect_family_re_eval_required. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 111 — architecture_refresh_defect_family_re_eval_required (enforcers E156 E157 E158)
#
# Operationalises Rule G-9 (Recurring-Defect Family Truth). Enforces three
# invariants on the recurring-defect-family ledger:
#   .a (E156) — yaml well-formedness: docs/governance/recurring-defect-families.yaml
#               exists, has top-level schema_version + last_updated + families,
#               and every family entry has 9 required per-family fields.
#   .b (E157) — freshness: yaml `last_updated:` ISO date is no older than the
#               commit date of the most recent refresh-signal change. Signals:
#               docs/adr/*.yaml, docs/governance/architecture-status.yaml,
#               docs/logs/releases/*.md, CLAUDE.md.
#   .c (E158) — yaml/md family-id parity: the set of `^  - id:` slugs in the
#               yaml equals the set of `F-...` ids referenced in
#               docs/governance/recurring-defect-families.md.
# Per ADR-0094 (rc17 recurring-defect-family-truth + rule-consolidation).
#
# scope_surfaces: docs/governance/recurring-defect-families.yaml, docs/governance/recurring-defect-families.md, docs/adr/, docs/logs/releases/, CLAUDE.md, docs/governance/architecture-status.yaml
# ---------------------------------------------------------------------------
_r111_yaml="docs/governance/recurring-defect-families.yaml"
_r111_md="docs/governance/recurring-defect-families.md"
_r111_fail=0

# Sub-check .a — yaml well-formedness
if [[ ! -f "$_r111_yaml" ]]; then
  fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_yaml missing -- Rule G-9.a / E156 (ADR-0094)"
  _r111_fail=1
else
  for _r111_topkey in schema_version last_updated families; do
    if ! grep -qE "^${_r111_topkey}:" "$_r111_yaml" 2>/dev/null; then
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_yaml missing top-level key '$_r111_topkey' -- Rule G-9.a / E156"
      _r111_fail=1
    fi
  done
  _r111_fam_count=$(grep -cE '^  - id:' "$_r111_yaml" 2>/dev/null)
  _r111_fam_count=${_r111_fam_count:-0}
  if [[ "$_r111_fam_count" -gt 0 ]]; then
    for _r111_field in title first_observed_rc last_observed_rc occurrences root_cause surfaces prevention_rules cleanup_status open_residual; do
      _r111_count=$(grep -cE "^    ${_r111_field}:" "$_r111_yaml" 2>/dev/null)
      _r111_count=${_r111_count:-0}
      if [[ "$_r111_count" -lt "$_r111_fam_count" ]]; then
        fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_yaml field '$_r111_field' appears $_r111_count times but $_r111_fam_count families declared -- Rule G-9.a / E156"
        _r111_fail=1
      fi
    done
  fi
fi

# Sub-check .b — freshness: yaml last_updated >= latest refresh-signal commit date
if [[ -f "$_r111_yaml" ]] && command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  _r111_yaml_date=$(awk '/^last_updated:/ { gsub(/[\"'\'']/,""); print $2; exit }' "$_r111_yaml" 2>/dev/null)
  if [[ -n "$_r111_yaml_date" ]]; then
    _r111_signal_date=$(git log -1 --format=%cI -- \
      'docs/adr/' \
      'docs/governance/architecture-status.yaml' \
      'docs/logs/releases/' \
      'CLAUDE.md' 2>/dev/null | cut -dT -f1)
    if [[ -n "$_r111_signal_date" && "$_r111_signal_date" > "$_r111_yaml_date" ]]; then
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_yaml last_updated=$_r111_yaml_date is older than refresh-signal commit date $_r111_signal_date -- Rule G-9.b / E157 (run /refresh-defect-archive)"
      _r111_fail=1
    fi
  fi
fi

# Sub-check .c — yaml/md family-id parity
if [[ -f "$_r111_yaml" && -f "$_r111_md" ]]; then
  _r111_yaml_ids_file="$(mktemp)"
  _r111_md_ids_file="$(mktemp)"
  awk '/^  - id:[[:space:]]+/ {print $3}' "$_r111_yaml" 2>/dev/null | sort -u > "$_r111_yaml_ids_file"
  grep -oE 'F-[a-z][a-z0-9-]*' "$_r111_md" 2>/dev/null | sort -u > "$_r111_md_ids_file"
  _r111_only_yaml=$(comm -23 "$_r111_yaml_ids_file" "$_r111_md_ids_file")
  _r111_only_md=$(comm -13 "$_r111_yaml_ids_file" "$_r111_md_ids_file")
  if [[ -n "$_r111_only_yaml" ]]; then
    fail_rule "architecture_refresh_defect_family_re_eval_required" "Family ids in yaml but missing from md: $(echo $_r111_only_yaml) -- Rule G-9.c / E158"
    _r111_fail=1
  fi
  if [[ -n "$_r111_only_md" ]]; then
    fail_rule "architecture_refresh_defect_family_re_eval_required" "Family ids in md but missing from yaml: $(echo $_r111_only_md) -- Rule G-9.c / E158"
    _r111_fail=1
  fi
  rm -f "$_r111_yaml_ids_file" "$_r111_md_ids_file"
fi

if [[ $_r111_fail -eq 0 ]]; then pass_rule "architecture_refresh_defect_family_re_eval_required"; fi

# === END OF RULES ===
# ---------------------------------------------------------------------------
if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
