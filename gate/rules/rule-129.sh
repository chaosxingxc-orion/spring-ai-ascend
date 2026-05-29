#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 129 — contract_spi_count_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 129 — contract_spi_count_truth (enforcer E177)
#
# Contract-catalog active SPI totals, module totals, and the latest release
# note's Active SPI total must agree. Promoted SPIs must not remain listed
# as deferred design names. Agent/advisor composition claims must also be
# backed by AgentDefinition fields, typed advisor carriers, and the shared
# advisor/model hook sequence.
#
# scope_surfaces: docs/contracts/contract-catalog.md,
#                 docs/logs/releases/*.md,
#                 docs/contracts/chat-advisor.v1.yaml,
#                 docs/contracts/agent-definition.v1.yaml,
#                 docs/contracts/model-streaming.v1.yaml,
#                 agent-service/src/main/java/.../AgentDefinition.java
# ---------------------------------------------------------------------------
_r129_out=$(python3 gate/lib/check_contract_spi_count_truth.py --root . 2>&1)
_r129_rc=$?
if [[ $_r129_rc -ne 0 ]]; then
  fail_rule "contract_spi_count_truth" "${_r129_out:-contract SPI count truth check failed} -- Rule G-8 / E177"
else
  pass_rule "contract_spi_count_truth"
fi

