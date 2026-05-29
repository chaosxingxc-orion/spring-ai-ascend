#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 10 — module_dep_direction. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 10 — module_dep_direction (amended at L1 by ADR-0055; further by ADR-0078)
# Phase C consolidation (ADR-0078) merged agent-platform + agent-runtime into a
# single agent-service Maven module. The cross-module pom direction is no longer
# meaningful: the new invariant is INTRA-MODULE sub-package layering —
#   com.huawei.ascend.service.runtime.* MUST NOT depend on com.huawei.ascend.service.platform.*
# enforced at source level by ArchUnit RuntimeMustNotDependOnPlatformTest (E2).
# At the pom level, this rule asserts agent-service does not regress by adding
# a dependency on a deleted artifact (agent-platform, agent-runtime).
# Enforcer row: docs/governance/enforcers.yaml#E1
# ---------------------------------------------------------------------------
_r10_fail=0
if [[ -f 'agent-service/pom.xml' ]]; then
  for _r10_dead in 'agent-platform' 'agent-runtime'; do
    if grep -q "<artifactId>${_r10_dead}</artifactId>" 'agent-service/pom.xml' 2>/dev/null; then
      fail_rule "module_dep_direction" "agent-service/pom.xml declares dependency on ${_r10_dead}. Per ADR-0078 this artifact was deleted in Phase C consolidation."
      _r10_fail=1
    fi
  done
fi
if [[ $_r10_fail -eq 0 ]]; then pass_rule "module_dep_direction"; fi

# ---------------------------------------------------------------------------
