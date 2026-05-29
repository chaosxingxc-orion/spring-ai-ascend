#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 95 — spi_catalog_exhaustiveness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 95 — spi_catalog_exhaustiveness (enforcer E131)
#
# Closes rc8 post-corrective review P1-2: SkillCapacityRegistry was a public
# interface under a declared *.spi.* package but absent from
# contract-catalog.md §2 "Active SPI interfaces" table. Rule 95 asserts that
# every public `interface Foo` declared in a Java file under any `*.spi.*`
# package path appears in `docs/contracts/contract-catalog.md` as either an
# active SPI row OR is explicitly marked `(internal)`.
# ---------------------------------------------------------------------------
_r95_fail=0
_r95_catalog="docs/contracts/contract-catalog.md"
if [[ ! -f "$_r95_catalog" ]]; then
  fail_rule "spi_catalog_exhaustiveness" "$_r95_catalog missing — Rule 95 / E131"
  _r95_fail=1
else
  _r95_missing=""
  while IFS= read -r _r95_spi_file; do
    [[ -z "$_r95_spi_file" ]] && continue
    # Extract `public interface XXX` declarations — EXCLUDING sealed and non-sealed
    # interfaces (the contract-catalog convention classifies sealed types as
    # "Structural carriers" rather than SPI; matches `public interface` only).
    _r95_iface=$(grep -E '^public[[:space:]]+interface[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$_r95_spi_file" 2>/dev/null | head -1 | sed -E 's/^public[[:space:]]+interface[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/')
    [[ -z "$_r95_iface" ]] && continue
    # Check catalog for the interface name (either as ` `Iface` ` cell or `(internal)` mark)
    if ! grep -qE "\`${_r95_iface}\`" "$_r95_catalog"; then
      _r95_missing="${_r95_missing}${_r95_iface}(${_r95_spi_file}) "
    fi
  done < <(find . -type f -name '*.java' -path '*/spi/*' -not -path './target/*' -not -path './*/target/*' -not -path './.git/*')
  if [[ -n "$_r95_missing" ]]; then
    fail_rule "spi_catalog_exhaustiveness" "public SPI interface(s) missing from $_r95_catalog: ${_r95_missing}-- Rule 95 / E131 (add as active SPI row OR mark '(internal)'; rc8 post-corrective P1-2 closure)"
    _r95_fail=1
  fi
fi
if [[ $_r95_fail -eq 0 ]]; then pass_rule "spi_catalog_exhaustiveness"; fi

# ---------------------------------------------------------------------------
