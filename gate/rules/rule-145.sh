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
# Both helpers run CHANGED-FILES-BLOCKING here (`--mode changed-files-blocking`):
# a PR may not ADD or worsen a leak in an L0/L1 document it TOUCHES; pre-existing
# leaks in untouched documents stay advisory (and a still-open dated grandfather
# row tolerates a known, not-yet-migrated leak in either helper). Ratchet:
# advisory -> changed-files-blocking (this rung) -> full-blocking (the terminal
# rung once the whole L0/L1 corpus is clean + every grandfather row retired, per
# ADR-0159 §9). A missing helper fails closed; a missing python interpreter is a
# vacuous pass (Rule G-7 lists WSL as the canonical env).
#
# The changed-file scope is computed once below and shared: check_layer_purity.py
# derives its own changed set from --base; check_l2_detail_sink.py takes the same
# set as explicit --changed args. Base ref = BASE_REF (default origin/main) when
# it resolves, else HEAD (scope collapses to uncommitted + untracked changes —
# the safe minimal scope on a clone without the base ref, never the full corpus).
#
# scope_surfaces: architecture/docs/L0/*.md, architecture/docs/L1/**/*.md, gate/lib/check_layer_purity.py, gate/lib/check_l2_detail_sink.py
# ---------------------------------------------------------------------------
_r145_fail=0

# Resolve the base ref for the changed-files scope (mirrors Rule 44).
_r145_base="${BASE_REF:-origin/main}"
if ! { command -v git >/dev/null 2>&1 && git rev-parse --verify "$_r145_base" >/dev/null 2>&1; }; then
  _r145_base="HEAD"
fi
# Build the changed L0/L1 markdown list: (base..HEAD committed diff) UNION
# (uncommitted tracked changes) UNION (untracked files under the scanned roots).
_r145_changed_files=$(
  {
    git diff --name-only "$_r145_base" HEAD 2>/dev/null || true
    git diff --name-only HEAD 2>/dev/null || true
    git ls-files --others --exclude-standard architecture/docs/L0 architecture/docs/L1 2>/dev/null || true
  } | sort -u | grep -E '^architecture/docs/(L0|L1)/.*\.md$' || true
)
# Assemble the repeated --changed args for the sink helper (one per changed doc).
_r145_changed_args=()
if [[ -n "$_r145_changed_files" ]]; then
  while IFS= read -r _r145_cf; do
    [[ -n "$_r145_cf" ]] && _r145_changed_args+=(--changed "$_r145_cf")
  done <<< "$_r145_changed_files"
fi

_r145_lp_helper="gate/lib/check_layer_purity.py"
if [[ ! -f "$_r145_lp_helper" ]]; then
  fail_rule "layer_purity" "$_r145_lp_helper missing -- Rule G-27 / E194"
  _r145_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r145_lp_out=$("$GATE_PYTHON_BIN" "$_r145_lp_helper" --mode changed-files-blocking --base "$_r145_base" 2>&1)
  _r145_lp_rc=$?
  _r145_lp_sum=$(printf '%s' "$_r145_lp_out" | grep -E 'finding\(s\)' | tail -1)
  [[ -z "$_r145_lp_sum" ]] && _r145_lp_sum=$(printf '%s' "$_r145_lp_out" | tail -1)
  if [[ $_r145_lp_rc -ne 0 ]]; then
    # Surface the offending finding lines (drop GRANDFATHERED/summary noise).
    _r145_lp_hits=$(printf '%s' "$_r145_lp_out" | grep -E '^layer-purity [^ ]+:[0-9]+ ' | grep -v 'GRANDFATHERED' | head -5)
    fail_rule "layer_purity" "changed L0/L1 doc adds a layer-purity leak (Rule G-27 / E194): ${_r145_lp_sum}${_r145_lp_hits:+ || ${_r145_lp_hits}}"
    _r145_fail=1
  else
    [[ -n "$_r145_lp_sum" ]] && echo "OK (Rule G-27 / E194 changed-files-blocking): $_r145_lp_sum"
  fi
fi

_r145_sink_helper="gate/lib/check_l2_detail_sink.py"
if [[ ! -f "$_r145_sink_helper" ]]; then
  fail_rule "layer_purity" "$_r145_sink_helper missing -- Rule G-27 / E195"
  _r145_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r145_sink_out=$("$GATE_PYTHON_BIN" "$_r145_sink_helper" --mode changed-files-blocking "${_r145_changed_args[@]}" 2>&1)
  _r145_sink_rc=$?
  _r145_sink_sum=$(printf '%s' "$_r145_sink_out" | grep -E 'finding\(s\)' | tail -1)
  [[ -z "$_r145_sink_sum" ]] && _r145_sink_sum=$(printf '%s' "$_r145_sink_out" | tail -1)
  if [[ $_r145_sink_rc -ne 0 ]]; then
    _r145_sink_hits=$(printf '%s' "$_r145_sink_out" | grep -E '^L2-DETAIL-SINK [^ ]+:[0-9]+ ' | grep -v 'GRANDFATHERED' | head -5)
    fail_rule "layer_purity" "changed L0/L1 doc adds an L2-detail leak (Rule G-27 / E195): ${_r145_sink_sum}${_r145_sink_hits:+ || ${_r145_sink_hits}}"
    _r145_fail=1
  else
    [[ -n "$_r145_sink_sum" ]] && echo "OK (Rule G-27 / E195 changed-files-blocking): $_r145_sink_sum"
  fi
fi

[[ $_r145_fail -eq 0 ]] && pass_rule "layer_purity"

