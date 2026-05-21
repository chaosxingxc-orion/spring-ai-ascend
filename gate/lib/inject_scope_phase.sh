#!/usr/bin/env bash
# Inject `scope_phase:` frontmatter into every governance rule + principle card
# per gate/lib/scope-phase-map.txt. Idempotent — skips cards that already declare
# scope_phase. Used by rc21 Wave 2.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

map_file="gate/lib/scope-phase-map.txt"
[[ -f "$map_file" ]] || { echo "ERROR: $map_file missing" >&2; exit 2; }

declare -A SCOPE
while IFS='=' read -r id phase; do
  [[ -z "$id" || "$id" == \#* ]] && continue
  SCOPE["$id"]="$phase"
done < "$map_file"

inject_one() {
  local card="$1"
  local id="$2"
  local phase="${SCOPE[$id]:-}"
  if [[ -z "$phase" ]]; then
    echo "SKIP $card (no mapping for id=$id)"
    return 0
  fi
  if grep -q '^scope_phase:' "$card"; then
    echo "OK   $card (already has scope_phase)"
    return 0
  fi
  # Insert after the `status:` line. If no status:, insert before the closing ---.
  if grep -q '^status:' "$card"; then
    awk -v phase="$phase" '
      /^status:/ { print; print "scope_phase: " phase; next }
      { print }
    ' "$card" > "${card}.tmp" && mv "${card}.tmp" "$card"
  else
    awk -v phase="$phase" '
      BEGIN { fm=0 }
      /^---$/ { fm++; if (fm==2) { print "scope_phase: " phase } print; next }
      { print }
    ' "$card" > "${card}.tmp" && mv "${card}.tmp" "$card"
  fi
  echo "INJECT $card -> scope_phase: $phase"
}

# Rule cards: extract id from rule-<id>.md filename
for card in docs/governance/rules/rule-*.md; do
  [[ -f "$card" ]] || continue
  base=$(basename "$card" .md)
  id="${base#rule-}"
  inject_one "$card" "$id"
done

# Principle cards: extract id from P-X.md filename (P-A through P-M)
for card in docs/governance/principles/P-*.md; do
  [[ -f "$card" ]] || continue
  base=$(basename "$card" .md)
  inject_one "$card" "$base"
done
