#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 137 — governance_infra_honesty. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 137 — governance_infra_honesty (enforcer E185, kernel Rule G-20)
#
# Phase A Wave 5 (advisory at landing). An artefact (rule card, ADR, contract,
# feature) marked governance_infra:true MUST NOT use product-value vocabulary
# (customer / beneficiary / user-facing claim / saves time/cost) in its body
# prose. Words reserved for product-claim-bound artefacts. Advisory until
# enough artefacts are classified to support precise lexicon enforcement.
# ---------------------------------------------------------------------------
_r137_fail=0
# BLOCKING from Phase B convergence: artefacts marked governance_infra:true MUST
# NOT use product-value vocabulary in body prose. Narrow phrase list (not bare
# "user") to avoid false positives on governance docs that legitimately mention users.
_r137_hits=""
while IFS= read -r _r137_f; do
  [[ -z "$_r137_f" ]] && continue
  if grep -qiE 'delivers (customer|user) value|saves (the )?(customer|user)|user-facing (benefit|value)|customer benefit|compounds value for' "$_r137_f"; then
    _r137_hits="${_r137_hits}$(basename "$_r137_f") "
  fi
done < <(grep -rlE '^[[:space:]]*governance_infra:[[:space:]]*true' docs/governance/rules docs/adr docs/contracts 2>/dev/null)
if [[ -n "$_r137_hits" ]]; then
  fail_rule "governance_infra_honesty" "governance_infra:true artefact(s) use product-value vocabulary: ${_r137_hits}-- Rule G-20 / E185 (blocking from Phase B convergence)"
  _r137_fail=1
fi
[[ $_r137_fail -eq 0 ]] && pass_rule "governance_infra_honesty"

# ---------------------------------------------------------------------------
