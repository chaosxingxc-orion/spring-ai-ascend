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
_r82_vocab="gate/baseline-snapshot-marker-vocabulary.txt"
if [[ ! -f "$_r82_vocab" ]]; then
  fail_rule "baseline_metrics_single_source" "$_r82_vocab missing -- Rule 82 / E115 (Wave 2 vocabulary externalisation)"
  _r82_fail=1
fi
_r82_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r82_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
# Perf fix (2026-05-23): the original inner-loop forked `awk` once per
# (phrase × line) pair (~133 phrases × ~100 lines × 2 files = ~26k forks).
# On WSL/mnt/d that was ~165s per gate run. Pre-parse the baseline_metrics
# block ONCE into a bash associative array, then do O(1) lookups in the
# loop. Same with the per-line `echo | grep` marker check, replaced with a
# bash-native regex.
declare -A _r82_metric=()
while IFS= read -r _r82_kv; do
  [[ -z "$_r82_kv" ]] && continue
  _r82_metric["${_r82_kv%%=*}"]="${_r82_kv#*=}"
done < <(awk '
  /^architecture_sync_gate:/ { f = 1; next }
  f && /^[^[:space:]]/ { exit }
  f && /^[[:space:]]+[a-zA-Z_]+:[[:space:]]*[0-9]+/ {
    key = $0; val = $0
    sub(/^[[:space:]]+/, "", key); sub(/:.*$/, "", key)
    sub(/^[[:space:]]+[a-zA-Z_]+:[[:space:]]*/, "", val); sub(/[^0-9].*$/, "", val)
    if (val != "") print key "=" val
  }
' "$_r82_yaml" 2>/dev/null)
# Cache gate_executable_test_cases for the Tests-passed pattern below.
_r82_tp_expected="${_r82_metric[gate_executable_test_cases]:-}"

# Convert bash-extended-glob phrase list into a single regex group so the
# per-line loop can do ONE bash-regex test instead of N. Marker check stays
# per-line because the vocabulary regex has alternations across many phrases.
for _r82_pointer_file in README.md gate/README.md; do
  [[ -f "$_r82_pointer_file" ]] || continue
  mapfile -t _r82_lines < "$_r82_pointer_file"
  _r82_in_code=0
  for ((_r82_i=0; _r82_i<${#_r82_lines[@]}; _r82_i++)); do
    _r82_line="${_r82_lines[$_r82_i]}"
    _r82_lineno=$((_r82_i + 1))
    if [[ "$_r82_line" =~ ^[[:space:]]*\`\`\` ]]; then
      _r82_in_code=$((1 - _r82_in_code))
      continue
    fi
    [[ $_r82_in_code -eq 1 ]] && continue
    # Marker check via bash regex (case-insensitive). nocasematch shopt is
    # local to this rule body via the save/restore below.
    shopt -q nocasematch
    _r82_nocase_was=$?
    shopt -s nocasematch
    if [[ "$_r82_line" =~ $_r82_marker_re ]]; then
      [[ $_r82_nocase_was -ne 0 ]] && shopt -u nocasematch
      continue
    fi
    [[ $_r82_nocase_was -ne 0 ]] && shopt -u nocasematch

    for _r82_pair in "${_r82_phrases[@]}"; do
      _r82_phrase="${_r82_pair%%|*}"
      _r82_key="${_r82_pair##*|}"
      _r82_expected="${_r82_metric[$_r82_key]:-}"
      [[ -z "$_r82_expected" ]] && continue
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
    # Tests-passed pattern: "Tests passed: N/N" — both N MUST equal gate_executable_test_cases.
    if [[ "$_r82_line" =~ Tests[[:space:]]passed:[[:space:]]*([0-9]+)/([0-9]+) ]] && [[ -n "$_r82_tp_expected" ]]; then
      _r82_tp_left="${BASH_REMATCH[1]}"
      _r82_tp_right="${BASH_REMATCH[2]}"
      if [[ "$_r82_tp_left" != "$_r82_tp_expected" ]] || [[ "$_r82_tp_right" != "$_r82_tp_expected" ]]; then
        fail_rule "baseline_metrics_single_source" "$_r82_pointer_file:$_r82_lineno claims 'Tests passed: $_r82_tp_left/$_r82_tp_right' but baseline_metrics.gate_executable_test_cases = $_r82_tp_expected -- Rule 82 / E115 (numeric drift)"
        _r82_fail=1
      fi
    fi
  done
done

if [[ $_r82_fail -eq 0 ]]; then pass_rule "baseline_metrics_single_source"; fi

