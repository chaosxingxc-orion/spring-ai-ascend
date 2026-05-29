#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 113 — legacy_paren_no_reintroduction_and_migration_doc_complete. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 113 — legacy_paren_no_reintroduction_and_migration_doc_complete (enforcer E160)
#
# Per ADR-0096 rc19 Wave 2: Wave 4 (rc18) removed 9 `(legacy Rule NN — ...)`
# parentheticals from enforcers.yaml and created gate/rule-number-migration.md
# as the legacy mapping SSOT. Adversarial review (ADV-RC18-6) flagged that
# nothing prevented reintroduction or audited the migration doc.
#
#   sub-check .a: enforcers.yaml MUST NOT contain `(legacy Rule NN — ...)`
#                 parenthetical patterns (reintroduction guard).
#   sub-check .b: gate/rule-number-migration.md MUST exist AND contain both
#                 expected sections so the audit trail is preserved.
#
# scope_surfaces: docs/governance/enforcers.yaml, gate/rule-number-migration.md
# ---------------------------------------------------------------------------
_r113_fail=0
_r113_enforcers="docs/governance/enforcers.yaml"
_r113_migration="gate/rule-number-migration.md"

# Extract grep + heading checks into helper so fixtures source the same
# code — fixtures cannot drift from the production regex this way.
# shellcheck disable=SC1091
source "gate/lib/check_legacy_paren.sh"  # source gate/lib/check_legacy_paren.sh — Rule 113 helper extraction

_r113_paren_output=$(_check_legacy_paren_no_reintroduction "$_r113_enforcers")
if [[ -n "$_r113_paren_output" ]]; then
  while IFS= read -r _r113_line; do
    [[ -z "$_r113_line" ]] && continue
    fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_line"
    _r113_fail=1
  done <<< "$_r113_paren_output"
fi

_r113_migration_output=$(_check_migration_doc_complete "$_r113_migration" 'Legacy numeric' 'rc17 sub-rule splits')
if [[ -n "$_r113_migration_output" ]]; then
  while IFS= read -r _r113_line; do
    [[ -z "$_r113_line" ]] && continue
    fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_line"
    _r113_fail=1
  done <<< "$_r113_migration_output"
fi

if [[ $_r113_fail -eq 0 ]]; then pass_rule "legacy_paren_no_reintroduction_and_migration_doc_complete"; fi
