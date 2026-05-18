#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 82 — baseline_metrics_single_source. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 82 — baseline_metrics_single_source (enforcer E115)
#
# docs/governance/architecture-status.yaml MUST contain a baseline_metrics:
# block under architecture_sync_gate: with at minimum these required keys:
# active_engineering_rules, active_gate_checks, gate_executable_test_cases,
# enforcer_rows, architecture_graph_nodes, architecture_graph_edges.
# README.md and gate/README.md MUST point to the block by substring match
# "architecture_sync_gate.baseline_metrics" (so entrypoint counts have one
# structured source -- rc4 review P1-1 closure).
#
# rc6 (2026-05-18) strengthening per rc5 review P1-1 closure: README.md and
# gate/README.md ALSO MUST NOT carry an active "N <phrase>" count whose
# value disagrees with the parsed baseline_metrics value for that phrase's
# canonical key (e.g. "64 active gate rules" disagrees with
# active_gate_checks: 68 -> FAIL). Historical / rc[N] baseline / pre-rc[N]
# / previous / deprecated / superseded markers on the same line exempt the
# claim (matches the marker convention Rule 80 uses for S2cCallbackSignal
# historical-only paragraphs). Lines inside fenced code blocks (``` ... ```)
# are also exempt so code examples cannot trigger false positives.
# ---------------------------------------------------------------------------
_r82_fail=0
_r82_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r82_yaml" ]]; then
  fail_rule "baseline_metrics_single_source" "$_r82_yaml missing -- Rule 82 / E115"
  _r82_fail=1
else
  for _r82_key in active_engineering_rules active_gate_checks gate_executable_test_cases enforcer_rows architecture_graph_nodes architecture_graph_edges; do
    if ! grep -qE "^[[:space:]]+${_r82_key}:" "$_r82_yaml" 2>/dev/null; then
      fail_rule "baseline_metrics_single_source" "$_r82_yaml missing required key '${_r82_key}:' under architecture_sync_gate.baseline_metrics -- Rule 82 / E115"
      _r82_fail=1
    fi
  done
fi
for _r82_pointer_file in README.md gate/README.md; do
  if [[ -f "$_r82_pointer_file" ]] && ! grep -qF 'architecture_sync_gate.baseline_metrics' "$_r82_pointer_file" 2>/dev/null; then
    fail_rule "baseline_metrics_single_source" "$_r82_pointer_file does not reference architecture_sync_gate.baseline_metrics -- Rule 82 / E115 (entrypoint must point to single source)"
    _r82_fail=1
  fi
done

# rc6 strengthening: numeric-agreement check for entrypoint count phrases.
# Phrase patterns are anchored after their leading number and matched only
# OUTSIDE fenced code blocks AND only on lines NOT carrying a historical
# marker. Each phrase maps to one baseline_metrics key whose parsed value
# defines the expected number.
_r82_phrases=(
  "active gate rules|active_gate_checks"
  "active rules|active_gate_checks"
  "self-tests|gate_executable_test_cases"
  "self-test cases|gate_executable_test_cases"
  "active engineering rules|active_engineering_rules_post_rc6"
  "enforcer rows|enforcer_rows"
  "architecture-graph nodes|architecture_graph_nodes"
  "graph nodes|architecture_graph_nodes"
  "architecture-graph edges|architecture_graph_edges"
  "graph edges|architecture_graph_edges"
  "ADRs|adr_count"
)
_r82_marker_re='historical|rc[0-9]+ baseline|pre-rc[0-9]+|previous|prior|deprecated|superseded|formerly|was [0-9]'
for _r82_pointer_file in README.md gate/README.md; do
  [[ -f "$_r82_pointer_file" ]] || continue
  _r82_in_code=0
  _r82_lineno=0
  while IFS= read -r _r82_line || [[ -n "$_r82_line" ]]; do
    _r82_lineno=$((_r82_lineno + 1))
    if [[ "$_r82_line" =~ ^[[:space:]]*\`\`\` ]]; then
      _r82_in_code=$((1 - _r82_in_code))
      continue
    fi
    [[ $_r82_in_code -eq 1 ]] && continue
    if echo "$_r82_line" | grep -qiE "$_r82_marker_re"; then continue; fi
    for _r82_pair in "${_r82_phrases[@]}"; do
      _r82_phrase="${_r82_pair%%|*}"
      _r82_key="${_r82_pair##*|}"
      _r82_expected=$(awk -v key="$_r82_key" '
        /^architecture_sync_gate:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && $0 ~ "^[[:space:]]+"key":"{
          sub(/^[[:space:]]+[a-zA-Z_]+:[[:space:]]*/, ""); sub(/[^0-9].*$/, ""); print; exit
        }
      ' "$_r82_yaml" 2>/dev/null)
      [[ -z "$_r82_expected" ]] && continue
      # First-occurrence-per-phrase-per-line check (the `if` rather than `while` avoids the
      # replacement-vs-regex infinite-loop risk that would fire if the line uses tabs or
      # multi-space separators between the number and the phrase). A line that carries the
      # same phrase twice with different numbers is rare in practice; the second occurrence
      # is silently accepted -- acceptable miss rate for this rule's scope.
      if [[ "$_r82_line" =~ ([^0-9])([0-9]+)[[:space:]]+${_r82_phrase}([^a-zA-Z-]|$) ]] || [[ "$_r82_line" =~ ^([0-9]+)[[:space:]]+${_r82_phrase}([^a-zA-Z-]|$) ]]; then
        if [[ -n "${BASH_REMATCH[2]:-}" ]]; then
          _r82_actual="${BASH_REMATCH[2]}"
        else
          _r82_actual="${BASH_REMATCH[1]}"
        fi
        if [[ "$_r82_actual" != "$_r82_expected" ]]; then
          fail_rule "baseline_metrics_single_source" "$_r82_pointer_file:$_r82_lineno claims '$_r82_actual $_r82_phrase' but architecture_sync_gate.baseline_metrics.$_r82_key = $_r82_expected -- Rule 82 / E115 (numeric drift)"
          _r82_fail=1
        fi
      fi
    done
    # Tests-passed pattern: "Tests passed: N/N" where both N MUST equal gate_executable_test_cases.
    if [[ "$_r82_line" =~ Tests[[:space:]]passed:[[:space:]]*([0-9]+)/([0-9]+) ]]; then
      _r82_tp_left="${BASH_REMATCH[1]}"
      _r82_tp_right="${BASH_REMATCH[2]}"
      _r82_expected=$(awk '
        /^architecture_sync_gate:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && /^[[:space:]]+gate_executable_test_cases:/{sub(/^[[:space:]]+[a-zA-Z_]+:[[:space:]]*/, ""); sub(/[^0-9].*$/, ""); print; exit}
      ' "$_r82_yaml" 2>/dev/null)
      if [[ -n "$_r82_expected" ]] && { [[ "$_r82_tp_left" != "$_r82_expected" ]] || [[ "$_r82_tp_right" != "$_r82_expected" ]]; }; then
        fail_rule "baseline_metrics_single_source" "$_r82_pointer_file:$_r82_lineno claims 'Tests passed: $_r82_tp_left/$_r82_tp_right' but baseline_metrics.gate_executable_test_cases = $_r82_expected -- Rule 82 / E115 (numeric drift)"
        _r82_fail=1
      fi
    fi
  done < "$_r82_pointer_file"
done

if [[ $_r82_fail -eq 0 ]]; then pass_rule "baseline_metrics_single_source"; fi

