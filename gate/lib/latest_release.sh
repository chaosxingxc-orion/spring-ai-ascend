#!/usr/bin/env bash
# gate/lib/latest_release.sh
#
# Resolves the "latest" release note under docs/logs/releases/*.md by parsed
# rc-number ordering, NOT lexicographic filename ordering.
#
# Authority: rc11 review P1-2 (closed by rc12 K-β) — lex sort placed
# 2026-05-19-l0-rc9-corrective.en.md AFTER 2026-05-19-l0-rc11-corrective.en.md
# (character "9" > character "1"), so Rule 33, Rule 97, and Rule G-2 evaluated
# stale rc9 prose as canonical. Rule 102 (release_recency_resolver_correctness)
# enforces the helper is the only resolver path.
#
# Sort key: extract `rc(\d+)` from the basename and numeric-sort by it. Files
# without `rcN` in the filename fall back to lexicographic order (used by
# pre-rc release notes like `2026-05-13-L0-architecture-release-v2.en.md`).
#
# Usage:
#   _latest=$(latest_release_path docs/logs/releases)
#   _latest=$(latest_release_path docs/logs/releases en.md)   # filter suffix
#
# Returns the absolute or relative path of the latest release note, OR empty
# string if the directory is missing / empty. Caller must handle empty.

latest_release_path() {
  local _dir="${1:-docs/logs/releases}"
  local _suffix="${2:-.md}"
  if [[ ! -d "$_dir" ]]; then
    return 0
  fi
  # Two-key sort:
  #   key 1: rcN extracted from basename, numeric (ascending; missing → 0)
  #   key 2: full filename, lexicographic (stable tie-break)
  # `tail -1` picks the maximum.
  find "$_dir" -maxdepth 1 -type f -name "*${_suffix}" 2>/dev/null \
    | awk '{
        bn = $0
        sub(/.*\//, "", bn)
        rc = 0
        if (match(bn, /rc[0-9]+/)) {
          rc = substr(bn, RSTART+2, RLENGTH-2) + 0
        }
        printf "%010d\t%s\t%s\n", rc, bn, $0
      }' \
    | sort -k1,1n -k2,2 \
    | tail -1 \
    | awk -F'\t' '{print $3}'
}

# When sourced from a gate-rule context the function is callable as
# `latest_release_path`. When invoked directly as a CLI (e.g. for smoke
# testing) the first arg is the directory.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  latest_release_path "$@"
fi
