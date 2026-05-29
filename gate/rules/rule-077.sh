#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 77 — spi_packages_dot_spi_convention. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 77 — spi_packages_dot_spi_convention (enforcer E110)
#
# Every <module>/module-metadata.yaml#spi_packages entry MUST end in `.spi`
# OR contain `.spi.` (sub-packages). Operationalises Rule 32's `*.spi.*`
# wording — a package called e.g. `service.runtime.runs` (no `.spi`) is a
# domain package, not an SPI package, and must not be declared as one.
# ---------------------------------------------------------------------------
_r77_fail=0
while IFS= read -r _r77_meta; do
  [[ -z "$_r77_meta" ]] && continue
  _r77_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/["\047]/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r77_meta")
  while IFS= read -r _r77_pkg; do
    [[ -z "$_r77_pkg" ]] && continue
    if [[ ! "$_r77_pkg" =~ \.spi$ ]] && [[ ! "$_r77_pkg" =~ \.spi\. ]]; then
      fail_rule "spi_packages_dot_spi_convention" "$_r77_meta declares spi package '$_r77_pkg' which does not end in '.spi' or contain '.spi.' (Rule 77 / E110 — Rule 32 *.spi.* convention)"
      _r77_fail=1
    fi
  done <<< "$_r77_declared"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r77_fail -eq 0 ]]; then pass_rule "spi_packages_dot_spi_convention"; fi

# ---------------------------------------------------------------------------
