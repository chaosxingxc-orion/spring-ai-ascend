#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28 — constraint_enforcer_coverage. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28 — constraint_enforcer_coverage (meta-rule, enforcer E28)
#
# **L1 scope (Phase L truthful naming, per reviewer P2-1):** baseline presence
# check only. Verifies that `docs/governance/enforcers.yaml` references
# `CLAUDE.md` AND `ARCHITECTURE.md`. This is the smallest viable bootstrap
# meta-check — it does NOT parse every "must"/"forbidden"/"required" sentence
# in the corpus and cross-reference each one. Full natural-language parsing is
# deferred (no executable enforcer is feasible without committing to a brittle
# regex over evolving prose).
#
# Anchor-level truth is enforced by Rule 28j (`enforcer_artifact_paths_exist`,
# Phase L hardening), which validates that every `artifact: path#anchor`
# resolves to a real method (.java/.sh) or heading (.md) — closing reviewer
# finding P0-2.
# ---------------------------------------------------------------------------
_r28_fail=0
if [[ -f "$_efile" ]] && [[ -f 'CLAUDE.md' ]]; then
  if ! grep -q 'CLAUDE.md' "$_efile" 2>/dev/null; then
    fail_rule "constraint_enforcer_coverage" "enforcers.yaml does not reference CLAUDE.md at all; the meta-rule requires every active CLAUDE rule to map to an enforcer. Per Rule 28 / enforcer E28."
    _r28_fail=1
  fi
  if ! grep -q 'ARCHITECTURE.md' "$_efile" 2>/dev/null; then
    fail_rule "constraint_enforcer_coverage" "enforcers.yaml does not reference ARCHITECTURE.md; §4 constraints must map to enforcers. Per Rule 28 / enforcer E28."
    _r28_fail=1
  fi
fi
if [[ $_r28_fail -eq 0 ]]; then pass_rule "constraint_enforcer_coverage"; fi

# ---------------------------------------------------------------------------
