#!/usr/bin/env bash
# gate/lib/fast_grep.sh — performance-optimized regex search helpers.
#
# Wraps `ripgrep` (rg) when available; falls back to `grep -r` / `git grep`.
# Auto-selects the fastest backend based on PATH availability:
#
#   1. ripgrep (rg)     — multi-threaded, mmap, gitignore-aware, 3-10x faster.
#   2. git grep         — packfile-aware, internally parallelized, 2-5x faster.
#   3. grep -r / find   — POSIX baseline (single-threaded).
#
# Authority: docs/governance/rules/rule-72.md (gate-machinery integrity) +
#            PR-Opt-rc22 user-driven gate optimization (target: gate < 5min).
#
# Exports backend-detection results:
#   _FAST_GREP_BACKEND       one of: rg | git-grep | grep
#   _FAST_GREP_JOBS          number of worker threads (defaults to nproc or 4)
#
# Helper functions exposed to gate rules:
#
#   fast_grep_files <pattern> [<path>...]
#       Returns matching file paths (one per line). Backend-agnostic.
#       Equivalent to `grep -rln <pattern> <path>...`.
#
#   fast_grep_content <pattern> [<path>...]
#       Returns matching lines with file:line:content. Backend-agnostic.
#       Equivalent to `grep -rEn <pattern> <path>...`.
#
#   fast_grep_count <pattern> [<path>...]
#       Returns match count (integer). Backend-agnostic.
#
#   fast_grep_parallel_files <pattern> <file_list>
#       Given a newline-separated file list (e.g., $_SCAN_AGENT_JAVA_MAIN),
#       parallelize the grep across $_FAST_GREP_JOBS workers via xargs -P.
#       Returns matching file paths.
#
# Caller MUST set GATE_REPO_ROOT before sourcing.

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

# ---------------------------------------------------------------------------
# Backend detection (once per gate run).
# ---------------------------------------------------------------------------
_fast_grep_detect_backend() {
  if [[ -n "${_FAST_GREP_BACKEND:-}" ]]; then
    return 0  # already detected
  fi

  # Honor explicit override (for testing / disabling).
  if [[ -n "${GATE_GREP_BACKEND:-}" ]]; then
    _FAST_GREP_BACKEND="$GATE_GREP_BACKEND"
  elif command -v rg >/dev/null 2>&1; then
    _FAST_GREP_BACKEND="rg"
  elif command -v git >/dev/null 2>&1 && git -C "$GATE_REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    _FAST_GREP_BACKEND="git-grep"
  else
    _FAST_GREP_BACKEND="grep"
  fi

  # Worker count (used by parallel helpers).
  if [[ -z "${_FAST_GREP_JOBS:-}" ]]; then
    if [[ -n "${GATE_GREP_JOBS:-}" ]]; then
      _FAST_GREP_JOBS="$GATE_GREP_JOBS"
    elif command -v nproc >/dev/null 2>&1; then
      _FAST_GREP_JOBS="$(nproc 2>/dev/null || echo 4)"
    else
      _FAST_GREP_JOBS=4
    fi
  fi

  export _FAST_GREP_BACKEND _FAST_GREP_JOBS
}

# ---------------------------------------------------------------------------
# Public API.
# ---------------------------------------------------------------------------

# fast_grep_files <pattern> [<path>...]
#   Returns file paths containing matches (one per line, sorted, unique).
#   Empty stdout on no matches; non-zero exit code from underlying tool is
#   suppressed (gate rules typically want to enumerate, not fail).
fast_grep_files() {
  _fast_grep_detect_backend
  local _pat="$1"; shift
  local _paths=("$@")
  [[ ${#_paths[@]} -eq 0 ]] && _paths=(".")

  case "$_FAST_GREP_BACKEND" in
    rg)
      rg -l --no-messages -e "$_pat" "${_paths[@]}" 2>/dev/null | sort -u
      ;;
    git-grep)
      ( cd "$GATE_REPO_ROOT" && git grep -lE "$_pat" -- "${_paths[@]}" 2>/dev/null | sort -u )
      ;;
    *)
      grep -rlE "$_pat" "${_paths[@]}" 2>/dev/null | sort -u
      ;;
  esac
  return 0
}

# fast_grep_content <pattern> [<path>...]
#   Returns matching lines as file:line:content.
fast_grep_content() {
  _fast_grep_detect_backend
  local _pat="$1"; shift
  local _paths=("$@")
  [[ ${#_paths[@]} -eq 0 ]] && _paths=(".")

  case "$_FAST_GREP_BACKEND" in
    rg)
      rg -nH --no-messages -e "$_pat" "${_paths[@]}" 2>/dev/null
      ;;
    git-grep)
      ( cd "$GATE_REPO_ROOT" && git grep -nE "$_pat" -- "${_paths[@]}" 2>/dev/null )
      ;;
    *)
      grep -rnE "$_pat" "${_paths[@]}" 2>/dev/null
      ;;
  esac
  return 0
}

# fast_grep_count <pattern> [<path>...]
#   Returns total match count across all files. Always echoes a non-negative
#   integer (zero on no matches).
fast_grep_count() {
  _fast_grep_detect_backend
  local _pat="$1"; shift
  local _paths=("$@")
  [[ ${#_paths[@]} -eq 0 ]] && _paths=(".")

  case "$_FAST_GREP_BACKEND" in
    rg)
      rg -c --no-messages -e "$_pat" "${_paths[@]}" 2>/dev/null \
        | awk -F: '{ s += $NF } END { print (s ? s : 0) }'
      ;;
    git-grep)
      ( cd "$GATE_REPO_ROOT" && git grep -cE "$_pat" -- "${_paths[@]}" 2>/dev/null \
          | awk -F: '{ s += $NF } END { print (s ? s : 0) }' )
      ;;
    *)
      grep -rcE "$_pat" "${_paths[@]}" 2>/dev/null \
        | awk -F: '{ s += $NF } END { print (s ? s : 0) }'
      ;;
  esac
  return 0
}

# fast_grep_parallel_files <pattern> <file_list_var>
#   Parallelize grep across $_FAST_GREP_JOBS workers via xargs -P.
#   Caller passes the NAME of a variable containing newline-separated paths
#   (e.g., '_SCAN_AGENT_JAVA_MAIN'); we look it up by indirection.
#   Returns matching file paths (one per line, sorted).
#   For very large file lists this beats `grep -r` because grep is internally
#   serial; xargs -P spreads N grep processes across cores.
fast_grep_parallel_files() {
  _fast_grep_detect_backend
  local _pat="$1"
  local _var_name="$2"
  local _file_list="${!_var_name:-}"
  [[ -z "$_file_list" ]] && return 0

  case "$_FAST_GREP_BACKEND" in
    rg)
      # rg is already multi-threaded internally; just feed the file list.
      echo "$_file_list" \
        | rg -l --no-messages -e "$_pat" -F "-" 2>/dev/null | sort -u || true
      ;;
    *)
      # xargs -P fans grep across workers; each grep handles a batch of files.
      printf '%s\n' "$_file_list" \
        | xargs -P "$_FAST_GREP_JOBS" -n 50 grep -lE "$_pat" 2>/dev/null \
        | sort -u || true
      ;;
  esac
  return 0
}

# Auto-detect on source.
_fast_grep_detect_backend
