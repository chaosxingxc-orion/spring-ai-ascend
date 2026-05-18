#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 78 — dfx_spi_packages_match_module_metadata. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 78 — dfx_spi_packages_match_module_metadata (enforcer E111)
#
# For every module with kind ∈ {platform, domain}, the dfx yaml at
# docs/dfx/<module>.yaml MUST declare a top-level `spi_packages:` block
# whose entries are an order-insensitive set match with the module's
# module-metadata.yaml#spi_packages (placeholder entries excluded — see
# Rule 75). Catches the 2026-05-18 root cause where dfx yamls omitted,
# mis-nested (under observability), or under-declared spi packages
# relative to module-metadata.yaml.
#
# Placeholder filter: lines whose inline comment contains BOTH "placeholder"
# AND "ADR-NNNN" are excluded from both sides of the comparison so deferred
# SPI work declared symmetrically (or asymmetrically) in metadata only does
# not force a noisy dfx declaration before the real SPI lands.
# ---------------------------------------------------------------------------
_r78_fail=0
_r78_dfx_required_kinds_re='^(platform|domain)$'
_r78_extract_real_spi() {
  # Reads a yaml file from stdin/arg, prints non-placeholder spi_packages entries
  # one per line, sorted-unique.
  local _f="$1"
  local _in_block=0
  local _line
  while IFS= read -r _line; do
    if [[ "$_line" =~ ^spi_packages: ]]; then _in_block=1; continue; fi
    if [[ $_in_block -eq 1 ]]; then
      if [[ "$_line" =~ ^[a-zA-Z_] ]]; then _in_block=0; continue; fi
      if [[ "$_line" =~ ^[[:space:]]*-[[:space:]] ]]; then
        if [[ "$_line" == *"#"* ]] && \
           echo "$_line" | grep -qE 'placeholder' && \
           echo "$_line" | grep -qE 'ADR-[0-9]{4}'; then
          continue
        fi
        echo "$_line" | sed -E 's/^[[:space:]]*-[[:space:]]*//' | sed -E 's/[[:space:]#].*$//' | tr -d "\"'"
      fi
    fi
  done < "$_f" | sort -u
}
while IFS= read -r _r78_meta; do
  [[ -z "$_r78_meta" ]] && continue
  _r78_kind="$(grep -E '^[[:space:]]*kind:' "$_r78_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*kind:[[:space:]]*([A-Za-z_]+).*/\1/')"
  [[ ! "$_r78_kind" =~ $_r78_dfx_required_kinds_re ]] && continue
  _r78_mod="$(grep -E '^[[:space:]]*module:' "$_r78_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*module:[[:space:]]*([A-Za-z0-9_-]+).*/\1/')"
  _r78_dfx="docs/dfx/${_r78_mod}.yaml"
  [[ ! -f "$_r78_dfx" ]] && continue   # Rule 35 reports the missing-dfx case
  _r78_meta_spi=$(_r78_extract_real_spi "$_r78_meta")
  _r78_dfx_spi=$(_r78_extract_real_spi "$_r78_dfx")
  # If metadata has zero real (non-placeholder) SPI, dfx not required.
  [[ -z "$_r78_meta_spi" ]] && continue
  if [[ -z "$_r78_dfx_spi" ]]; then
    fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_dfx missing top-level 'spi_packages:' block (must mirror non-placeholder entries of $_r78_meta) — Rule 78 / E111"
    _r78_fail=1
    continue
  fi
  if [[ "$_r78_meta_spi" != "$_r78_dfx_spi" ]]; then
    _r78_meta_one=$(echo "$_r78_meta_spi" | tr '\n' ',' | sed 's/,$//')
    _r78_dfx_one=$(echo "$_r78_dfx_spi" | tr '\n' ',' | sed 's/,$//')
    fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_meta non-placeholder spi_packages={${_r78_meta_one}} but $_r78_dfx declares {${_r78_dfx_one}} — Rule 78 / E111"
    _r78_fail=1
  fi
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r78_fail -eq 0 ]]; then pass_rule "dfx_spi_packages_match_module_metadata"; fi

# ---------------------------------------------------------------------------
