#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 129 — contract_spi_count_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 129 — contract_spi_count_truth (enforcer E177)
#
# Contract-catalog active SPI totals, module totals, and the latest release
# note's Active SPI total must agree. Promoted SPIs must not remain listed
# as deferred design names.
#
# scope_surfaces: docs/contracts/contract-catalog.md,
#                 docs/logs/releases/*.md
# ---------------------------------------------------------------------------
_r129_out=$(python3 gate/lib/check_contract_spi_count_truth.py --root . 2>&1)
_r129_rc=$?
if [[ $_r129_rc -ne 0 ]]; then
  fail_rule "contract_spi_count_truth" "${_r129_out:-contract SPI count truth check failed} -- Rule G-8 / E177"
else
  pass_rule "contract_spi_count_truth"
fi

