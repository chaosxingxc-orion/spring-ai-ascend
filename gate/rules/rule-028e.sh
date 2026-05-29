#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28e — module_count_invariant. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28e — module_count_invariant (enforcer E27)
# Root pom.xml MUST declare exactly 8 <module> entries after the 2026-05-20
# rc13 wave (ADR-0088 dissolved agent-runtime-core): BoM + 6 substantive
# modules (agent-client, agent-bus, agent-middleware, agent-execution-engine,
# agent-evolve, agent-service) + graphmemory starter. Any other count is
# rejected; L1 plan decision D3 amended per ADR-0078 + ADR-0088.
# ---------------------------------------------------------------------------
_r28e_fail=0
_root_pom='pom.xml'
_r28e_expected=8
if [[ -f "$_root_pom" ]]; then
  _module_count=$(grep -c '<module>' "$_root_pom" 2>/dev/null || echo 0)
  if [[ "$_module_count" -ne "$_r28e_expected" ]]; then
    fail_rule "module_count_invariant" "$_root_pom declares $_module_count <module> entries; L1 (six-module materialization) requires exactly $_r28e_expected. Per Rule 28e / enforcer E27 / plan decision D3."
    _r28e_fail=1
  fi
fi
if [[ $_r28e_fail -eq 0 ]]; then pass_rule "module_count_invariant"; fi

# ---------------------------------------------------------------------------
