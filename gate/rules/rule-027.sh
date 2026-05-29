#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 27 — active_entrypoint_baseline_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 27 — active_entrypoint_baseline_truth
# ADR-0047: root README.md MUST contain the four architecture baseline counts
# currently asserted by docs/governance/architecture-status.yaml
# architecture_sync_gate.allowed_claim. Catches CANONICAL-DRIFT.
# ---------------------------------------------------------------------------
_r27_fail=0
if [[ -f docs/governance/architecture-status.yaml && -f README.md ]]; then
  # Extract the architecture_sync_gate.allowed_claim line (it is a single line in YAML).
  _claim27=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' docs/governance/architecture-status.yaml)
  if [[ -z "$_claim27" ]]; then
    fail_rule "active_entrypoint_baseline_truth" "docs/governance/architecture-status.yaml missing architecture_sync_gate.allowed_claim line. Per ADR-0047 Gate Rule 27."
    _r27_fail=1
  else
    _readme27=$(cat README.md)
    _check_baseline27() {
      _label="$1"; _yaml_re="$2"; _readme_re="$3"
      _expected=$(printf '%s' "$_claim27" | grep -oE "$_yaml_re" | head -1 | grep -oE '^[0-9]+' | head -1)
      [[ -z "$_expected" ]] && return 0
      _readme_matches=$(printf '%s' "$_readme27" | grep -oE "$_readme_re")
      if [[ -z "$_readme_matches" ]]; then
        fail_rule "active_entrypoint_baseline_truth" "README.md missing baseline count for '$_label'. Per ADR-0047 Gate Rule 27 the README MUST contain '$_expected $_label' (current canonical baseline)."
        _r27_fail=1
        return 0
      fi
      while IFS= read -r _rm27; do
        _actual=$(printf '%s' "$_rm27" | grep -oE '^[0-9]+' | head -1)
        if [[ "$_actual" != "$_expected" ]]; then
          fail_rule "active_entrypoint_baseline_truth" "README.md asserts '$_actual $_label' but canonical baseline is '$_expected $_label'. Per ADR-0047 Gate Rule 27."
          _r27_fail=1
        fi
      done <<< "$_readme_matches"
    }
    _check_baseline27 '§4 constraints' '[0-9]+[[:space:]]+§4[[:space:]]+constraints' '[0-9]+[[:space:]]+§4[[:space:]]+constraints'
    _check_baseline27 'ADRs' '[0-9]+[[:space:]]+ADRs' '[0-9]+[[:space:]]+ADRs'
    _check_baseline27 'gate rules' '[0-9]+[[:space:]]+active[[:space:]]+gate[[:space:]]+rules' '[0-9]+[[:space:]]+(active[[:space:]]+)?gate[[:space:]]+rules'
    _check_baseline27 'self-tests' '[0-9]+[[:space:]]+gate[[:space:]]+self-tests' '[0-9]+[[:space:]]+(gate[[:space:]]+)?self-tests'
  fi
fi
if [[ $_r27_fail -eq 0 ]]; then pass_rule "active_entrypoint_baseline_truth"; fi

# ---------------------------------------------------------------------------
