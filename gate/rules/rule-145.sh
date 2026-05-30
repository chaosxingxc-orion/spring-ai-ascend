#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 145 — layer_purity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 145 — layer_purity (enforcers E194 + E195, kernel Rule G-27)
#
# Authority: docs/governance/rules/rule-G-27.md. One rule, two ADVISORY helpers
# that together encode the adjudicated layer-purity VERDICT: an L0/L1 authority
# surface is a STRUCTURAL boundary document and MUST NOT carry runtime L2
# implementation detail (method call chains, runtime sequences, SQL/RLS/GUC +
# persistence, HTTP status / route-verb / header behaviour, filter ordering,
# wire formats, method signatures, test-class inventories). The detail belongs
# at architecture/docs/L2/ + the contract surfaces (docs/contracts/) + the
# generated facts (architecture/facts/generated/). Naming a public SPI as a
# boundary identity, development-view package decomposition, and ArchUnit /
# enforcer citations are DEFENSIBLE and are never reported.
#   * gate/lib/check_layer_purity.py (E194, slug component layer_purity) —
#     reports the self-contradiction: an authority surface that DECLARES it
#     carries no runtime contract / wire / SPI signature yet does (the L0 §0.6
#     vs §0.5.3 / §4 leak the VERDICT names).
#   * gate/lib/check_l2_detail_sink.py (E195, slug component l2_detail_sink) —
#     reports L2 implementation detail left in L0/L1 prose by signal family
#     (sql_persistence / http_runtime / wire_format / method_signature /
#     filter_ordering / test_inventory). A finding can be suppressed in place
#     with an HTML comment `<!-- l2-detail-sink-allow: <reason> -->`.
# Both helpers run ADVISORY here (`--mode advisory`): they surface a finding
# summary to the gate log and NEVER block while the L0/L1 corpus is swept clean.
# Ratchet: advisory -> changed-files-blocking -> blocking (the helper --mode
# flags implement the rungs). A missing helper fails closed; a missing python
# interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).
#
# scope_surfaces: architecture/docs/L0/*.md, architecture/docs/L1/**/*.md, gate/lib/check_layer_purity.py, gate/lib/check_l2_detail_sink.py
# ---------------------------------------------------------------------------
_r145_fail=0

_r145_lp_helper="gate/lib/check_layer_purity.py"
if [[ ! -f "$_r145_lp_helper" ]]; then
  fail_rule "layer_purity" "$_r145_lp_helper missing -- Rule G-27 / E194"
  _r145_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r145_lp_out=$("$GATE_PYTHON_BIN" "$_r145_lp_helper" --mode advisory 2>&1)
  # Advisory: report the finding summary to the gate log, never block.
  _r145_lp_sum=$(printf '%s' "$_r145_lp_out" | grep -E 'finding\(s\)' | tail -1)
  [[ -z "$_r145_lp_sum" ]] && _r145_lp_sum=$(printf '%s' "$_r145_lp_out" | tail -1)
  [[ -n "$_r145_lp_sum" ]] && echo "ADVISORY (Rule G-27 / E194): $_r145_lp_sum"
fi

_r145_sink_helper="gate/lib/check_l2_detail_sink.py"
if [[ ! -f "$_r145_sink_helper" ]]; then
  fail_rule "layer_purity" "$_r145_sink_helper missing -- Rule G-27 / E195"
  _r145_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r145_sink_out=$("$GATE_PYTHON_BIN" "$_r145_sink_helper" --mode advisory 2>&1)
  # Advisory: report the finding summary to the gate log, never block.
  _r145_sink_sum=$(printf '%s' "$_r145_sink_out" | grep -E 'finding\(s\)' | tail -1)
  [[ -z "$_r145_sink_sum" ]] && _r145_sink_sum=$(printf '%s' "$_r145_sink_out" | tail -1)
  [[ -n "$_r145_sink_sum" ]] && echo "ADVISORY (Rule G-27 / E195): $_r145_sink_sum"
fi

[[ $_r145_fail -eq 0 ]] && pass_rule "layer_purity"

