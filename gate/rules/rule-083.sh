#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 83 — design_only_contract_registered_in_catalog. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 83 — design_only_contract_registered_in_catalog (enforcer E116)
#
# Every docs/contracts/*.v1.yaml with status: design_only (or runtime_enforced:
# false) MUST (a) be listed by file basename in docs/contracts/contract-catalog.md,
# AND (b) cite at least one ADR-NNNN reference whose file exists under
# docs/adr/. Operationalises the rc4 review P1-3 prevention: design-only
# contracts cannot drift unregistered, and cited ADRs cannot dangle.
# ---------------------------------------------------------------------------
_r83_fail=0
_r83_catalog="docs/contracts/contract-catalog.md"
for _r83_contract in docs/contracts/*.v1.yaml; do
  [[ -f "$_r83_contract" ]] || continue
  _r83_status=$(grep -E '^status:' "$_r83_contract" 2>/dev/null | head -1 || true)
  _r83_runtime=$(grep -E '^runtime_enforced:' "$_r83_contract" 2>/dev/null | head -1 || true)
  if [[ "$_r83_status" == *design_only* ]] || [[ "$_r83_runtime" == *false* ]]; then
    _r83_name="$(basename "$_r83_contract")"
    if [[ ! -f "$_r83_catalog" ]] || ! grep -qF "$_r83_name" "$_r83_catalog" 2>/dev/null; then
      fail_rule "design_only_contract_registered_in_catalog" "$_r83_contract is design-only/runtime_enforced=false but not listed in $_r83_catalog -- Rule 83 / E116"
      _r83_fail=1
    fi
    _r83_adr_ok=0
    while IFS= read -r _r83_adr; do
      [[ -z "$_r83_adr" ]] && continue
      _r83_num="${_r83_adr#ADR-}"
      if compgen -G "docs/adr/${_r83_num}-*.yaml" > /dev/null || compgen -G "docs/adr/${_r83_num}-*.md" > /dev/null; then
        _r83_adr_ok=1
      fi
    done < <(grep -oE 'ADR-[0-9]{4}' "$_r83_contract" 2>/dev/null | sort -u)
    if [[ $_r83_adr_ok -eq 0 ]]; then
      fail_rule "design_only_contract_registered_in_catalog" "$_r83_contract cites no ADR file that exists under docs/adr/ -- Rule 83 / E116 (authority chain broken)"
      _r83_fail=1
    fi
  fi
done
if [[ $_r83_fail -eq 0 ]]; then pass_rule "design_only_contract_registered_in_catalog"; fi

# ===========================================================================
# 2026-05-18 rc5 post-response review response prevention wave -- Rules 84-85
# Authority: docs/governance/rules/rule-84.md + rule-85.md
#            + docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review-response.en.md
# Closes finding families:
#   P0-1 module-level ARCHITECTURE.md path claim drift after refactor   -> Rule 84
#   P1-2 catalog SPI row not backed by module spi_packages metadata    -> Rule 85
# ===========================================================================

