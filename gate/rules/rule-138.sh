#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 138 — productclaim_placeholder_decreasing. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 138 — productclaim_placeholder_decreasing (enforcer E186, kernel Rule G-21)
#
# Phase A Wave 5 (advisory at landing). Count of product_claim_placeholder:true
# markers in the corpus MUST decrease monotonically across Phase B cluster
# cycles; reaching zero is the Phase B convergence signal. At landing the
# count is the Phase B backlog baseline; the rule passes vacuously until a
# baseline is recorded.
# ---------------------------------------------------------------------------
_r138_fail=0
[[ $_r138_fail -eq 0 ]] && pass_rule "productclaim_placeholder_decreasing"

