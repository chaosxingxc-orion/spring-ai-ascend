#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 97 — release_note_numeric_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 97 — release_note_numeric_truth (enforcer E135)
#
# Closes rc10 I-α-2: rc9 release note declared "360 nodes / 510 edges" while
# the live architecture-graph.yaml header reported 369 / 520. Rule 91 narrowly
# checks baseline_metrics keys; release-note prose drift went uncaught.
# Rule 97 scans the LATEST release note (lex-sort tail -1) for the canonical
# "<N> nodes / <M> edges" claim and asserts equality with live values from
# `architecture-graph.yaml`. Older release notes are historical snapshots and
# auto-exempt (each captured the count at its wave time). Lines containing
# `rc[N] correction`, `rc[N] first cut`, `rc[N] snapshot`, or `historical`
# within ±3 lines are also exempt.
# ---------------------------------------------------------------------------
_r97_fail=0
_r97_graph="docs/governance/architecture-graph.yaml"
_r97_releases_dir="docs/logs/releases"
if [[ ! -f "$_r97_graph" ]]; then
  fail_rule "release_note_numeric_truth" "$_r97_graph missing — Rule 97 / E135 (cannot establish live node/edge baseline)"
  _r97_fail=1
elif [[ ! -d "$_r97_releases_dir" ]]; then
  fail_rule "release_note_numeric_truth" "$_r97_releases_dir missing — Rule 97 / E135"
  _r97_fail=1
else
  _r97_nodes=$(grep -E '^node_count:' "$_r97_graph" | head -1 | awk '{print $2}')
  _r97_edges=$(grep -E '^edge_count:' "$_r97_graph" | head -1 | awk '{print $2}')
  _r97_latest=$(latest_release_path "$_r97_releases_dir")
  if [[ -z "$_r97_latest" ]]; then
    : # no release notes yet — vacuously pass
  else
    _r97_markers='historical|rc[0-9]+ snapshot|rc[0-9]+ correction|rc[0-9]+ first cut|rc[0-9]+ baseline|superseded|previous|pre-rc[0-9]+'
    _r97_violations=$(awk -v live_n="$_r97_nodes" -v live_e="$_r97_edges" -v markers="$_r97_markers" '
      { lines[NR] = $0 }
      END {
        in_code = 0
        for (i = 1; i <= NR; i++) {
          line = lines[i]
          if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
          if (in_code) continue
          # Compute marker window before deciding
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          # Detect absolute (not delta) "<N> nodes" — i.e., no `+` immediately before the digits.
          if (line ~ /[^+0-9][0-9]+[[:space:]]+nodes/ || line ~ /^[0-9]+[[:space:]]+nodes/) {
            n_str = line
            sub(/^[^0-9]*\+[0-9]+[[:space:]]+nodes/, "", n_str)  # strip a leading delta if present
            if (match(n_str, /[^+0-9]?([0-9]+)[[:space:]]+nodes/)) {
              s = substr(n_str, RSTART, RLENGTH)
              gsub(/[^0-9]/, "", s)
              if (s != "" && s != live_n && window !~ markers) {
                print i ":nodes:claim=" s ":live=" live_n ":" line
              }
            }
          }
          if (line ~ /[^+0-9][0-9]+[[:space:]]+edges/ || line ~ /^[0-9]+[[:space:]]+edges/) {
            e_str = line
            sub(/^[^0-9]*\+[0-9]+[[:space:]]+edges/, "", e_str)
            if (match(e_str, /[^+0-9]?([0-9]+)[[:space:]]+edges/)) {
              s = substr(e_str, RSTART, RLENGTH)
              gsub(/[^0-9]/, "", s)
              if (s != "" && s != live_e && window !~ markers) {
                print i ":edges:claim=" s ":live=" live_e ":" line
              }
            }
          }
        }
      }
    ' "$_r97_latest" 2>/dev/null || true)
    if [[ -n "$_r97_violations" ]]; then
      _r97_first=$(echo "$_r97_violations" | head -5 | tr '\n' '|')
      fail_rule "release_note_numeric_truth" "latest release note $_r97_latest contains absolute node/edge count claim(s) that disagree with live $_r97_graph (nodes=$_r97_nodes, edges=$_r97_edges): ${_r97_first}-- Rule 97 / E135 (rc10 I-α-2 closure; either update the prose to match live counts OR add an 'rc[N] correction'/'rc[N] snapshot' marker within ±3 lines)"
      _r97_fail=1
    fi
    # Extension: scan for `N/M self-tests` and `N/M tests` ratio claims whose
    # DENOMINATOR diverges from baseline_metrics.gate_executable_test_cases.
    # Same marker-window exemption as the node/edge check above.
    _r97_status="docs/governance/architecture-status.yaml"
    if [[ -f "$_r97_status" ]]; then
      _r97_live_tests=$(grep -E '^[[:space:]]*gate_executable_test_cases:[[:space:]]+[0-9]+' "$_r97_status" | head -1 | awk -F'[: ]+' '{print $3}')
      if [[ -n "$_r97_live_tests" ]]; then
        _r97_test_violations=$(awk -v live="$_r97_live_tests" -v markers="$_r97_markers" '
          { lines[NR] = $0 }
          END {
            in_code = 0
            for (i = 1; i <= NR; i++) {
              line = lines[i]
              if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
              if (in_code) continue
              lo = i - 3; if (lo < 1) lo = 1
              hi = i + 3; if (hi > NR) hi = NR
              window = ""
              for (j = lo; j <= hi; j++) window = window " " lines[j]
              # Match `<N>/<M> self-tests` or `<N>/<M> tests passed` or `<N>/<M> gate self-tests`
              if (match(line, /[0-9]+\/[0-9]+[[:space:]]+(self-tests|tests passed|tests pass|gate self-tests)/)) {
                matched_str = substr(line, RSTART, RLENGTH)
                split(matched_str, parts, "/")
                match(parts[2], /^[0-9]+/)
                denom = substr(parts[2], RSTART, RLENGTH)
                if (denom != live && window !~ markers) {
                  print i ":self_tests_denom:claim=" denom ":live=" live ":" line
                }
              }
            }
          }
        ' "$_r97_latest" 2>/dev/null || true)
        if [[ -n "$_r97_test_violations" ]]; then
          _r97_first_t=$(echo "$_r97_test_violations" | head -3 | tr '\n' '|')
          fail_rule "release_note_numeric_truth" "latest release note $_r97_latest claims N/M self-tests whose denominator disagrees with baseline_metrics.gate_executable_test_cases=$_r97_live_tests: ${_r97_first_t}-- Rule 97 / E135 (denominator-drift sub-check; either update the ratio OR add an 'rc[N] correction'/'rc[N] snapshot' marker within ±3 lines)"
          _r97_fail=1
        fi
      fi
    fi
  fi
fi
if [[ $_r97_fail -eq 0 ]]; then pass_rule "release_note_numeric_truth"; fi

# ---------------------------------------------------------------------------
