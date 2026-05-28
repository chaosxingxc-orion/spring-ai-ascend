#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 137 — governance_infra_honesty. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 137 — governance_infra_honesty (enforcer E185, kernel Rule G-20)
#
# Phase A Wave 5 (advisory at landing). An artefact (rule card, ADR, contract,
# feature) marked governance_infra:true MUST NOT use product-value vocabulary
# (customer / beneficiary / user-facing claim / saves time/cost) in its body
# prose. Words reserved for product-claim-bound artefacts. Advisory until
# enough artefacts are classified to support precise lexicon enforcement.
# ---------------------------------------------------------------------------
_r137_fail=0
[[ $_r137_fail -eq 0 ]] && pass_rule "governance_infra_honesty"

# ---------------------------------------------------------------------------
