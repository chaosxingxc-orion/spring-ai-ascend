#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 144 — adr_normalization. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 144 — adr_normalization (enforcers E192 + E193, kernel Rule G-28)
#
# Authority: ADR-0160 (ADR Governance Model). One rule, two advisory helpers
# that together pin the normalized-ADR review surface:
#   * gate/lib/check_adr_taxonomy.py (E192, slug adr_taxonomy_normalization) —
#     validates each normalized ADR view (docs/adr/normalized/ADR-NNNN.yaml)
#     against adr-taxonomy.yaml + adr-governance-policy.yaml: required fields,
#     the closed five-value current_state set, the per-state field invariants,
#     and the per-level decision_type altitude (forbidden lower-altitude leak).
#   * gate/lib/check_historical_adr_governance.py (E193, slug
#     historical_adr_governance) — ledger totality (every raw ADR in adrs.json
#     has a docs/governance/adr-remediation-ledger.yaml entry) + normalized-view
#     coverage (every accepted ADR has a normalized view).
# The taxonomy helper (E192) runs ADVISORY here: it reports findings but does not
# block. The historical-ADR helper (E193) is BLOCKING: the dedicated
# normalization wave back-filled the ledger + docs/adr/normalized/ to TOTAL
# coverage (every raw ADR in adrs.json has a ledger entry; every accepted ADR has
# a normalized view), so the ledger-totality + normalized-view-coverage assertion
# is the ADR-0160 ratchet's full-blocking rung and an un-entried raw ADR or an
# accepted ADR with no view now fails the gate. The helper-missing branch still
# fails closed; a non-zero helper rc (a vanished apex adrs.json, OR a real
# coverage finding under --mode blocking) is a hard fail. A missing python
# interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env); a
# missing PyYAML surfaces as the helper's own config error.
#
# scope_surfaces: docs/governance/adr-taxonomy.yaml, docs/governance/adr-governance-policy.yaml, docs/governance/adr-remediation-ledger.yaml, docs/adr/normalized/*, architecture/facts/generated/adrs.json, gate/lib/check_adr_taxonomy.py, gate/lib/check_historical_adr_governance.py
# ---------------------------------------------------------------------------
_r144_tax_fail=0
_r144_tax_helper="gate/lib/check_adr_taxonomy.py"
if [[ ! -f "$_r144_tax_helper" ]]; then
  fail_rule "adr_taxonomy_normalization" "$_r144_tax_helper missing -- Rule G-28 / E192"
  _r144_tax_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r144_tax_out=$("$GATE_PYTHON_BIN" "$_r144_tax_helper" --mode advisory 2>&1)
  # Advisory: report the finding summary to the gate log, never block.
  _r144_tax_sum=$(printf '%s' "$_r144_tax_out" | grep -E 'finding\(s\)' | tail -1)
  [[ -n "$_r144_tax_sum" ]] && echo "ADVISORY (Rule G-28 / E192): $_r144_tax_sum"
fi
[[ $_r144_tax_fail -eq 0 ]] && pass_rule "adr_taxonomy_normalization"

_r144_hist_fail=0
_r144_hist_helper="gate/lib/check_historical_adr_governance.py"
if [[ ! -f "$_r144_hist_helper" ]]; then
  fail_rule "historical_adr_governance" "$_r144_hist_helper missing -- Rule G-28 / E193"
  _r144_hist_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r144_hist_out=$("$GATE_PYTHON_BIN" "$_r144_hist_helper" --mode blocking 2>&1)
  _r144_hist_rc=$?
  # Blocking: a non-zero rc is either a fatal ERROR (a vanished apex adrs.json —
  # the helper exits 1 in every mode) or a real coverage finding (a raw ADR with
  # no ledger entry, or an accepted ADR with no normalized view). Surface the
  # first ERROR/BLOCKING line so neither a vanished apex fact nor a coverage gap
  # ever green-washes.
  if [[ $_r144_hist_rc -ne 0 ]]; then
    _r144_hist_err=$(printf '%s' "$_r144_hist_out" | grep -E '^(ERROR|BLOCKING):' | head -1)
    fail_rule "historical_adr_governance" "${_r144_hist_err:-historical-ADR governance helper exited $_r144_hist_rc} -- Rule G-28 / E193"
    _r144_hist_fail=1
  else
    _r144_hist_sum=$(printf '%s' "$_r144_hist_out" | grep -E '^OK:' | tail -1)
    [[ -n "$_r144_hist_sum" ]] && echo "PASS (Rule G-28 / E193): $_r144_hist_sum"
  fi
fi
[[ $_r144_hist_fail -eq 0 ]] && pass_rule "historical_adr_governance"

# ---------------------------------------------------------------------------
