#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 134 — no_orphan_artefacts. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 134 — no_orphan_artefacts (enforcer E182, kernel Rule G-17)
#
# Phase A Wave 5 (advisory at landing 2026-05-28). Every ADR YAML / rule card /
# enforcer / SAA Feature / contract MUST declare one of: (a) product_claim: with
# a PC-NNN value, (b) governance_infra: true, (c) product_claim_placeholder: true
# (Wave 4 backfill marker). Missing all three = orphan. Counts orphans and emits
# info; doesn't fail unless orphan count exceeds the per-corpus advisory
# threshold (currently 100% -- vacuous-PASS until Wave 4 backfill brings the
# threshold down).
# ---------------------------------------------------------------------------
_r134_fail=0
# BLOCKING from Phase B convergence (2026-05-28, placeholder count reached 0).
# Every ADR yaml / contract yaml / rule card / principle card MUST carry one of
# product_claim: / governance_infra: / product_claim_placeholder: markers.
_r134_orphans=""
for _r134_f in docs/adr/*.yaml architecture/decisions/*.yaml docs/contracts/*.yaml docs/governance/rules/rule-*.md docs/governance/principles/P-*.md; do
  [[ -f "$_r134_f" ]] || continue
  if ! grep -qE '^[[:space:]]*(product_claim|governance_infra|product_claim_placeholder):' "$_r134_f"; then
    _r134_orphans="${_r134_orphans}$(basename "$_r134_f") "
  fi
done
if [[ -n "$_r134_orphans" ]]; then
  fail_rule "no_orphan_artefacts" "artefacts without a ProductClaim marker (orphans): ${_r134_orphans}-- Rule G-17 / E182 (blocking from Phase B convergence)"
  _r134_fail=1
fi
[[ $_r134_fail -eq 0 ]] && pass_rule "no_orphan_artefacts"

# ---------------------------------------------------------------------------
