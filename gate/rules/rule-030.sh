#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 30 — telemetry_vertical_constraint_coverage. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 30 — telemetry_vertical_constraint_coverage (enforcer E47)
#
# Telemetry Vertical L1.x (ADR-0061 / §4 #53–#59): every Telemetry-Vertical
# constraint number in ARCHITECTURE.md §4 MUST resolve to at least one
# enforcer row in docs/governance/enforcers.yaml. Stricter than the existing
# meta-rule 28 (presence check only) — Rule 30 validates each §4 #N reference
# individually for N in {53..59}.
# ---------------------------------------------------------------------------
_r30_fail=0
_efile='docs/governance/enforcers.yaml'
_archfile='ARCHITECTURE.md'
if [[ -f "$_archfile" && -f "$_efile" ]]; then
  for _n in 53 54 55 56 57 58 59; do
    # Constraint number must exist in ARCHITECTURE.md §4 as a top-level numbered item.
    if ! grep -qE "^${_n}\. \*\*" "$_archfile"; then
      fail_rule "telemetry_vertical_constraint_coverage" "ARCHITECTURE.md §4 #${_n} (Telemetry Vertical) is missing — expected '${_n}. **' at line start. Per ADR-0061 §8."
      _r30_fail=1
      continue
    fi
    # And the constraint number must be cited in at least one enforcer row.
    if ! grep -qE "§4 #${_n}" "$_efile"; then
      fail_rule "telemetry_vertical_constraint_coverage" "enforcers.yaml has no row citing '§4 #${_n}' (Telemetry Vertical). Add an E-row per ADR-0061 §8 + Rule 28."
      _r30_fail=1
    fi
  done
fi
if [[ $_r30_fail -eq 0 ]]; then pass_rule "telemetry_vertical_constraint_coverage"; fi

# ---------------------------------------------------------------------------
