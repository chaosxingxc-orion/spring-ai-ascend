#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 56 — engine_registry_covers_all_known_engines. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 56 — engine_registry_covers_all_known_engines (enforcer E77, Rule 44 / P-M, ADR-0072)
#
# Bidirectional consistency: every known_engines[].id in
# docs/contracts/engine-envelope.v1.yaml MUST appear as a
# String ENGINE_TYPE = "<id>" constant in agent-service/src/main, and every
# such constant MUST appear in known_engines. This guarantees the Phase 5
# EngineRegistry.validateAgainstSchema() boot check has matching inputs at
# compile time -- Rule 44 strict matching cannot be silently broken by a
# missing yaml row or a stale ENGINE_TYPE constant.
# ---------------------------------------------------------------------------
_r56_fail=0
_r56_yaml="docs/contracts/engine-envelope.v1.yaml"
# Post-T2.B2 (ADR-0079): EngineRegistry + ENGINE_TYPE constants moved to
# agent-execution-engine. Reference adapters (SequentialGraphExecutor +
# IterativeAgentLoopExecutor) stay in agent-service/.../inmemory and also
# declare ENGINE_TYPE. Scan BOTH source roots.
_r56_main="agent-execution-engine/src/main/java agent-service/src/main/java"
if [[ ! -f "$_r56_yaml" ]]; then
  fail_rule "engine_registry_covers_all_known_engines" "$_r56_yaml missing -- cannot cross-check"
  _r56_fail=1
else
  _r56_yaml_ids=$(grep -E '^[[:space:]]+- id:[[:space:]]+' "$_r56_yaml" | sed -E 's/^[[:space:]]+- id:[[:space:]]+([A-Za-z0-9_.-]+).*/\1/' | sort -u)
  _r56_src_ids=$(grep -rhE 'String[[:space:]]+ENGINE_TYPE[[:space:]]*=[[:space:]]*"[A-Za-z0-9_.-]+"' $_r56_main 2>/dev/null | sed -E 's/.*ENGINE_TYPE[[:space:]]*=[[:space:]]*"([A-Za-z0-9_.-]+)".*/\1/' | sort -u)
  for _id in $_r56_yaml_ids; do
    if ! echo "$_r56_src_ids" | grep -qxE "${_id}"; then
      fail_rule "engine_registry_covers_all_known_engines" "yaml declares known_engines.id=$_id but no ENGINE_TYPE=\"$_id\" found in $_r56_main"
      _r56_fail=1
    fi
  done
  for _id in $_r56_src_ids; do
    if ! echo "$_r56_yaml_ids" | grep -qxE "${_id}"; then
      fail_rule "engine_registry_covers_all_known_engines" "ENGINE_TYPE=\"$_id\" in source has no matching - id: $_id in $_r56_yaml"
      _r56_fail=1
    fi
  done
fi
if [[ $_r56_fail -eq 0 ]]; then pass_rule "engine_registry_covers_all_known_engines"; fi

# ---------------------------------------------------------------------------
