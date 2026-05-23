#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 91 — baseline_metric_matches_executable_manifest. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 91 — baseline_metric_matches_executable_manifest (enforcer E123)
#
# Closes rc8 post-corrective review P0-1: the parallel summary trailer reported
# 102 executable rule sections while `architecture-status.yaml` declared 74.
# Rule 91 asserts that `baseline_metrics.active_gate_checks` equals the literal
# count of `# Rule N — slug` headers in this script (canonical manifest).
# ---------------------------------------------------------------------------
_r91_fail=0
_r91_status_file="docs/governance/architecture-status.yaml"
_r91_canonical="gate/check_architecture_sync.sh"
_r91_enforcers="docs/governance/enforcers.yaml"
if [[ ! -f "$_r91_status_file" ]] || [[ ! -f "$_r91_canonical" ]]; then
  fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file or $_r91_canonical missing — Rule 91 / E123"
  _r91_fail=1
else
  _r91_manifest_count=$(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+.?[a-z]? — /{c++} END{print c+0}' "$_r91_canonical")
  _r91_declared=$(grep -E '^[[:space:]]*active_gate_checks:[[:space:]]*[0-9]+' "$_r91_status_file" | head -1 | sed -E 's/.*active_gate_checks:[[:space:]]*([0-9]+).*/\1/')
  # rc10 widening per ADR-0084 / I-α-1 closure: extend Rule 91 to cover baseline_metrics.enforcer_rows.
  # Closes rc10 hidden defect: rc9 declared enforcer_rows: 116 (104 baseline + 12 wave) but live count was 134.
  _r91_enforcer_actual=$(grep -cE '^- id: E[0-9]+' "$_r91_enforcers" 2>/dev/null || echo 0)
  _r91_enforcer_declared=$(grep -E '^[[:space:]]*enforcer_rows:[[:space:]]*[0-9]+' "$_r91_status_file" | head -1 | sed -E 's/.*enforcer_rows:[[:space:]]*([0-9]+).*/\1/')
  if [[ -z "$_r91_declared" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file missing baseline_metrics.active_gate_checks key — Rule 91 / E123"
    _r91_fail=1
  elif [[ "$_r91_declared" != "$_r91_manifest_count" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "baseline_metrics.active_gate_checks=$_r91_declared != canonical manifest count $_r91_manifest_count (count of '# Rule N — slug' headers in $_r91_canonical before END marker) — Rule 91 / E123 (rc8 post-corrective P0-1 closure)"
    _r91_fail=1
  elif [[ -z "$_r91_enforcer_declared" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file missing baseline_metrics.enforcer_rows key — Rule 91 / E123 (rc10 widening per ADR-0084)"
    _r91_fail=1
  elif [[ "$_r91_enforcer_declared" != "$_r91_enforcer_actual" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "baseline_metrics.enforcer_rows=$_r91_enforcer_declared != live enforcer count $_r91_enforcer_actual ('^- id: E[0-9]+' in $_r91_enforcers) — Rule 91 / E123 (rc10 widening per ADR-0084 / I-α-1 closure)"
    _r91_fail=1
  fi
fi
if [[ $_r91_fail -eq 0 ]]; then pass_rule "baseline_metric_matches_executable_manifest"; fi

# ---------------------------------------------------------------------------
