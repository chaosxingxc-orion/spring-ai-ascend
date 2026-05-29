#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28h — l1_review_checklist_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28h — l1_review_checklist_present (enforcer E31)
# Every L1 ADR (0055–0059) MUST include the §16 review checklist subsection.
# ---------------------------------------------------------------------------
_r28h_fail=0
for _n in 0055 0056 0057 0058 0059 0060; do
  _adr=$(find docs/adr -maxdepth 1 -name "${_n}-*.md" 2>/dev/null | head -1)
  [[ -z "$_adr" ]] && continue
  if ! grep -qE '(§16 Review Checklist|L1 Review Checklist)' "$_adr" 2>/dev/null; then
    fail_rule "l1_review_checklist_present" "$_adr missing '§16 Review Checklist' subsection. Per Rule 28h / enforcer E31 / architect guidance §16."
    _r28h_fail=1
  fi
done
if [[ $_r28h_fail -eq 0 ]]; then pass_rule "l1_review_checklist_present"; fi

# ---------------------------------------------------------------------------
