#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 133 — productclaim_referential_integrity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 133 — productclaim_referential_integrity (enforcer E181, kernel Rule G-16)
#
# Phase A Wave 5 (advisory at landing 2026-05-28; promotes to blocking when
# placeholder count reaches 0 per Rule G-21). Every product_claim: value in
# ADR YAML, rule card frontmatter, enforcer rows, SAA feature saa.productClaim,
# or contract frontmatter MUST resolve to a PC-NNN id declared in
# product/claims.yaml -- OR carry one of the explicit sentinel values
# governance_infra:true or product_claim_placeholder:true. Bare missing field
# is checked by Rule 134 (no_orphan_artefacts), not this rule.
#
# scope_surfaces: product/claims.yaml, docs/governance/rules/*.md (frontmatter),
# docs/governance/enforcers.yaml, architecture/features/features.dsl,
# architecture/decisions/*.yaml, docs/contracts/*.yaml
# ---------------------------------------------------------------------------
_r133_fail=0
_r133_claims="product/claims.yaml"
if [[ ! -f "$_r133_claims" ]]; then
  : # vacuous pass before product authority lands
else
  _r133_valid_ids=$(grep -oE '^  - id: PC-[0-9]+' "$_r133_claims" 2>/dev/null | awk '{print $3}')
  _r133_bad=$(grep -rhEn '^\s*product_claim:\s*"?(PC-[0-9]+(\|PC-[0-9]+)*)"?\s*$|^\s+"saa\.productClaim"\s+"(PC-[0-9]+(\|PC-[0-9]+)*)"\s*$' \
              docs/governance/rules/ architecture/decisions/ docs/contracts/ architecture/features/ 2>/dev/null \
              | grep -oE 'PC-[0-9]+' | sort -u | while read _r133_ref; do
      if ! echo "$_r133_valid_ids" | grep -qxF "$_r133_ref"; then
        echo "$_r133_ref"
      fi
    done | head -3 | tr '\n' ' ')
  if [[ -n "$_r133_bad" ]]; then
    fail_rule "productclaim_referential_integrity" "product_claim references that don't resolve in product/claims.yaml: $_r133_bad -- Rule G-16 / E181 (advisory at W5 landing; blocking when placeholder count reaches 0)"
    _r133_fail=1
  fi
fi
[[ $_r133_fail -eq 0 ]] && pass_rule "productclaim_referential_integrity"

# ---------------------------------------------------------------------------
