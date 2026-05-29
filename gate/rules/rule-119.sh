#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 119 — l1_spi_appendix_4way_parity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 119 — l1_spi_appendix_4way_parity (enforcer E167)
# rc28 fix (NEW-3): fail-closed when helper missing instead of silent pass.
_r119_fail=0
if ! command -v check_l1_spi_appendix >/dev/null 2>&1; then
  fail_rule "l1_spi_appendix_4way_parity" "helper-missing: gate/lib/check_l1_spi_appendix.sh not sourced -- Rule G-1.1.b / E167"
  _r119_fail=1
else
  _r119_out=$(check_l1_spi_appendix 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ "$_s" == "FAIL" ]] || continue
    fail_rule "l1_spi_appendix_4way_parity" "$_f: $_d -- Rule G-1.1.b / E167"
    _r119_fail=1
  done <<< "$_r119_out"
fi
[[ $_r119_fail -eq 0 ]] && pass_rule "l1_spi_appendix_4way_parity"

