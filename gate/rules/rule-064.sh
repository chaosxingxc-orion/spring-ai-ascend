#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 64 — module_count_data_driven. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 64 — module_count_data_driven (enforcer E94, G1 prevention)
#
# The canonical module count lives in
# docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules.
# Rule 28e (module_count_invariant) checks against a hard-coded constant; this
# rule cross-checks the canonical value vs the actual count of <module> entries
# in root pom.xml. Adding a new reactor module thus updates ONE file
# (architecture-status.yaml), not four (gate + ADR-0055 + ADR-0059 + ADR-0067).
# ---------------------------------------------------------------------------
_r64_fail=0
_r64_status='docs/governance/architecture-status.yaml'
_r64_pom='pom.xml'
if [[ ! -f "$_r64_status" ]]; then
  fail_rule "module_count_data_driven" "$_r64_status missing -- cannot cross-check canonical module count (G1 prevention)"
  _r64_fail=1
elif [[ ! -f "$_r64_pom" ]]; then
  fail_rule "module_count_data_driven" "$_r64_pom missing -- cannot count <module> entries"
  _r64_fail=1
else
  _r64_canonical=$(grep -E '^[[:space:]]*total_reactor_modules:[[:space:]]*[0-9]+' "$_r64_status" | head -1 | sed -E 's/^[[:space:]]*total_reactor_modules:[[:space:]]*([0-9]+).*/\1/')
  _r64_pom_count=$(grep -c '<module>' "$_r64_pom" 2>/dev/null || echo 0)
  if [[ -z "$_r64_canonical" ]]; then
    fail_rule "module_count_data_driven" "$_r64_status missing repository_counts.total_reactor_modules field (G1 prevention)"
    _r64_fail=1
  elif [[ "$_r64_canonical" != "$_r64_pom_count" ]]; then
    fail_rule "module_count_data_driven" "$_r64_pom declares $_r64_pom_count <module> entries; canonical total_reactor_modules in $_r64_status is $_r64_canonical (G1 prevention -- update one file, not many)"
    _r64_fail=1
  fi
fi
if [[ $_r64_fail -eq 0 ]]; then pass_rule "module_count_data_driven"; fi

# ---------------------------------------------------------------------------
