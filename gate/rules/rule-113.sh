#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 113 — legacy_paren_no_reintroduction_and_migration_doc_complete. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

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

if [[ -f "$_r113_enforcers" ]]; then
  _r113_paren_hits=$(grep -nE '\(legacy Rule [0-9]+' "$_r113_enforcers" 2>/dev/null || true)
  if [[ -n "$_r113_paren_hits" ]]; then
    while IFS= read -r _r113_line; do
      [[ -z "$_r113_line" ]] && continue
      fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_enforcers:$_r113_line -- reintroduced (legacy Rule NN ...) parenthetical (rc18 Wave 4 removed these; legacy mapping belongs in gate/rule-number-migration.md). Rule 113 / E160 (ADR-0096 Wave 2)"
      _r113_fail=1
    done <<< "$_r113_paren_hits"
  fi
fi

if [[ ! -f "$_r113_migration" ]]; then
  fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_migration missing -- rc18 Wave 4 created this as legacy-mapping SSOT. Rule 113 / E160 (ADR-0096)"
  _r113_fail=1
else
  for _r113_required in 'Legacy numeric' 'rc17 sub-rule splits'; do
    if ! grep -qF "$_r113_required" "$_r113_migration" 2>/dev/null; then
      fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_migration missing required section heading containing '$_r113_required'. Rule 113 / E160"
      _r113_fail=1
    fi
  done
fi

if [[ $_r113_fail -eq 0 ]]; then pass_rule "legacy_paren_no_reintroduction_and_migration_doc_complete"; fi
