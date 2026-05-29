#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 17 — contract_catalog_spi_table_matches_source. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 17 — contract_catalog_spi_table_matches_source
# ADR-0041: contract-catalog.md must list the 7 known active SPI interfaces.
# OssApiProbe must NOT appear before the **Probes sub-table heading.
# ---------------------------------------------------------------------------
_r17_fail=0
_catalog17='docs/contracts/contract-catalog.md'
_known_spis=('RunRepository' 'Checkpointer' 'GraphMemoryRepository' 'ResilienceContract' 'Orchestrator' 'GraphExecutor' 'AgentLoopExecutor')
if [[ -f "$_catalog17" ]]; then
  for _spi in "${_known_spis[@]}"; do
    if ! grep -qF "$_spi" "$_catalog17" 2>/dev/null; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 does not list SPI '$_spi'. Per ADR-0041 Gate Rule 17 all 7 active SPI interfaces must appear."
      _r17_fail=1
    fi
  done
  # Perf fix (2026-05-23): combine both per-line passes (probes-sub-table
  # OssApiProbe + data-carriers RunContext interface) into a single mapfile +
  # bash-regex walk. Original ran 2 × `while read` loops each with 2 forks
  # per line × ~600 lines = ~2400 forks. Replace with one mapfile + 4 regex
  # checks per line (no forks).
  if [[ $_r17_fail -eq 0 ]]; then
    mapfile -t _r17_arr < "$_catalog17"
    _past_probes=0
    _in_data_carriers=0
    _run_ctx_has_interface=0
    _run_ctx_found=0
    for _ln17 in "${_r17_arr[@]}"; do
      if [[ "$_ln17" =~ \*\*Probes|^#+[[:space:]]+Probes ]]; then _past_probes=1; fi
      if [[ $_past_probes -eq 0 ]] && [[ "$_ln17" == *"OssApiProbe"* ]]; then
        fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 contains OssApiProbe before the Probes sub-table. OssApiProbe is a probe, not an SPI. Per ADR-0041 Gate Rule 17."
        _r17_fail=1
        break
      fi
      if [[ "$_ln17" =~ \*\*Data\ carriers ]]; then _in_data_carriers=1; fi
      if [[ $_in_data_carriers -eq 1 && $_run_ctx_found -eq 0 ]] && [[ "$_ln17" == *"RunContext"* ]]; then
        _run_ctx_found=1
        [[ "$_ln17" == *"interface"* ]] && _run_ctx_has_interface=1
      fi
    done
    if [[ $_r17_fail -eq 0 && $_run_ctx_found -eq 1 && $_run_ctx_has_interface -eq 0 ]]; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 RunContext row in data-carriers sub-table does not contain 'interface'. Per ADR-0044 Gate Rule 17 extension RunContext must be classified as interface."
      _r17_fail=1
    fi
  fi
else
  fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 not found."
  _r17_fail=1
fi
if [[ $_r17_fail -eq 0 ]]; then pass_rule "contract_catalog_spi_table_matches_source"; fi

# ---------------------------------------------------------------------------
