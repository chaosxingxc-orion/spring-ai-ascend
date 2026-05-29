#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 79 — rule_79_runbook_present_and_cited. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 79 — rule_79_runbook_present_and_cited (enforcer E112)
#
# Three invariants:
#   1. docs/runbooks/debug-first-evidence.md exists.
#   2. docs/runbooks/debug-first-evidence.md contains the literal string
#      "Evidence-First Debug Sequence" (catches drift-by-replacement).
#   3. docs/governance/rules/rule-79.md references the runbook path
#      (catches card-runbook link breakage on rename).
# ---------------------------------------------------------------------------
_r79_fail=0
_r79_runbook="docs/runbooks/debug-first-evidence.md"
_r79_card="docs/governance/rules/rule-79.md"
if [[ ! -f "$_r79_runbook" ]]; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_runbook missing — Rule 79 / E112 (runbook required by docs/governance/rules/rule-79.md)"
  _r79_fail=1
elif ! grep -qF 'Evidence-First Debug Sequence' "$_r79_runbook" 2>/dev/null; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_runbook missing the canonical title string 'Evidence-First Debug Sequence' — Rule 79 / E112"
  _r79_fail=1
fi
if [[ -f "$_r79_card" ]] && ! grep -qF 'docs/runbooks/debug-first-evidence.md' "$_r79_card" 2>/dev/null; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_card does not reference docs/runbooks/debug-first-evidence.md — Rule 79 / E112 (card-runbook link broken)"
  _r79_fail=1
fi
if [[ $_r79_fail -eq 0 ]]; then pass_rule "rule_79_runbook_present_and_cited"; fi

# ===========================================================================
# 2026-05-18 rc4 cross-constraint review response prevention wave -- Rules 80-83
# Authority: docs/governance/rules/rule-80.md ... rule-83.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md
# Closes finding families:
#   P0-1 ADR-vs-code drift after rc3 S2C refactor          -> Rule 80
#   P0-2 module-status drift after engine extraction         -> Rule 81
#   P1-1 baseline-count drift across entrypoints             -> Rule 82
#   P1-3 design-only contracts unregistered / dangling auth  -> Rule 83
# ===========================================================================

