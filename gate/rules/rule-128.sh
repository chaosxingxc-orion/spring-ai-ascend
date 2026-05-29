#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 128 — model_gateway_authority_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 128 — model_gateway_authority_truth (enforcer E176)
#
# ADR-0121, Java code, and the contract catalog must agree on ModelGateway's
# package and synchronous SPI signature.
#
# scope_surfaces: docs/adr/0121-model-gateway-spi.yaml,
#                 agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java,
#                 docs/contracts/contract-catalog.md
# ---------------------------------------------------------------------------
_r128_out=$(python3 gate/lib/check_model_gateway_authority_truth.py --root . 2>&1)
_r128_rc=$?
if [[ $_r128_rc -ne 0 ]]; then
  fail_rule "model_gateway_authority_truth" "${_r128_out:-ModelGateway authority surfaces disagree} -- Rule G-8 / E176"
else
  pass_rule "model_gateway_authority_truth"
fi

# ---------------------------------------------------------------------------
