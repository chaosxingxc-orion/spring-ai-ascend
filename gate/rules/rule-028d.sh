#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28d — out_of_scope_name_guard. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 28d — out_of_scope_name_guard (enforcer E26)
# Names of W2+ deferred concepts (LLMGateway, PostgresCheckpointer,
# HookChain, SpawnEnvelope, LogicalCallHandle, ConnectionLease,
# AdmissionDecision, BackpressureSignal, ChronosHydration, SandboxExecutor)
# MUST NOT appear in agent-*/src/main/java. Test sources, ADRs, plans,
# release notes, and architecture-status.yaml are intentionally exempt.
# rc43 (ADR-0127): SkillRegistry removed from the blacklist; it is
# promoted from W2 deferred to L0 contract (see ADR-0127 Skill SPI).
# ---------------------------------------------------------------------------
_r28d_fail=0
_oos_names='LLMGateway|PostgresCheckpointer|HookChain|SpawnEnvelope|LogicalCallHandle|ConnectionLease|AdmissionDecision|BackpressureSignal|ChronosHydration|SandboxExecutor'
_28d_hits=$(grep -rnE "\\b($_oos_names)\\b" \
  agent-*/src/main/java 2>/dev/null || true)
if [[ -n "$_28d_hits" ]]; then
  fail_rule "out_of_scope_name_guard" "W2+ out-of-scope name detected in main sources:\n$_28d_hits\nPer Rule 28d / enforcer E26 / plan §13."
  _r28d_fail=1
fi
if [[ $_r28d_fail -eq 0 ]]; then pass_rule "out_of_scope_name_guard"; fi

# ---------------------------------------------------------------------------
