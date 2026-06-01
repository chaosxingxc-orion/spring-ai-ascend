#!/usr/bin/env bash
# Advisory search over the AI knowledge corpus. ripgrep if available, else grep -r.
#
# The knowledge corpus is the knowledge/ tree PLUS the knowledge-in-place surfaces
# that the keystone already excludes from governance enforcement: the ADR decision
# record (docs/adr/), the history/wave logs (docs/logs/), and the architecture
# delivery/design narrative (docs/architecture/). ADRs stay in docs/adr/ because
# they are also the generated fact-layer's decision record — they are knowledge
# in place, not physically relocated.
#
# Usage:
#   search.sh "<query>"            full-text search across the knowledge corpus
#   search.sh --titles "<query>"   match markdown headings / front-matter titles only
#
# This is a convenience tool, not a gate. Load the smallest slice that answers the task.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOTS=()
for d in knowledge docs/adr docs/logs docs/architecture; do
  [[ -d "$REPO/$d" ]] && ROOTS+=("$REPO/$d")
done

mode="full"
if [[ "${1:-}" == "--titles" ]]; then mode="titles"; shift; fi
query="${1:-}"
if [[ -z "$query" ]]; then
  echo "usage: search.sh [--titles] \"<query>\"" >&2
  exit 2
fi

has_rg=0; command -v rg >/dev/null 2>&1 && has_rg=1

if [[ "$mode" == "titles" ]]; then
  # Headings (#..) and front-matter title/id/topic lines.
  if [[ $has_rg -eq 1 ]]; then
    rg -n --no-heading -i -e "^#{1,6}\s.*${query}" -e "^(title|id|topic):.*${query}" "${ROOTS[@]}" || true
  else
    grep -rniE "^#{1,6}[[:space:]].*${query}|^(title|id|topic):.*${query}" "${ROOTS[@]}" || true
  fi
else
  if [[ $has_rg -eq 1 ]]; then
    rg -n -i --max-columns 200 "$query" "${ROOTS[@]}" || true
  else
    grep -rni "$query" "${ROOTS[@]}" || true
  fi
fi
