#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 29 — whitepaper_alignment_matrix_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 29 — whitepaper_alignment_matrix_present
# ADR-0049 + P2-1: docs/governance/whitepaper-alignment-matrix.md must exist
# and must contain rows for each of the 20 required whitepaper concepts.
# Closes the concept-traceability gap from the whitepaper-alignment review.
# ---------------------------------------------------------------------------
_r29_fail=0
_matrix29='docs/governance/whitepaper-alignment-matrix.md'
if [[ ! -f "$_matrix29" ]]; then
  fail_rule "whitepaper_alignment_matrix_present" "$_matrix29 missing. Per Gate Rule 29 / ADR-0049 the whitepaper alignment matrix must exist as concept-level traceability from whitepaper to active architecture."
  _r29_fail=1
else
  _required29=(
    'C/S separation'
    'Task Cursor'
    'Dynamic Hydration'
    'Sync State'
    'Sub-Stream'
    'Yield & Handoff'
    'Business ontology ownership'
    'S-side execution trajectory ownership'
    'Placeholder exemption'
    'Full Trace vs Node Snapshot'
    'Lazy mounting'
    'Skill Topology Scheduler'
    'C-side business degradation authority'
    'Session/context decoupling'
    'Workflow Intermediary'
    'Three-track bus'
    'Capability bidding'
    'Permission issuance'
    'Chronos Hydration'
    'Service Layer microservice commitment'
  )
  for _concept29 in "${_required29[@]}"; do
    if ! grep -qF "$_concept29" "$_matrix29"; then
      fail_rule "whitepaper_alignment_matrix_present" "$_matrix29 missing required concept row '$_concept29'. Per Gate Rule 29 all 20 named whitepaper concepts must appear in the alignment matrix."
      _r29_fail=1
    fi
  done
fi
if [[ $_r29_fail -eq 0 ]]; then pass_rule "whitepaper_alignment_matrix_present"; fi

# ---------------------------------------------------------------------------
