#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 112 — meta_rule_self_application_check. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 112 — meta_rule_self_application_check (enforcer E159) [META]
#
# Per ADR-0096 (rc19 Wave 1 / F-recursive-prevention-irony permanent close):
# every gate rule whose `# Rule N — slug` header line carries the `[META]`
# marker MUST source a helper from `gate/lib/check_*.sh` (the helper-
# extraction template established by Rule 111 in rc18 Wave 1) within its
# rule body. This structurally prevents future META rules from re-creating
# Pattern D drift — the class that bit rc17 Rule 111.
#
# Rule 110 META already gates scope_surfaces declaration + fixture count,
# but does NOT gate the helper-extraction discipline. Rule 112 closes that
# gap. Together Rules 110 + 111 + 112 prevent the F-recursive-prevention-
# irony family from recurring on future META rules.
#
# scope_surfaces: gate/check_architecture_sync.sh (META-marked rule blocks), gate/lib/check_*.sh
#
# Algorithm:
#   1. Find each `# Rule N — slug ... [META]` header (incl. Rule 112 itself).
#   2. Scan the next 80 lines (or until next `# ---` separator) for
#      `source ... gate/lib/check_*.sh`.
#   3. Fail if no such source statement found in body.
#
# Sources gate/lib/check_recurring_families.sh as a no-op (proves Rule 112
# itself follows its own discipline — dogfooding closes the recursive
# irony at Rule 112's own layer).
# ---------------------------------------------------------------------------
# shellcheck disable=SC1091
source "gate/lib/check_recurring_families.sh"  # Rule 112 self-application
_r112_fail=0
_r112_canonical="gate/check_architecture_sync.sh"
if [[ -f "$_r112_canonical" ]]; then
  while IFS= read -r _r112_meta_line; do
    [[ -z "$_r112_meta_line" ]] && continue
    _r112_lineno=$(printf '%s' "$_r112_meta_line" | cut -d: -f1)
    _r112_slug=$(printf '%s' "$_r112_meta_line" | sed -nE 's|^[0-9]+:# Rule ([0-9]+[a-z]?) — ([a-z_]+).*\[META\].*|\1:\2|p')
    [[ -z "$_r112_slug" ]] && continue
    _r112_rule_num=$(printf '%s' "$_r112_slug" | cut -d: -f1)
    _r112_rule_slug=$(printf '%s' "$_r112_slug" | cut -d: -f2)
    _r112_body=$(awk -v start="$_r112_lineno" 'NR > start && NR <= start + 80' "$_r112_canonical")
    if ! printf '%s' "$_r112_body" | grep -qE '(source|\. )[[:space:]]+["'\''[:space:]]*[^"'\''[:space:]]*gate/lib/check_[a-zA-Z_]+\.sh'; then
      fail_rule "meta_rule_self_application_check" "Rule $_r112_rule_num ($_r112_rule_slug) is marked [META] but body does not source a gate/lib/check_*.sh helper within 80 lines of header. F-recursive-prevention-irony pattern — every META rule MUST use the helper-extraction template per ADR-0096. Rule 112 / E159"
      _r112_fail=1
    fi
  done < <(grep -nE '^# Rule [0-9]+[a-z]? — .*\[META\]' "$_r112_canonical" 2>/dev/null)
fi
if [[ $_r112_fail -eq 0 ]]; then pass_rule "meta_rule_self_application_check"; fi
