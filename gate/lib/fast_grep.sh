#!/usr/bin/env bash
# gate/lib/fast_grep.sh — awk-based regex search helpers (POSIX, single-pass).
#
# Design philosophy (per PR-Opt-rc22 user feedback): prefer awk over external
# tools like ripgrep. awk is POSIX (always available, no install dep), and a
# single awk invocation can match N patterns against a file in ONE pass
# instead of N grep invocations each re-reading the file.
#
# Authority: docs/governance/rules/rule-72.md (gate-machinery integrity) +
#            PR-Opt-rc22 (target: gate < 5min).
#
# Helper functions exposed to gate rules:
#
#   awk_multi_match <file_list_var> <slug1>=<pat1> [<slug2>=<pat2>...]
#       Scan every file in $file_list_var ONCE, match multiple regex patterns
#       in a single awk pass. Emits TSV: slug \t file \t line_num \t content.
#       Caller can group results via `awk -F'\t' -v s=slug1 '$1==s {...}'`.
#
#   awk_count_matches <file_list_var> <pattern>
#       Scan every file ONCE; return aggregate match count. Integer output.
#
#   awk_files_with_match <file_list_var> <pattern>
#       Scan every file ONCE; return sorted unique paths containing >=1 match.
#       Equivalent to `grep -lE pattern` but single-pass over the file list.
#
# All helpers respect the caller's file list (no implicit `find`); pair with
# the scan_cache.sh pre-scanned _SCAN_* file lists for max benefit.
#
# Why awk over grep/ripgrep:
#   - One file open per file, regardless of pattern count (vs N greps).
#   - Pure POSIX — works in Git Bash, WSL, Linux, BSD identically.
#   - Programmable (full language) — caller can extend with awk action blocks.
#   - Deterministic ordering (file list order preserved).
#
# When to NOT use this (still call grep/find):
#   - File scope unknown ahead of time (no pre-built file list).
#   - Single regex query (the awk-loop overhead doesn't pay off).
#   - Need ripgrep's binary-detection / gitignore-skip semantics.

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

# ---------------------------------------------------------------------------
# awk_multi_match — single-pass, multi-pattern match against a file list.
#
# Usage:
#   awk_multi_match _SCAN_AGENT_JAVA_MAIN \
#     'forbidden_import=^import com\.example\.' \
#     'spi_only=class .+Spi'
#
# Output (stdout): one TSV line per match
#   <slug>\t<file>\t<line_num>\t<matched_content>
#
# Filter results in the calling rule:
#   awk_multi_match _SCAN_AGENT_JAVA_MAIN 'a=p1' 'b=p2' \
#     | awk -F'\t' '$1=="a" { ... }'
# ---------------------------------------------------------------------------
awk_multi_match() {
  local _list_var="$1"; shift
  local _list="${!_list_var:-}"
  [[ -z "$_list" ]] && return 0
  [[ $# -eq 0 ]] && return 0

  # Build the awk pattern program: PATTERNS["slug"] = "pattern".
  # Use a temp script to avoid quoting nightmares for complex patterns.
  local _tmp_script
  _tmp_script="$(mktemp)" || return 1
  {
    echo 'BEGIN {'
    local _i=0
    while [[ $# -gt 0 ]]; do
      local _spec="$1"; shift
      local _slug="${_spec%%=*}"
      local _pat="${_spec#*=}"
      # Escape backslashes + double-quotes for awk literal.
      _pat="${_pat//\\/\\\\}"
      _pat="${_pat//\"/\\\"}"
      echo "  slugs[$_i] = \"$_slug\"; pats[$_i] = \"$_pat\";"
      _i=$((_i + 1))
    done
    echo "  n_pats = $_i;"
    echo '}'
    cat <<'AWK'
{
  for (i = 0; i < n_pats; i++) {
    if ($0 ~ pats[i]) {
      printf "%s\t%s\t%d\t%s\n", slugs[i], FILENAME, FNR, $0
    }
  }
}
AWK
  } > "$_tmp_script"

  # Feed file list as positional args. xargs -d '\n' handles spaces in names.
  printf '%s\n' "$_list" | xargs -d '\n' -r awk -f "$_tmp_script" 2>/dev/null
  local _rc=$?
  rm -f "$_tmp_script"
  return $_rc
}

# ---------------------------------------------------------------------------
# awk_count_matches — aggregate match count across a file list.
# ---------------------------------------------------------------------------
awk_count_matches() {
  local _list_var="$1"
  local _pat="$2"
  local _list="${!_list_var:-}"
  [[ -z "$_list" ]] && { echo 0; return 0; }

  printf '%s\n' "$_list" \
    | xargs -d '\n' -r awk -v pat="$_pat" '$0 ~ pat { n++ } END { print n+0 }' 2>/dev/null \
    | awk '{ s += $1 } END { print s+0 }'
  return 0
}

# ---------------------------------------------------------------------------
# awk_files_with_match — sorted unique file paths containing >=1 match.
# Equivalent to `grep -lE pattern file...` but single-pass per file.
# ---------------------------------------------------------------------------
awk_files_with_match() {
  local _list_var="$1"
  local _pat="$2"
  local _list="${!_list_var:-}"
  [[ -z "$_list" ]] && return 0

  printf '%s\n' "$_list" \
    | xargs -d '\n' -r awk -v pat="$_pat" '$0 ~ pat { print FILENAME; nextfile }' 2>/dev/null \
    | sort -u
  return 0
}
