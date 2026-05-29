#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 92 — gate_rules_corpus_freshness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 92 — gate_rules_corpus_freshness (enforcer E125)
#
# Closes rc8 post-corrective review P2-1: `gate/rules/` is a shadow corpus
# that drifts stale relative to the canonical monolith. Rule 92 asserts that
# every `# Rule N — slug` header in canonical has a matching
# `gate/rules/rule-NNN.sh` file (zero-padded to 3 digits, supports letter
# suffix like `028a`). Production parallel gate operates from the canonical
# monolith; gate/rules/ is the IDE-only inspection artifact (ADR-0083).
# ---------------------------------------------------------------------------
_r92_fail=0
_r92_canonical="gate/check_architecture_sync.sh"
_r92_dir="gate/rules"
if [[ ! -f "$_r92_canonical" ]] || [[ ! -d "$_r92_dir" ]]; then
  fail_rule "gate_rules_corpus_freshness" "$_r92_canonical or $_r92_dir missing — Rule 92 / E125"
  _r92_fail=1
else
  # Perf fix (2026-05-23): replaced per-rule-id `echo | grep -oE` + per-id
  # printf + per-id [[ -f ]] check (~130 ids × 3 forks = ~400 forks, ~13s) with
  # a single bash-native loop that uses bash regex to split id parts.
  _r92_missing=""
  _r92_id_re='^([0-9]+)([a-z]?)$'
  while IFS= read -r _r92_rid; do
    [[ -z "$_r92_rid" ]] && continue
    [[ "$_r92_rid" =~ $_r92_id_re ]] || continue
    _r92_num_part="${BASH_REMATCH[1]}"
    _r92_letter="${BASH_REMATCH[2]}"
    printf -v _r92_padded "%03d" "$_r92_num_part"
    _r92_expected="${_r92_dir}/rule-${_r92_padded}${_r92_letter}.sh"
    if [[ ! -f "$_r92_expected" ]]; then
      _r92_missing="${_r92_missing}${_r92_rid} "
    fi
  done < <(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+.?[a-z]? — /{match($0, /^# Rule ([0-9]+.?[a-z]?) — /, a); print a[1]}' "$_r92_canonical")
  if [[ -n "$_r92_missing" ]]; then
    fail_rule "gate_rules_corpus_freshness" "$_r92_dir lacks rule file(s) for canonical header(s): ${_r92_missing}-- Rule 92 / E125 (run bash gate/lib/extract_rules.sh to refresh)"
    _r92_fail=1
  fi
fi
if [[ $_r92_fail -eq 0 ]]; then pass_rule "gate_rules_corpus_freshness"; fi

# ---------------------------------------------------------------------------
