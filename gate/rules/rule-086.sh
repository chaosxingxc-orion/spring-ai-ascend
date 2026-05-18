#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 86 — root_architecture_count_and_path_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 86 — root_architecture_count_and_path_truth (enforcer E119)
#
# Every "N-module" / "N modules" / "N reactor modules" claim in root
# ARCHITECTURE.md (outside fenced code blocks and frontmatter) MUST equal the
# pom.xml <module> count AND architecture-status.yaml#repository_counts.reactor_modules.
# Every "agent-*/src/main/java/..." path claim MUST resolve OR have a historical
# marker within +/-3 lines. Operationalises rc6 post-response review P0-2 closure.
# ---------------------------------------------------------------------------
_r86_fail=0
_r86_arch="ARCHITECTURE.md"
_r86_pom="pom.xml"
_r86_status_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r86_arch" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_arch missing -- Rule 86 / E119"
  _r86_fail=1
elif [[ ! -f "$_r86_pom" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_pom missing -- Rule 86 / E119"
  _r86_fail=1
elif [[ ! -f "$_r86_status_yaml" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_status_yaml missing -- Rule 86 / E119"
  _r86_fail=1
else
  _r86_pom_count=$(awk '/<modules>/,/<\/modules>/' "$_r86_pom" | grep -cE '^[[:space:]]*<module>')
  _r86_status_count=$(awk '/^repository_counts:/{flag=1; next} flag && /^[a-z]/{flag=0} flag' "$_r86_status_yaml" | grep -oE 'reactor_modules:[[:space:]]+[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [[ "$_r86_pom_count" != "$_r86_status_count" ]]; then
    fail_rule "root_architecture_count_and_path_truth" "pom.xml declares $_r86_pom_count modules but architecture-status.yaml reactor_modules: $_r86_status_count -- Rule 86 / E119 (canonical sources disagree)"
    _r86_fail=1
  fi
  _r86_canonical=$_r86_pom_count
  _r86_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|consolidated|merged into|merged in|was rooted|formerly|superseded|deferred|moved|extracted per ADR-[0-9]{4}|post-ADR-[0-9]{4}|archived'
  _r86_in_code=0
  _r86_lineno=0
  _r86_in_frontmatter=0
  _r86_frontmatter_seen_open=0
  while IFS= read -r _r86_line || [[ -n "$_r86_line" ]]; do
    _r86_lineno=$((_r86_lineno + 1))
    if [[ "$_r86_line" =~ ^---[[:space:]]*$ ]]; then
      if [[ $_r86_frontmatter_seen_open -eq 0 ]]; then
        _r86_in_frontmatter=1; _r86_frontmatter_seen_open=1
      else
        _r86_in_frontmatter=0
      fi
      continue
    fi
    [[ $_r86_in_frontmatter -eq 1 ]] && continue
    if [[ "$_r86_line" =~ ^\`\`\` ]]; then
      _r86_in_code=$((1 - _r86_in_code))
      continue
    fi
    [[ "$_r86_in_code" -eq 1 ]] && continue
    _r86_count_claim=$(echo "$_r86_line" | grep -oE '\*\*[0-9]+ modules\*\*|\b[0-9]+-module\b|\b[0-9]+ reactor modules\b|\b[0-9]+ modules\b' | head -1)
    if [[ -n "$_r86_count_claim" ]]; then
      _r86_claim_num=$(echo "$_r86_count_claim" | grep -oE '[0-9]+' | head -1)
      _r86_lo=$((_r86_lineno > 3 ? _r86_lineno - 3 : 1))
      _r86_hi=$((_r86_lineno + 3))
      _r86_marker_present=0
      if sed -n "${_r86_lo},${_r86_hi}p" "$_r86_arch" 2>/dev/null | grep -qiE "$_r86_marker_re"; then
        _r86_marker_present=1
      fi
      if [[ $_r86_marker_present -eq 0 ]] && [[ "$_r86_claim_num" != "$_r86_canonical" ]]; then
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_lineno active count claim '$_r86_count_claim' (N=$_r86_claim_num) disagrees with canonical $_r86_canonical from pom.xml + architecture-status.yaml -- Rule 86 / E119 (root architecture count drift)"
        _r86_fail=1
      fi
    fi
    _r86_paths=$(echo "$_r86_line" | grep -oE 'agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+' | sort -u)
    if [[ -n "$_r86_paths" ]]; then
      while IFS= read -r _r86_path; do
        [[ -z "$_r86_path" ]] && continue
        _r86_path_clean="${_r86_path%.}"
        if [[ -e "$_r86_path_clean" ]] || [[ -e "${_r86_path_clean}.java" ]]; then continue; fi
        _r86_lo=$((_r86_lineno > 3 ? _r86_lineno - 3 : 1))
        _r86_hi=$((_r86_lineno + 3))
        if sed -n "${_r86_lo},${_r86_hi}p" "$_r86_arch" 2>/dev/null | grep -qiE "$_r86_marker_re"; then continue; fi
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_lineno claims path '$_r86_path_clean' that does not exist on disk and the surrounding +/-3 lines carry no historical/moved/extracted-per-ADR/consolidated/pre-Phase-C marker -- Rule 86 / E119"
        _r86_fail=1
      done <<< "$_r86_paths"
    fi
  done < "$_r86_arch"

  # rc8 extension: 2nd pass — validate SPI-ownership claims inside fenced
  # tree-diagram code blocks. The 1st pass above intentionally skips fenced
  # blocks (to avoid false positives on prose examples), but the rc7
  # GraphMemoryRepository drift hid inside the root tree block precisely
  # because of that exclusion. This pass scans only fenced blocks, identifies
  # module-header lines (`  agent-foo/    #...`) plus their indent level, and
  # for each indented `<pkg>/spi/` leaf checks that the module's
  # module-metadata.yaml#spi_packages declares an entry containing
  # `.<pkg>.spi`. Historical markers within +/-3 lines still exempt.
  _r86_tb_in=0
  _r86_tb_mod=""
  _r86_tb_mod_indent=0
  _r86_tb_lineno=0
  while IFS= read -r _r86_tbline || [[ -n "$_r86_tbline" ]]; do
    _r86_tb_lineno=$((_r86_tb_lineno + 1))
    if [[ "$_r86_tbline" =~ ^\`\`\` ]]; then
      _r86_tb_in=$((1 - _r86_tb_in))
      _r86_tb_mod=""
      continue
    fi
    [[ "$_r86_tb_in" -eq 0 ]] && continue
    # Module-header line: indented `<modulename>/    #...` or `<modulename>/`
    if echo "$_r86_tbline" | grep -qE '^[[:space:]]+(agent-[a-z-]+|spring-ai-ascend-[a-z-]+)/[[:space:]]*(#.*)?$'; then
      _r86_tb_mod=$(echo "$_r86_tbline" | grep -oE '(agent-[a-z-]+|spring-ai-ascend-[a-z-]+)/' | head -1 | tr -d '/')
      _r86_tb_mod_indent=$(echo "$_r86_tbline" | awk '{ match($0, /[^ ]/); print RSTART - 1 }')
      continue
    fi
    # SPI leaf line: indented `<pkg>/spi/  # ...` -- look up parent module's metadata
    if [[ -n "$_r86_tb_mod" ]] && echo "$_r86_tbline" | grep -qE '^[[:space:]]+[a-z][a-z_]*/spi/[[:space:]]*(#.*)?$'; then
      _r86_tb_leaf_indent=$(echo "$_r86_tbline" | awk '{ match($0, /[^ ]/); print RSTART - 1 }')
      if [[ $_r86_tb_leaf_indent -le $_r86_tb_mod_indent ]]; then
        _r86_tb_mod=""
        continue
      fi
      _r86_tb_pkg=$(echo "$_r86_tbline" | grep -oE '[a-z_]+/spi/' | head -1 | sed 's|/spi/||')
      _r86_tb_meta="${_r86_tb_mod}/module-metadata.yaml"
      _r86_tb_lo=$((_r86_tb_lineno > 3 ? _r86_tb_lineno - 3 : 1))
      _r86_tb_hi=$((_r86_tb_lineno + 3))
      if sed -n "${_r86_tb_lo},${_r86_tb_hi}p" "$_r86_arch" 2>/dev/null | grep -qiE "$_r86_marker_re"; then continue; fi
      if [[ ! -f "$_r86_tb_meta" ]]; then
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_tb_lineno tree-block leaf '${_r86_tb_pkg}/spi/' under module '${_r86_tb_mod}' but ${_r86_tb_meta} does not exist -- Rule 86 / E119 (tree-block ownership drift, fenced-block extension)"
        _r86_fail=1
        continue
      fi
      if ! grep -E '^[[:space:]]*-[[:space:]]+' "$_r86_tb_meta" 2>/dev/null | grep -qE "\.${_r86_tb_pkg}\.spi([^a-zA-Z0-9]|$)"; then
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_tb_lineno tree-block claims '${_r86_tb_pkg}/spi/' under module '${_r86_tb_mod}' but ${_r86_tb_meta}#spi_packages declares no entry containing '.${_r86_tb_pkg}.spi' -- Rule 86 / E119 (tree-block ownership drift, fenced-block extension)"
        _r86_fail=1
      fi
    fi
  done < "$_r86_arch"
fi
if [[ $_r86_fail -eq 0 ]]; then pass_rule "root_architecture_count_and_path_truth"; fi

