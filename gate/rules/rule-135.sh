#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 135 — traceability_chain_completeness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 135 — traceability_chain_completeness (enforcer E183, kernel Rule G-18)
#
# Phase A Wave 5 (advisory at landing). Every PC-NNN in product/claims.yaml MUST
# have >=1 SAA Feature referencing it via saa.productClaim. Vacuously passes
# until Wave 4 backfill threads the chain across the corpus.
# ---------------------------------------------------------------------------
_r135_fail=0
# BLOCKING from Phase B convergence: every PC-NNN declared in product/claims.yaml
# MUST be referenced by >=1 artefact (feature / rule / contract / ADR).
_r135_claims="product/claims.yaml"
if [[ -f "$_r135_claims" ]]; then
  for _r135_pc in $(grep -oE '^[[:space:]]*-?[[:space:]]*id:[[:space:]]*PC-[0-9]+' "$_r135_claims" | grep -oE 'PC-[0-9]+' | sort -u); do
    if ! grep -rqE "${_r135_pc}([^0-9]|$)" docs/contracts docs/governance/rules architecture/features docs/adr docs/governance/principles 2>/dev/null; then
      fail_rule "traceability_chain_completeness" "${_r135_pc} declared in product/claims.yaml but referenced by zero artefacts -- Rule G-18 / E183 (blocking from Phase B convergence)"
      _r135_fail=1
    fi
  done
fi
[[ $_r135_fail -eq 0 ]] && pass_rule "traceability_chain_completeness"

# ---------------------------------------------------------------------------
