#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 75 — spi_packages_populated. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 75 — spi_packages_populated (enforcer E108)
#
# Every <module>/module-metadata.yaml#spi_packages entry MUST resolve to a
# real directory under <module>/src/main/java/... AND that directory MUST
# contain at least one .java file beyond package-info.java. Catches the
# 2026-05-18 root cause (com.huawei.ascend.engine.spi declared but empty).
#
# Placeholder marker: an spi_packages line that includes BOTH "placeholder"
# AND an "ADR-NNNN" reference in its inline comment is allowed to be empty
# (or absent on disk). This honors deferred SPI work explicitly waived via
# an ADR — e.g. agent-bus / agent-client / agent-evolve W2/W3+ scaffolds.
# ---------------------------------------------------------------------------
_r75_fail=0
while IFS= read -r _r75_meta; do
  [[ -z "$_r75_meta" ]] && continue
  _r75_mod_dir="$(dirname "$_r75_meta")"
  _r75_src="${_r75_mod_dir}/src/main/java"
  _r75_in_block=0
  while IFS= read -r _r75_line; do
    if [[ "$_r75_line" =~ ^spi_packages: ]]; then
      _r75_in_block=1
      continue
    fi
    if [[ $_r75_in_block -eq 1 ]]; then
      if [[ "$_r75_line" =~ ^[a-zA-Z_] ]]; then
        _r75_in_block=0
        continue
      fi
      if [[ "$_r75_line" =~ ^[[:space:]]*-[[:space:]] ]]; then
        # Honor placeholder marker — skip if line comment contains both
        # "placeholder" and an ADR-NNNN reference (deferred SPI work).
        if [[ "$_r75_line" == *"#"* ]] && \
           echo "$_r75_line" | grep -qE 'placeholder' && \
           echo "$_r75_line" | grep -qE 'ADR-[0-9]{4}'; then
          continue
        fi
        _r75_pkg=$(echo "$_r75_line" | sed -E 's/^[[:space:]]*-[[:space:]]*//' | sed -E 's/[[:space:]#].*$//' | tr -d "\"'")
        [[ -z "$_r75_pkg" ]] && continue
        _r75_dir="${_r75_src}/${_r75_pkg//./\/}"
        if [[ ! -d "$_r75_dir" ]]; then
          fail_rule "spi_packages_populated" "$_r75_meta declares spi package '$_r75_pkg' which resolves to '$_r75_dir' — directory does not exist on disk (Rule 75 / E108)"
          _r75_fail=1
          continue
        fi
        _r75_java_count=$(find "$_r75_dir" -maxdepth 1 -name '*.java' -not -name 'package-info.java' 2>/dev/null | wc -l)
        if [[ "${_r75_java_count:-0}" -lt 1 ]]; then
          fail_rule "spi_packages_populated" "$_r75_meta declares spi package '$_r75_pkg' at '$_r75_dir' which contains only package-info.java (no real SPI classes). Mark as deferred with '# placeholder; ADR-NNNN ...' comment to waive, or populate the SPI. Rule 75 / E108"
          _r75_fail=1
        fi
      fi
    fi
  done < "$_r75_meta"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r75_fail -eq 0 ]]; then pass_rule "spi_packages_populated"; fi

# ---------------------------------------------------------------------------
