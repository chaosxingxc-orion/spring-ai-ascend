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
# BLOCKING from Phase B convergence (2026-05-28): count of REAL product_claim_placeholder
# field markers (frontmatter/yaml field lines, not prose mentions in meta-rule docs)
# MUST be 0. Reaching 0 was the G-21 convergence signal that promoted G-16/17/18/20
# from advisory to blocking.
_r138_count=$(grep -rlE '^[[:space:]]*product_claim_placeholder:[[:space:]]*true[[:space:]]*$' docs/adr architecture/decisions docs/contracts docs/governance/rules docs/governance/principles architecture/features 2>/dev/null | wc -l | tr -d '[:space:]')
if [[ "${_r138_count:-0}" -gt 0 ]]; then
  fail_rule "productclaim_placeholder_decreasing" "${_r138_count} artefact(s) still carry product_claim_placeholder:true; Phase B convergence requires 0 -- Rule G-21 / E186 (blocking from convergence)"
  _r138_fail=1
fi
[[ $_r138_fail -eq 0 ]] && pass_rule "productclaim_placeholder_decreasing"

