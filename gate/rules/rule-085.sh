#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 85 — catalog_spi_row_matches_module_spi_metadata. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 85 — catalog_spi_row_matches_module_spi_metadata (enforcer E118)
#
# Every row in docs/contracts/contract-catalog.md "Active SPI interfaces (N
# total)" table whose Status column does NOT contain "(internal)" MUST have
# its Module column resolve to a module whose
# module-metadata.yaml#spi_packages contains the row's Package column value
# (exact OR as a .spi-prefix sub-package match), AND the same module's
# docs/dfx/<module>.yaml#spi_packages MUST contain the same package.
# Operationalises rc5 review P1-2 closure: catalog SPI commitments must be
# backed by SPI metadata declarations on both sides of the Rule 78 set.
# ---------------------------------------------------------------------------
_r85_fail=0
_r85_catalog="docs/contracts/contract-catalog.md"
if [[ -f "$_r85_catalog" ]]; then
  # Find the SPI section header and total claim. Extract rows between header and the next
  # bold-heading separator. Header pattern: **Active SPI interfaces (N total):**
  _r85_header_lineno=$(grep -nE '^\*\*Active SPI interfaces \([0-9]+ total\):\*\*' "$_r85_catalog" 2>/dev/null | head -1 | cut -d: -f1)
  _r85_header_total=$(grep -oE '^\*\*Active SPI interfaces \([0-9]+ total\):\*\*' "$_r85_catalog" 2>/dev/null | head -1 | grep -oE '[0-9]+')
  if [[ -z "$_r85_header_lineno" ]]; then
    fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog missing header '**Active SPI interfaces (N total):**' -- Rule 85 / E118"
    _r85_fail=1
  else
    # Scan rows starting at header_lineno; stop at first ** heading after a blank line, or at the next ** heading.
    _r85_active_rows=0
    _r85_lineno=0
    _r85_in_table=0
    while IFS= read -r _r85_line || [[ -n "$_r85_line" ]]; do
      _r85_lineno=$((_r85_lineno + 1))
      [[ $_r85_lineno -le $_r85_header_lineno ]] && continue
      # Stop scanning once we hit the next bold section heading.
      if [[ "$_r85_line" =~ ^\*\* ]] && [[ ! "$_r85_line" =~ ^\*\*Active\ SPI ]]; then break; fi
      # Table separator marker: skip rows that look like |---|---|---|---|
      [[ "$_r85_line" =~ ^\|[-:[:space:]\|]+\|$ ]] && continue
      [[ ! "$_r85_line" =~ ^\| ]] && continue
      [[ "$_r85_line" =~ ^\|[[:space:]]*Interface ]] && continue
      # Parse | Interface | Module | Package | Status |
      _r85_iface=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2}' | tr -d '`')
      _r85_mod=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $3); print $3}' | tr -d '`')
      _r85_pkg=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}' | tr -d '`')
      _r85_status=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $5); print $5}')
      [[ -z "$_r85_iface" || -z "$_r85_mod" || -z "$_r85_pkg" ]] && continue
      # Internal-marker exemption: skip the metadata + DFX checks AND exclude from the count.
      if echo "$_r85_status" | grep -qi '(internal)'; then continue; fi
      _r85_active_rows=$((_r85_active_rows + 1))
      _r85_meta="$_r85_mod/module-metadata.yaml"
      _r85_dfx="docs/dfx/$_r85_mod.yaml"
      if [[ ! -f "$_r85_meta" ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface points at module $_r85_mod but $_r85_meta does not exist -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      if [[ ! -f "$_r85_dfx" ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface points at module $_r85_mod but $_r85_dfx does not exist -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      # Extract metadata spi_packages list (only entries under the top-level spi_packages: block;
      # stop at the next non-indented key).
      _r85_meta_pkgs=$(awk '
        /^spi_packages:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print}
      ' "$_r85_meta" 2>/dev/null)
      _r85_dfx_pkgs=$(awk '
        /^spi_packages:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print}
      ' "$_r85_dfx" 2>/dev/null)
      # Match: exact OR catalog-pkg starts with metadata-pkg as a prefix followed by . (sub-package).
      _r85_meta_match=0
      while IFS= read -r _r85_meta_entry; do
        [[ -z "$_r85_meta_entry" ]] && continue
        if [[ "$_r85_pkg" == "$_r85_meta_entry" ]] || [[ "$_r85_pkg" == "$_r85_meta_entry".* ]] || [[ "$_r85_meta_entry" == "$_r85_pkg".* ]]; then
          _r85_meta_match=1; break
        fi
      done <<< "$_r85_meta_pkgs"
      if [[ $_r85_meta_match -eq 0 ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface declares package '$_r85_pkg' not present in $_r85_meta#spi_packages: ($_r85_meta_pkgs) -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      _r85_dfx_match=0
      while IFS= read -r _r85_dfx_entry; do
        [[ -z "$_r85_dfx_entry" ]] && continue
        if [[ "$_r85_pkg" == "$_r85_dfx_entry" ]] || [[ "$_r85_pkg" == "$_r85_dfx_entry".* ]] || [[ "$_r85_dfx_entry" == "$_r85_pkg".* ]]; then
          _r85_dfx_match=1; break
        fi
      done <<< "$_r85_dfx_pkgs"
      if [[ $_r85_dfx_match -eq 0 ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface declares package '$_r85_pkg' not present in $_r85_dfx#spi_packages: ($_r85_dfx_pkgs) -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
    done < "$_r85_catalog"
    # Header count consistency: (N total) MUST equal the number of non-internal rows.
    if [[ -n "$_r85_header_total" ]] && [[ "$_r85_header_total" != "$_r85_active_rows" ]]; then
      fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog header claims '$_r85_header_total total' but counted $_r85_active_rows non-(internal) SPI rows -- Rule 85 / E118"
      _r85_fail=1
    fi
  fi
fi
if [[ $_r85_fail -eq 0 ]]; then pass_rule "catalog_spi_row_matches_module_spi_metadata"; fi

# ---------------------------------------------------------------------------
# Wave history (rc6 -> rc7 -> rc8 prevention waves)
# ===========================================================================
# 2026-05-18 rc6 post-response wave -- Rules 86-87 (E119, E120)
# 2026-05-18 rc8 post-corrective wave -- Rules 88-89 (E121, E122) + Rule 86 fenced-tree-block extension
# Authority cards: docs/governance/rules/rule-86.md, rule-87.md, rule-88.md, rule-89.md
# Reviews:    docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md
#             docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md
# Responses:  docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md
#             docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md
# Closes finding families:
#   rc6 P0-2 root ARCHITECTURE.md 8-module + stale path claims  -> Rule 86 (rc7)
#   rc6 P1-2 status_yaml allowed_claim stale module names        -> Rule 87 (rc7)
#   rc7 P0-1 GraphMemoryRepository ownership corpus drift        -> Rule 86 fenced-tree-block extension (rc8)
#   rc7 P0-2 check_parallel.sh skips Rules 86/87                 -> Rule 88 (rc8)
#   rc7 P1-1 test harness fail-open + hardcoded TOTAL            -> Rule 89 (rc8)
# ===========================================================================

