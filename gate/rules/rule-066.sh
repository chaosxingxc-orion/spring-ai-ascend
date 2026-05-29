#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 66 — spi_package_exhaustiveness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 66 — spi_package_exhaustiveness (enforcer E96, G3 prevention)
#
# For each <module>/module-metadata.yaml, every src/main/java/.../spi
# directory MUST appear in spi_packages. Catches drift where a developer
# adds a new SPI package (e.g. runtime.s2c.spi) but forgets to declare it
# in the metadata.
# ---------------------------------------------------------------------------
_r66_fail=0
while IFS= read -r _r66_meta; do
  [[ -z "$_r66_meta" ]] && continue
  _r66_mod_dir="$(dirname "$_r66_meta")"
  _r66_src="${_r66_mod_dir}/src/main/java"
  [[ -d "$_r66_src" ]] || continue
  _r66_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r66_meta" | sort -u)
  while IFS= read -r _r66_dir; do
    [[ -z "$_r66_dir" ]] && continue
    _r66_pkg="${_r66_dir#${_r66_src}/}"
    _r66_pkg="${_r66_pkg//\//.}"
    if ! echo "$_r66_declared" | grep -qxF "$_r66_pkg"; then
      fail_rule "spi_package_exhaustiveness" "$_r66_dir exists on disk but package '$_r66_pkg' is not declared in $_r66_meta spi_packages (G3 prevention)"
      _r66_fail=1
    fi
  done <<< "$(find "$_r66_src" -type d -name spi 2>/dev/null)"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r66_fail -eq 0 ]]; then pass_rule "spi_package_exhaustiveness"; fi

# ===========================================================================
# CLAUDE.md token-optimization wave -- PR1 (2026-05-17)
# Authority: docs/governance/rules/rule-{67..71}.md
# Goal: shrink always-loaded governance set from ~99K -> ~10.6K tokens.
# Rules 67-71 with enforcer rows E97-E101 and 10 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
