#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 122 — proposal_immediate_scope_pending_contract_guard. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 122 — proposal_immediate_scope_pending_contract_guard (enforcer E170)
#
# Design proposals under docs/logs/reviews/ may be exploratory, but they MUST
# NOT claim immediate W0/W1 execution scope while the same document still says
# the boundary contracts are pending. This prevents release-readiness drift
# where a draft looks like current release authority.
# ---------------------------------------------------------------------------
_r122_fail=0
for _r122_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r122_file" ]] || continue
  if grep -qiE 'Target Wave:[^[:cntrl:]]*(W0/W1|Immediate Execution)' "$_r122_file" \
     && grep -qiE 'Pending Refinement|pending gaps|pending contract|pending refinement|TODO annotations' "$_r122_file"; then
    fail_rule "proposal_immediate_scope_pending_contract_guard" "$_r122_file claims immediate W0/W1 scope while carrying pending boundary-contract work -- Rule G-2 / E170"
    _r122_fail=1
  fi
done
[[ $_r122_fail -eq 0 ]] && pass_rule "proposal_immediate_scope_pending_contract_guard"

# ---------------------------------------------------------------------------
