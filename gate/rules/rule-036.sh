#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 36 — domain_module_has_spi_package. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 36 — domain_module_has_spi_package (enforcer E54, ADR-0067)
#
# Every module with kind=domain in its module-metadata.yaml MUST declare at
# least one entry under `spi_packages:` AND each declared package MUST exist
# as a directory under <module>/src/main/java/. Required by CLAUDE.md Rule 32.
# ---------------------------------------------------------------------------
_r36_fail=0
while IFS= read -r _meta; do
  [[ -z "$_meta" ]] && continue
  _kind="$(grep -E '^[[:space:]]*kind:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*kind:[[:space:]]*([A-Za-z_]+).*/\1/')"
  [[ "$_kind" != "domain" ]] && continue
  _mod_dir="$(dirname "$_meta")"
  # Extract spi_packages list entries (lines under spi_packages: that look like "  - <pkg>")
  _has_entry=0
  _pkg_lines="$(awk '/^[[:space:]]*spi_packages:/{flag=1; next} /^[A-Za-z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]*[A-Za-z0-9._-]+/{print}' "$_meta" 2>/dev/null || true)"
  if [[ -z "$_pkg_lines" ]]; then
    fail_rule "domain_module_has_spi_package" "$_meta declares kind=domain but has no spi_packages entries (CLAUDE.md Rule 32 / ADR-0067)"
    _r36_fail=1
    continue
  fi
  while IFS= read -r _ln; do
    _pkg="$(printf '%s\n' "$_ln" | sed -E 's/^[[:space:]]*-[[:space:]]*([A-Za-z0-9._-]+).*/\1/')"
    [[ -z "$_pkg" ]] && continue
    _has_entry=1
    _pkg_path="$(printf '%s\n' "$_pkg" | tr '.' '/')"
    _dir="${_mod_dir}/src/main/java/${_pkg_path}"
    if [[ ! -d "$_dir" ]]; then
      fail_rule "domain_module_has_spi_package" "$_meta declares spi_package '${_pkg}' but directory ${_dir} does not exist"
      _r36_fail=1
    fi
  done <<< "$_pkg_lines"
  if [[ $_has_entry -eq 0 ]]; then
    fail_rule "domain_module_has_spi_package" "$_meta declares kind=domain but spi_packages list is empty"
    _r36_fail=1
  fi
done < <(find . -mindepth 2 -maxdepth 2 -name 'module-metadata.yaml' -type f 2>/dev/null | sort || true)
if [[ $_r36_fail -eq 0 ]]; then pass_rule "domain_module_has_spi_package"; fi

# ===========================================================================
# W1 Layered-4+1 + Architecture-Graph wave (CLAUDE.md Rules 33-34, ADR-0068)
# Gate Rules 37-40 enforce the front-matter discipline and the machine-readable
# graph index. See enforcers.yaml rows E55-E59.
# ===========================================================================

# ---------------------------------------------------------------------------
