#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 114 — rule_card_filename_dot_convention. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 114 — rule_card_filename_dot_convention (enforcer E161)
#
# Per ADR-0096 rc19 Wave 4 + docs/governance/rules/README.md convention:
# every rule card under docs/governance/rules/rule-*.md MUST match the
# dotted-suffix filename pattern. Hyphenated variants (e.g.,
# rule-G-3-1.md) are rejected because the gate's rule_id ↔ card-file
# mapping uses dot notation (rule_id: G-3.1 → rule-G-3.1.md). rc17
# corrective #1 had 5 file renames precisely because of this hyphen-vs-dot
# confusion; this rule prevents the next contributor from re-creating
# the same trap.
#
# Accepted: rule-D-N.md, rule-R-X.md, rule-R-X.N.md, rule-R-X.c.md (R-A.c
# hybrid), rule-G-N.md, rule-G-N.N.md, rule-M-N.md, README.md.
#
# scope_surfaces: docs/governance/rules/*.md
# ---------------------------------------------------------------------------
_r114_fail=0
_r114_dir="docs/governance/rules"
if [[ -d "$_r114_dir" ]]; then
  while IFS= read -r _r114_file; do
    [[ -z "$_r114_file" ]] && continue
    _r114_basename=$(basename "$_r114_file")
    # README.md is allowed; ignore. Other names must match the convention.
    [[ "$_r114_basename" == "README.md" ]] && continue
    # Convention regex: rule-(D|R|G|M)-<letter>(.<suffix>)?.md
    # <suffix> is single letter (a-z) for old sub-clause OR digit(.letter)?
    # for new sub-rule (e.g., R-C.1, R-C.2.a).
    # Acceptable: rule-D-1.md, rule-R-A.md, rule-R-A.c.md, rule-R-C.1.md,
    # rule-R-C.2.md, rule-G-3.1.md, rule-G-9.md, rule-M-2.md.
    if [[ ! "$_r114_basename" =~ ^rule-[DRGM]-[A-Z0-9]+(\.[a-z0-9]+)?\.md$ ]]; then
      fail_rule "rule_card_filename_dot_convention" "$_r114_file: filename does not match rule card convention (rule-PREFIX-ID[.SUBID].md with dot, NOT hyphen). Per docs/governance/rules/README.md + ADR-0098 Wave 4 (rc21 widened ID to multi-char to admit G-10, G-11). Rule 114 / E161"
      _r114_fail=1
    fi
  done < <(find "$_r114_dir" -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort)
fi
if [[ $_r114_fail -eq 0 ]]; then pass_rule "rule_card_filename_dot_convention"; fi

# ---------------------------------------------------------------------------
